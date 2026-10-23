"""Backend for the mk2ai desktop app: song library + amp control (exposed to the UI via pywebview)."""
import asyncio, glob, json, os, re, subprocess, sys, threading, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import katana as k      # noqa: E402
import params as P      # noqa: E402
import patch as PT      # noqa: E402

SONGS = os.path.join(ROOT, "songs")


def _split_title(memo, fallback):
    head = (memo or "").split(":")[0]
    if " - " in head:
        artist, title = head.split(" - ", 1)
        return artist.strip(), title.strip()
    return "", fallback


class Service:
    def __init__(self):
        self._lock = threading.Lock()
        self._inp = self._out = None
        self._job = {"state": "idle"}

    # -------------------------------------------------------------- library
    def songs(self):
        res = []
        for path in sorted(glob.glob(os.path.join(SONGS, "*.json"))):
            sid = os.path.basename(path)[:-5]
            try:
                specs = json.load(open(path))
            except ValueError:
                continue
            specs = specs if isinstance(specs, list) else [specs]
            artist, title = _split_title(specs[0].get("memo"), sid)
            res.append({
                "id": sid, "artist": artist, "title": title,
                "patches": [{"index": i, "name": s["name"], "amp": s.get("amp", {}).get("type", ""),
                             "note": (s.get("memo", "").split(":", 1) + [""])[1].strip()}
                            for i, s in enumerate(specs)],
            })
        return res

    # -------------------------------------------------------------- amp
    def _connect(self):
        if self._out is not None:
            return
        self._inp, self._out = k.open_ports()

    def _drop(self):
        for p in (self._inp, self._out):
            try:
                p and p.close()
            except Exception:
                pass
        self._inp = self._out = None

    def status(self):
        with self._lock:
            try:
                self._connect()
                k.dt1(self._out, 0x7F000001, [1])
                time.sleep(0.1)
                name = k.rq1(self._inp, self._out, P.TEMP, 16, tries=2)
                if len(name) != 16:
                    raise IOError("no reply")
                return {"connected": True, "patch": bytes(name).decode("ascii", "replace").strip()}
            except Exception as e:  # amp off / unplugged / port busy
                self._drop()
                return {"connected": False, "error": str(e)}

    def play(self, song_id, index):
        path = os.path.join(SONGS, song_id + ".json")
        specs = json.load(open(path))
        specs = specs if isinstance(specs, list) else [specs]
        spec = specs[int(index)]
        with self._lock:
            try:
                self._connect()
                PT.apply_live(PT.build(spec), self._inp, self._out)
            except Exception as e:
                self._drop()
                return {"ok": False, "error": str(e)}
        return {"ok": True, "patch": spec["name"]}

    # -------------------------------------------------------------- reveal .tsl
    def reveal(self, song_id):
        tsl = os.path.join(SONGS, song_id + ".tsl")
        subprocess.run(["open", "-R", tsl if os.path.exists(tsl) else SONGS])
        return {"ok": True}

    # -------------------------------------------------------------- create with Claude
    def create_song(self, text):
        """Ask the Claude agent to research a song and write songs/<id>.json (+ .tsl). Poll job_status()."""
        if self._job.get("state") == "running":
            return {"ok": False, "error": "Already creating a song"}
        self._job = {"state": "running", "note": "Starting Claude…", "query": text}
        threading.Thread(target=self._run_job, args=(text,), daemon=True).start()
        return {"ok": True}

    def job_status(self):
        return dict(self._job)

    def _run_job(self, text):
        before = set(glob.glob(os.path.join(SONGS, "*.json")))
        try:
            asyncio.run(self._agent(text))
            new = sorted(set(glob.glob(os.path.join(SONGS, "*.json"))) - before)
            if not new:
                raise RuntimeError("Claude finished but did not create a song file")
            sid = os.path.basename(new[0])[:-5]
            self._validate(sid)
            self._job = {"state": "done", "id": sid}
        except Exception as e:
            msg = str(e)
            if "Not logged in" in msg or "authentication" in msg.lower():
                msg = "Claude is not signed in. See the README: run the one-time login."
            self._job = {"state": "error", "error": msg}

    def _validate(self, sid):
        specs = json.load(open(os.path.join(SONGS, sid + ".json")))
        for spec in (specs if isinstance(specs, list) else [specs]):
            PT.build(spec)
        if not os.path.exists(os.path.join(SONGS, sid + ".tsl")):
            PT.json.dump(PT.to_tsl(specs if isinstance(specs, list) else [specs], sid),
                         open(os.path.join(SONGS, sid + ".tsl"), "w"), indent=1)

    async def _agent(self, text):
        from claude_agent_sdk import query, ClaudeAgentOptions, AssistantMessage, ToolUseBlock
        skill = open(os.path.join(ROOT, ".claude", "skills", "katana-patch", "SKILL.md")).read()
        skill = re.sub(r"^---.*?---", "", skill, flags=re.S)
        prompt = (
            f"The user searched their song library for: {text!r} and nothing matched. "
            "Figure out the song (artist and title) and research the guitar tone (amp, pedals, effects, "
            "rhythm vs lead parts) with a web search. Then create ONE new file songs/<artist>-<title>.json "
            "(lowercase slug) holding a JSON list of 1-3 patch specs, following the instructions. "
            "The first patch memo must start with 'Artist - Title: ' . Validate and export by running "
            "`.venv/bin/python tools/patch.py export songs/<slug>.json -o songs/<slug>.tsl`; fix any error it "
            "reports. Do NOT apply to the amp and do not touch other files. Reply with one short line when done."
        )
        opts = ClaudeAgentOptions(
            cwd=ROOT, system_prompt={"type": "preset", "preset": "claude_code", "append": skill},
            allowed_tools=["Read", "Write", "WebSearch", "WebFetch",
                           "Bash(.venv/bin/python tools/patch.py export:*)"],
            permission_mode="acceptEdits", max_turns=25)
        labels = {"WebSearch": "Researching the tone…", "WebFetch": "Reading about the gear…",
                  "Write": "Writing the patch…", "Bash": "Building the .tsl…", "Read": "Reading notes…"}
        async for msg in query(prompt=prompt, options=opts):
            if isinstance(msg, AssistantMessage):
                for b in msg.content:
                    if isinstance(b, ToolUseBlock):
                        self._job = {**self._job, "note": labels.get(b.name, "Working…")}
