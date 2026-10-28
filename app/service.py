"""Backend for the TonenAI desktop app: song library + amp control (exposed to the UI via pywebview)."""
import asyncio, urllib.parse, urllib.request, glob, json, os, re, subprocess, sys, threading, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import katana as k      # noqa: E402
import params as P      # noqa: E402
import osutil            # noqa: E402

SID = re.compile(r"[a-z0-9][a-z0-9\-]*")


def _ok_sid(s):
    """A song id is a lowercase slug; anything else (../, slashes, drive letters) is refused before it touches a path."""
    return isinstance(s, str) and bool(SID.fullmatch(s))


VPY = ".venv/Scripts/python" if sys.platform == "win32" else ".venv/bin/python"   # the venv interpreter, as the AI agent must call it
import patch as PT      # noqa: E402
import local_llm        # noqa: E402

SONGS = os.path.join(ROOT, "songs")
COVERS = os.path.join(SONGS, "covers")


def _split_title(memo, fallback):
    head = (memo or "").split(":")[0]
    if " - " in head:
        artist, title = head.split(" - ", 1)
        return artist.strip(), title.strip()
    return "", fallback


def _added(path):
    """When a song was added to the library (file creation time; falls back to the modification time)."""
    st = os.stat(path)
    return getattr(st, "st_birthtime", st.st_mtime)


def _cover_url(sid):
    """Cached cover as a data URL (the window's web root can't reach songs/covers)."""
    path = os.path.join(COVERS, sid + ".jpg")
    if not os.path.exists(path):
        return ""
    import base64
    return "data:image/jpeg;base64," + base64.b64encode(open(path, "rb").read()).decode()


DATA = os.path.join(ROOT, "data")
GUITARS = os.path.join(DATA, "guitars.json")
LIVESETS = os.path.join(DATA, "livesets.json")
TABS_CFG = os.path.join(DATA, "tabs.json")
FAVS = os.path.join(DATA, "favorites.json")
RECENT = os.path.join(DATA, "recent.json")
SETTINGS = os.path.join(DATA, "settings.json")
SONG_META = os.path.join(DATA, "song_meta.json")   # your practice status + notes per song
LANG_NAMES = {"en": "English", "pt-BR": "Brazilian Portuguese (pt-BR)", "es": "Spanish (es)"}   # UI languages; keep in sync with LANGS in i18n.js
RIG = os.path.join(DATA, "rig.json")                # which amp and pedals you own (see gear/catalog.json)
CATALOG = os.path.join(ROOT, "gear", "catalog.json")
SECTION_OF_KIND = {"trio": "band", "whammy": "whammy"}   # built-in pedals keep their own song sections
BACKUPS = os.path.join(DATA, "backups")
EXPORTS = os.path.join(DATA, "exports")
BANK_KEYS = P.SLOT_KEYS[1:]            # A1..A4, B1..B4: the 8 channels that can be written (the Panel cannot)


def _read_json(path, default):
    try:
        return json.load(open(path, encoding="utf8"))
    except (OSError, ValueError):
        return default


def _short(name):
    """Tile label: the name in capitals, cut at a word boundary to fit."""
    n = str(name).upper().strip()
    if len(n) <= 14:
        return n
    cut = n[:15].rsplit(" ", 1)[0]
    return cut if len(cut) >= 4 else n[:14]


def _slug(text):
    return re.sub(r"[^a-z0-9]+", "-", str(text).lower()).strip("-")[:30]




def _agent_guard_check(tool, inp, root=None):
    """Return None if the AI agent may use this tool call, else the reason it may not.
    The agent reads web pages, so it must not be able to change program files or read your settings:
    it may only write songs/*.json and songs/*.tsl, and read inside the project except data/ and key files."""
    root = os.path.realpath(root or ROOT)
    songs = os.path.join(root, "songs")
    paths = [os.path.expanduser(str(inp.get(k))) for k in ("file_path", "path", "notebook_path") if inp.get(k)]
    if tool in ("Write", "Edit", "MultiEdit", "NotebookEdit"):
        for p in paths or [""]:
            full = os.path.realpath(p if os.path.isabs(p) else os.path.join(root, p))
            if os.path.dirname(full) != songs or not full.endswith((".json", ".tsl")):
                return "The agent may only write song files in songs/."
    elif tool in ("Read", "Grep", "Glob", "NotebookRead"):
        for p in paths:
            full = os.path.realpath(p if os.path.isabs(p) else os.path.join(root, p))
            rel = os.path.relpath(full, root)
            parts = rel.split(os.sep)
            if rel.startswith("..") or parts[0] == "data" or parts[-1].startswith(".env") or parts[-1].endswith((".pem", ".key")):
                return "The agent may only read project files (not data/ or key files)."
    elif tool == "Bash":
        import shlex
        try:
            words = shlex.split(inp.get("command", ""))
        except ValueError:
            return "Could not read that command."
        if "-o" in words and words.index("-o") + 1 < len(words):      # patch.py export ... -o <file>
            out = words[words.index("-o") + 1]
            full = os.path.realpath(out if os.path.isabs(out) else os.path.join(root, out))
            if os.path.dirname(full) != songs or not full.endswith(".tsl"):
                return "The agent may only export .tsl files into songs/."
    return None


async def _agent_guard(input_data, tool_use_id, context):
    reason = _agent_guard_check(input_data.get("tool_name", ""), input_data.get("tool_input") or {})
    if reason:
        return {"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny",
                                       "permissionDecisionReason": reason}}
    return {}


class Service:
    def __init__(self):
        self._lock = threading.Lock()
        self._inp = self._out = None
        self._job = {"state": "idle"}
        self._cancel = threading.Event()
        self._last_text = ""

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
                "id": sid, "artist": artist, "title": title, "cover": "", "added": _added(path), "band": specs[0].get("band"), "whammy": specs[0].get("whammy"), "guitar": specs[0].get("guitar"), "gear": specs[0].get("gear"),
                "patches": [{"index": i, "name": s["name"], "amp": s.get("amp", {}).get("type", ""),
                             "note": (s.get("memo", "").split(":", 1) + [""])[1].strip()}
                            for i, s in enumerate(specs)],
            })
        for path in sorted(glob.glob(os.path.join(SONGS, "*.tsl"))):
            sid = os.path.basename(path)[:-4]
            if os.path.exists(os.path.join(SONGS, sid + ".json")):
                continue
            try:
                name, patches = PT.from_tsl(path)
            except Exception:
                continue
            res.append({"id": sid, "artist": "Imported", "title": name or sid, "cover": "", "added": _added(path),
                        "patches": [{"index": i, "name": bytes(b["PatchName"]).decode("ascii", "replace").strip() or f"Patch {i + 1}",
                                     "amp": "", "note": "From a .tsl file"} for i, b in enumerate(patches)]})
        res.sort(key=lambda x: -x["added"])           # newest songs first
        return res

    def import_tsl(self):
        """Pick a Tone Studio .tsl with a native dialog and add it to the library."""
        import shutil, webview
        picked = webview.windows[0].create_file_dialog(
            webview.OPEN_DIALOG, file_types=("Tone Studio liveset (*.tsl)",))
        if not picked:
            return {"ok": False, "cancelled": True}
        src = picked[0]
        try:
            PT.from_tsl(src)
        except Exception as e:
            return {"ok": False, "error": f"Could not read that file: {e}"}
        sid = re.sub(r"[^a-z0-9]+", "-", os.path.basename(src)[:-4].lower()).strip("-") or "imported"
        dst = os.path.join(SONGS, sid + ".tsl")
        if os.path.abspath(src) != os.path.abspath(dst):
            shutil.copyfile(src, dst)
        return {"ok": True, "id": sid}

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
        if not _ok_sid(song_id):
            return {"ok": False, "error": "Invalid song id"}
        path = os.path.join(SONGS, song_id + ".json")
        if os.path.exists(path):
            specs = json.load(open(path))
            specs = specs if isinstance(specs, list) else [specs]
            spec = specs[int(index)]
            blocks = PT.build(spec)
            label = spec["name"]
        else:
            _, patches = PT.from_tsl(os.path.join(SONGS, song_id + ".tsl"))
            blocks = patches[int(index)]
            label = bytes(blocks["PatchName"]).decode("ascii", "replace").strip()
        with self._lock:
            try:
                self._connect()
                PT.apply_live(blocks, self._inp, self._out)
            except Exception as e:
                self._drop()
                return {"ok": False, "error": str(e)}
        self._push_recent(song_id)
        return {"ok": True, "patch": label}

    # -------------------------------------------------------------- opening sound (played natively: not subject to web autoplay rules)
    def play_intro(self):
        return {"ok": osutil.play_sound(os.path.join(ROOT, "assets", "tonenai-intro.wav"))}

    def stop_intro(self):
        osutil.stop_sound()
        return {"ok": True}

    # -------------------------------------------------------------- practice: status (learning / played) + your own notes
    def song_meta(self):
        try:
            m = json.load(open(SONG_META))
            return m if isinstance(m, dict) else {}
        except (OSError, ValueError):
            return {}

    def set_song_meta(self, song_id, patch):
        if not re.fullmatch(r"[a-z0-9][a-z0-9\-]*", song_id or "") or not isinstance(patch, dict):
            return {"ok": False, "error": "Invalid song"}
        meta = self.song_meta()
        cur = dict(meta.get(song_id) or {})
        if "status" in patch:
            st = patch["status"] or ""
            if st not in ("", "learning", "played"):
                return {"ok": False, "error": "Invalid status"}
            cur["status"] = st
        if "notes" in patch:
            cur["notes"] = str(patch["notes"] or "")[:2000]
        cur = {k: v for k, v in cur.items() if v}            # drop empty fields
        if cur:
            meta[song_id] = cur
        else:
            meta.pop(song_id, None)
        os.makedirs(DATA, exist_ok=True)
        json.dump(meta, open(SONG_META, "w"), indent=1, ensure_ascii=False)
        return {"ok": True, "meta": meta}

    # -------------------------------------------------------------- language (en / pt-BR)
    def get_lang(self):
        try:
            v = json.load(open(SETTINGS)).get("lang")
            return v if v in LANG_NAMES else None
        except (OSError, ValueError, AttributeError):
            return None

    def set_lang(self, lang):
        if lang not in LANG_NAMES:
            return {"ok": False, "error": "Unknown language"}
        try:
            cfg = json.load(open(SETTINGS))
            cfg = cfg if isinstance(cfg, dict) else {}
        except (OSError, ValueError):
            cfg = {}
        cfg["lang"] = lang
        os.makedirs(DATA, exist_ok=True)
        json.dump(cfg, open(SETTINGS, "w"), indent=1)
        return {"ok": True}

    # -------------------------------------------------------------- AI provider (Claude by default, or any OpenAI-compatible server)
    def _settings(self):
        try:
            cfg = json.load(open(SETTINGS))
            return cfg if isinstance(cfg, dict) else {}
        except (OSError, ValueError):
            return {}

    def _save_settings(self, cfg):
        os.makedirs(DATA, exist_ok=True)
        json.dump(cfg, open(SETTINGS, "w"), indent=1)
        try:
            os.chmod(SETTINGS, 0o600) if sys.platform != "win32" else None               # may hold an API key
        except OSError:
            pass

    def get_ai(self):
        """Current provider for the UI. The API key itself never leaves the backend (only has_key)."""
        saved = self._settings().get("openai")
        o = {**local_llm.DEFAULTS, **(saved if isinstance(saved, dict) else {})}
        return {"provider": "openai" if local_llm.config(SETTINGS) else "claude", "presets": local_llm.PRESETS,
                "env": bool(os.environ.get("TONENAI_PROVIDER")),
                "openai": {"preset": o["preset"], "base_url": o["base_url"], "model": o["model"], "search": o["search"],
                           "has_key": bool(o.get("api_key") or os.environ.get("TONENAI_API_KEY"))}}

    def set_ai(self, data):
        data = data if isinstance(data, dict) else {}
        if data.get("provider") not in ("claude", "openai"):
            return {"ok": False, "error": "Unknown provider"}
        cfg = self._settings()
        cfg["provider"] = data["provider"]
        if data["provider"] == "openai":
            url, model = str(data.get("base_url", "")).strip().rstrip("/"), str(data.get("model", "")).strip()
            if not re.match(r"https?://\S+$", url):
                return {"ok": False, "error": "Enter the server URL (http://… or https://…)"}
            if not model:
                return {"ok": False, "error": "Pick a model first"}
            old = cfg.get("openai") if isinstance(cfg.get("openai"), dict) else {}
            new = {**old, "preset": str(data.get("preset") or "custom")[:20], "base_url": url, "model": model,
                   "search": "none" if data.get("search") == "none" else "duckduckgo"}
            key = str(data.get("api_key", "")).strip()
            if key:
                new["api_key"] = key
            elif data.get("clear_key") or url != str(old.get("base_url", "")).rstrip("/"):
                new.pop("api_key", None)           # a key belongs to the server it was entered for
            cfg["openai"] = new
        self._save_settings(cfg)
        return {"ok": True, "ai": self.get_ai()}

    def test_ai(self, data):
        """Check the server (with the form's values, not yet saved) and list its models."""
        data = data if isinstance(data, dict) else {}
        saved = self._settings().get("openai")
        saved = saved if isinstance(saved, dict) else {}
        url = str(data.get("base_url") or saved.get("base_url") or "").strip()
        cfg = {**local_llm.DEFAULTS, **saved, "base_url": url}
        if url.rstrip("/") != str(saved.get("base_url", "")).rstrip("/"):
            cfg["api_key"] = ""                    # never send a saved key to a different server
        key = str(data.get("api_key") or "").strip()
        if key:
            cfg["api_key"] = key
        if os.environ.get("TONENAI_API_KEY") and not key:
            cfg["api_key"] = os.environ["TONENAI_API_KEY"]
        if not re.match(r"https?://\S+$", cfg["base_url"]):
            return {"ok": False, "error": "Enter the server URL (http://… or https://…)"}
        try:
            return {"ok": True, "models": local_llm.list_models(cfg)}
        except RuntimeError as e:
            return {"ok": False, "error": str(e)}

    def _lang_note(self):
        lang = self.get_lang()
        if lang in (None, "en"):
            return ""
        return (f" LANGUAGE: the user reads {LANG_NAMES[lang]}. Write every free-text field you add or replace "
                "(patch memo after the 'Artist - Title: ' prefix, notes, tips, how, parts, style_feel, when, position, tone_notes) in "
                "natural, idiomatic prose in that language. Keep JSON keys, fixed values (yes/optional/no, confirmed/likely/unknown, genre names), patch names, "
                "gear and effect names and the 'Artist - Title: ' prefix exactly as the instructions define them.")

    # -------------------------------------------------------------- songs last sent to the amp
    def recent(self):
        try:
            return [x for x in json.load(open(RECENT)) if isinstance(x, str)]
        except (OSError, ValueError):
            return []

    def _push_recent(self, song_id):
        try:
            r = [song_id] + [x for x in self.recent() if x != song_id]
            os.makedirs(DATA, exist_ok=True)
            json.dump(r[:12], open(RECENT, "w"), indent=1)
        except OSError:
            pass                                  # history is a convenience; never fail a play over it

    # -------------------------------------------------------------- reveal .tsl
    def reveal(self, song_id):
        if not _ok_sid(song_id):
            return {"ok": False, "error": "Invalid song id"}
        tsl = os.path.join(SONGS, song_id + ".tsl")
        osutil.reveal(tsl if os.path.exists(tsl) else SONGS)
        return {"ok": True}

    # -------------------------------------------------------------- create with Claude
    def create_song(self, text):
        """Ask the Claude agent to research a song and write songs/<id>.json (+ .tsl). Poll job_status()."""
        if self._job.get("state") == "running":
            return {"ok": False, "error": "Already creating a song"}
        self._cancel.clear()
        self._job = {"state": "running", "note": "Starting Claude…", "query": text, "stage": 0, "round": 0, "t0": int(time.time() * 1000)}
        threading.Thread(target=self._run_job, args=(text,), daemon=True).start()
        return {"ok": True}

    def job_status(self):
        return dict(self._job)

    def _note(self, text):
        self._job = {**self._job, "note": text}

    # create-song stages: 0 research, 1 write the patches, 2 check against the amp's parameters, 3 save to the library
    TOOL_STAGE = {"WebSearch": 0, "WebFetch": 0, "Read": 0, "Write": 1, "Edit": 1, "Bash": 2}

    def _stage(self, stage, round_=0):
        """Set the stage explicitly (local-model path: it may go back from checking to writing when it has to fix something)."""
        self._job = {**self._job, "stage": stage, "round": round_, "tokens": 0, "thinking": False}

    def _tool_progress(self, tool):
        """Claude agent path: stages only move forward, and each Bash export counts as one check round."""
        if "stage" not in self._job:                 # band / whammy / guitar jobs have no stages
            return
        rnd = self._job.get("round", 0) + (1 if tool == "Bash" else 0)
        self._job = {**self._job, "stage": max(self._job["stage"], self.TOOL_STAGE.get(tool, 0)), "round": rnd}

    def _progress(self, tokens, thinking):
        self._job = {**self._job, "tokens": tokens, "thinking": thinking}

    def cancel_job(self):
        """Stop a running create/generate job (OpenAI-compatible providers; the Claude agent runs to the end)."""
        self._cancel.set()
        return {"ok": True}

    def _local_ctx(self):
        """Everything the local-model backend needs, without importing the Claude SDK."""
        sections = self._sections()
        skill = self._skill(sections)
        example = []
        for path in sorted(glob.glob(os.path.join(SONGS, "*.json"))):      # first song that has every section you use
            try:
                specs = json.load(open(path))
            except (OSError, ValueError):
                continue
            if isinstance(specs, list) and specs and all(specs[0].get(x) for x in sections):
                example = specs
                break
        if example:                                    # show only the sections you use, so the model does not copy the rest
            example = [dict(example[0]), *example[1:]]
            for key in ("band", "whammy", "guitar", "gear"):
                if key not in sections:
                    example[0].pop(key, None)
        pedals = {x["id"]: x for x in self._active_pedals() if x.get("kind") == "generic"}
        return {"skill": skill, "example": example, "tables": P.TABLES, "sections": sections, "pedals": pedals,
                "build": PT.build, "songs_dir": SONGS, "lang_note": self._lang_note(), "progress": self._progress, "cancel": self._cancel, "stage": self._stage}

    def _run_job(self, text):
        before = set(glob.glob(os.path.join(SONGS, "*.json")))
        try:
            cfg = local_llm.config(SETTINGS)
            if cfg:
                local_llm.create_song(cfg, text, self._local_ctx(), self._note)
            else:
                asyncio.run(self._agent(self._create_prompt(text)))
            new = sorted(set(glob.glob(os.path.join(SONGS, "*.json"))) - before)
            if not new:
                raise RuntimeError("Claude finished but did not create a song file")
            sid = os.path.basename(new[0])[:-5]
            self._stage(3)
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

    async def _agent(self, prompt):
        prompt += self._lang_note()
        from claude_agent_sdk import query, ClaudeAgentOptions, AssistantMessage, ToolUseBlock, TextBlock, HookMatcher
        self._last_text = ""
        skill = self._skill(self._sections())
        opts = ClaudeAgentOptions(
            cwd=ROOT, system_prompt={"type": "preset", "preset": "claude_code", "append": skill},
            allowed_tools=["Read", "Write", "Edit", "WebSearch", "WebFetch",
                           "Bash(" + VPY + " tools/patch.py export:*)"],
            permission_mode="acceptEdits", max_turns=25,
            hooks={"PreToolUse": [HookMatcher(matcher="Write|Edit|MultiEdit|NotebookEdit|Read|Grep|Glob|Bash", hooks=[_agent_guard])]})
        labels = {"WebSearch": "Researching the tone…", "WebFetch": "Reading about the gear…", "Edit": "Fixing the patch…",
                  "Write": "Writing the patch…", "Bash": "Building the .tsl…", "Read": "Reading notes…"}
        async for msg in query(prompt=prompt, options=opts):
            if isinstance(msg, AssistantMessage):
                for b in msg.content:
                    if isinstance(b, ToolUseBlock):
                        self._job = {**self._job, "note": labels.get(b.name, "Working…")}
                        self._tool_progress(b.name)
                    elif isinstance(b, TextBlock) and b.text.strip():
                        self._last_text = b.text.strip()

    def _create_prompt(self, text):
        return (
            f"The user searched their song library for: {text!r} and nothing matched. "
            "Figure out the song (artist and title) and research the guitar tone (amp, pedals, effects, "
            "rhythm vs lead parts) with a web search. Then create ONE new file songs/<artist>-<title>.json "
            "(lowercase slug) holding a JSON list of 1-3 patch specs, following the instructions. "
            "The first patch memo must start with 'Artist - Title: ' . Also add " + self._sections_text() + " to the FIRST patch. "
            "Validate and export by running "
            "`" + VPY + " tools/patch.py export songs/<slug>.json -o songs/<slug>.tsl`; fix any error it "
            "reports. Do NOT apply to the amp and do not touch other files. Reply with one short line when done.")

    def _sections_text(self):
        names = {"band": 'a "band" object (see Band setup)', "whammy": 'a "whammy" object (see Whammy DT setup)',
                 "guitar": 'a "guitar" object (see Guitar setup)'}
        parts = [names[x] for x in self._sections()]
        return parts[0] if len(parts) == 1 else ", ".join(parts[:-1]) + " and " + parts[-1] + " in the instructions"

    def _band_prompt(self, sid):
        return (
            f"Add (or replace) the \"band\" object on the FIRST patch of songs/{sid}.json, following 'Band setup' in the "
            "instructions. Look up the song's real tempo, key and structure with a web search. Edit only that file, "
            f"then run `{VPY} tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "Reply with one short line when done.")

    def _whammy_prompt(self, sid):
        return (
            f"Add (or replace) the \"whammy\" object on the FIRST patch of songs/{sid}.json, following 'Whammy DT setup' in the "
            "instructions. Use a web search to find the tuning actually used on the recording and where a whammy/octave/harmony "
            "effect is used. Edit only that file, "
            f"then run `{VPY} tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "If you cannot verify the tuning, say so in tips and set use to optional. Reply with one short line when done.")

    def _guitar_prompt(self, sid):
        return (
            f"Add (or replace) the \"guitar\" object on the FIRST patch of songs/{sid}.json, following 'Guitar setup' in the "
            "instructions. Use a web search to find which guitar(s) and pickups were really used on the recording and which "
            "pickup position sounds right. Edit only that file, "
            f"then run `{VPY} tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "If you cannot confirm the guitar, set confidence to unknown. Reply with one short line when done.")

    def _gear_prompt(self, sid, pedal):
        return (
            f"Add (or replace) the setup for the guitar pedal \"{pedal['name']}\" ({pedal.get('about') or 'guitar pedal'}) on the FIRST patch of songs/{sid}.json, "
            f"as the object gear[\"{pedal['id']}\"]. It has: \"use\" (\"yes\" if the song needs it, \"optional\" for a nice-to-have, \"no\"), "
            "\"headline\" (short value for a summary tile, max 40 characters), \"settings\" (a list of {\"name\", \"value\"} knob or switch "
            "settings, at most 12), \"steps\" (at most 8 short how-to steps) and \"tips\" (one or two sentences). "
            + (("The pedal's real controls (use exactly these names in settings, values within these ranges):\n" + local_llm.profile_text(pedal["profile"]) + "\n"
                + (f"Watch out: {pedal['profile']['watch']}\n" if pedal["profile"].get("watch") else "")) if (pedal.get("profile") or {}).get("controls") else "")
            + "Use a web search to see how "
            "the pedal was used on the recording. Edit only that file, "
            f"then run `{VPY} tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "If you cannot verify something, say so in tips and set use to optional. Reply with one short line when done.")

    def generate_section(self, song_id, kind):
        """One entry point for every setup the song screen can generate: guitar, band, whammy or gear:<pedal id>."""
        active = self._active_pedals()
        if kind == "guitar":
            pass
        elif kind in ("band", "whammy"):
            if not any(SECTION_OF_KIND.get(x.get("kind")) == kind for x in active):
                return {"ok": False, "error": "That pedal is not in your gear"}
        elif kind.startswith("gear:"):
            if not any(x["id"] == kind[5:] and x.get("kind") == "generic" for x in active):
                return {"ok": False, "error": "That pedal is not in your gear"}
        else:
            return {"ok": False, "error": "Unknown setup"}
        return self._generate(song_id, kind)

    def generate_guitar(self, song_id):
        return self._generate(song_id, "guitar")

    def generate_whammy(self, song_id):
        return self._generate(song_id, "whammy")

    def generate_band(self, song_id):
        return self._generate(song_id, "band")

    def _generate(self, song_id, kind):
        """Ask the Claude agent to add the DigiTech Trio+ band setup to an existing song."""
        if self._job.get("state") == "running":
            return {"ok": False, "error": "Already working on something"}
        if not re.fullmatch(r"[a-z0-9][a-z0-9\-]*", song_id or "") or not os.path.exists(os.path.join(SONGS, song_id + ".json")):
            return {"ok": False, "error": "Song not found"}
        self._cancel.clear()
        self._job = {"state": "running", "note": "Starting Claude…", "query": kind.split(":")[0] + " setup", "kind": kind, "id": song_id}
        threading.Thread(target=self._run_band, args=(song_id, kind), daemon=True).start()
        return {"ok": True}

    def _run_band(self, sid, kind="band"):
        try:
            for attempt in range(2):          # the agent occasionally finishes without saving; try once more
                cfg = local_llm.config(SETTINGS)
                if cfg:
                    local_llm.add_section(cfg, sid, kind, self._local_ctx(), self._note)
                else:
                    if kind.startswith("gear:"):
                        pedal = next(x for x in self._active_pedals() if x["id"] == kind[5:])
                        asyncio.run(self._agent(self._gear_prompt(sid, pedal)))
                    else:
                        asyncio.run(self._agent({"band": self._band_prompt, "whammy": self._whammy_prompt, "guitar": self._guitar_prompt}[kind](sid)))
                self._validate(sid)
                specs = json.load(open(os.path.join(SONGS, sid + ".json")))
                specs = specs if isinstance(specs, list) else [specs]
                if kind.startswith("gear:"):
                    g = (specs[0].get("gear") or {}).get(kind[5:])
                    pedal = next((x for x in self._active_pedals() if x["id"] == kind[5:]), {})
                    problems = local_llm.check_gear(g, pedal.get("profile")) if g else ["the setup was not saved"]
                    if not problems:
                        break
                    self._last_text = "The setup did not match the pedal: " + problems[0]
                    if g:                                   # do not keep a setup that fails the check
                        specs[0]["gear"].pop(kind[5:], None)
                        json.dump(specs, open(os.path.join(SONGS, sid + ".json"), "w", encoding="utf8"), indent=1, ensure_ascii=False)
                elif specs[0].get(kind):
                    break
            else:
                raise RuntimeError((self._last_text or f"Claude could not add the {kind} setup for this song")[:300])
            self._job = {"state": "done", "id": sid, "kind": kind}
        except Exception as e:
            msg = str(e)
            if "Not logged in" in msg or "authentication" in msg.lower():
                msg = "Claude is not signed in. See the README: run the one-time login."
            self._job = {"state": "error", "error": msg, "kind": kind}

    # -------------------------------------------------------------- gear: the amp and pedals you own
    def _catalog(self):
        cat = _read_json(CATALOG, {})
        return {"amps": [a for a in cat.get("amps", []) if isinstance(a, dict) and a.get("id")],
                "pedals": [x for x in cat.get("pedals", []) if isinstance(x, dict) and x.get("id")]}

    def _custom_pedals(self, raw):
        """Your own pedals (data/rig.json): a name and what it does; the AI works out the settings per song."""
        out, seen = [], set()
        for c in raw if isinstance(raw, list) else []:
            if not isinstance(c, dict) or not str(c.get("name", "")).strip():
                continue
            name = str(c["name"]).strip()[:40]
            cid = c.get("id") if re.fullmatch(r"custom-[a-z0-9-]{1,30}", str(c.get("id", ""))) else "custom-" + (_slug(name) or "pedal")
            if cid in seen:
                continue
            seen.add(cid)
            ped = {"id": cid, "name": name, "short": _short(name), "icon": "🎛", "kind": "generic", "custom": True,
                   "about": str(c.get("about", "")).strip()[:200]}
            if isinstance(c.get("profile"), dict) and not local_llm.check_profile(c["profile"]):
                ped["profile"] = c["profile"]
            out.append(ped)
        return out

    def _rig(self):
        cat, raw = self._catalog(), _read_json(RIG, {})
        raw = raw if isinstance(raw, dict) else {}
        custom = self._custom_pedals(raw.get("custom"))
        known = {x["id"] for x in cat["pedals"]} | {c["id"] for c in custom}
        if isinstance(raw.get("pedals"), list):
            pedals = [i for i in raw["pedals"] if i in known]
        else:                                                   # never saved: the catalog's defaults (Whammy DT, Trio+)
            pedals = [x["id"] for x in cat["pedals"] if x.get("default")]
        amps = [a["id"] for a in cat["amps"]]
        amp = raw.get("amp") if raw.get("amp") in amps else (amps[0] if amps else "")
        return {"amp": amp, "pedals": pedals, "custom": custom}

    def get_gear(self):
        cat, rig = self._catalog(), self._rig()
        j = self._job
        custom = []
        for c in rig["custom"]:                         # research = none | running | failed (a saved profile means it is done)
            r = {"state": "none"}
            if j.get("kind") == "profile" and j.get("id") == c["id"]:
                r = {"state": "running", "note": j.get("note", "")} if j.get("state") == "running" else \
                    {"state": "failed", "error": j.get("error", "")} if j.get("state") == "error" else r
            custom.append({**c, "research": r})
        return {"amps": cat["amps"], "pedals": cat["pedals"] + custom, "rig": {"amp": rig["amp"], "pedals": rig["pedals"]}}

    def set_gear(self, data):
        """data = {amp, pedals: [ids you own], custom: [{id?, name, about}]}"""
        if not isinstance(data, dict):
            return {"ok": False, "error": "Bad request"}
        old = {c["id"]: c for c in self._rig()["custom"]}
        rig = {"amp": data.get("amp"), "pedals": data.get("pedals") if isinstance(data.get("pedals"), list) else [],
               "custom": self._custom_pedals(data.get("custom"))}
        for c in rig["custom"]:                         # profiles belong to the server: the window cannot overwrite them here
            if old.get(c["id"], {}).get("profile"):
                c["profile"] = old[c["id"]]["profile"]
            else:
                c.pop("profile", None)
        os.makedirs(DATA, exist_ok=True)
        json.dump(rig, open(RIG, "w", encoding="utf8"), indent=1, ensure_ascii=False)
        for c in rig["custom"]:
            if c["id"] not in old:                      # a new pedal: look it up right away
                self._start_profile(c["id"])
                break
        return {"ok": True, **self.get_gear()}

    def research_pedal(self, pedal_id):
        """(Re)run the pedal research, e.g. after it failed or when you want a fresh look."""
        return self._start_profile(pedal_id, force=True)

    def save_pedal_profile(self, pedal_id, profile):
        """You corrected a pedal's profile (for example removed a control that does not exist)."""
        if not isinstance(profile, dict):
            return {"ok": False, "error": "Bad request"}
        problems = local_llm.check_profile(profile)
        if problems:
            return {"ok": False, "error": problems[0]}
        if not self._store_profile(pedal_id, {**profile, "reviewed": True}):
            return {"ok": False, "error": "Pedal not found"}
        return {"ok": True, **self.get_gear()}

    def _store_profile(self, pedal_id, profile):
        raw = _read_json(RIG, {})
        raw = raw if isinstance(raw, dict) else {}
        for c in raw.get("custom", []) if isinstance(raw.get("custom"), list) else []:
            if isinstance(c, dict) and c.get("id") == pedal_id:
                c["profile"] = profile
                json.dump(raw, open(RIG, "w", encoding="utf8"), indent=1, ensure_ascii=False)
                return True
        return False

    def _start_profile(self, pedal_id, force=False):
        pedal = next((c for c in self._rig()["custom"] if c["id"] == pedal_id), None)
        if not pedal:
            return {"ok": False, "error": "Pedal not found"}
        if self._job.get("state") == "running":
            return {"ok": False, "error": "Already working on something"}
        if pedal.get("profile") and not force:
            return {"ok": True}
        self._cancel.clear()
        self._job = {"state": "running", "note": "Researching the pedal…", "query": pedal["name"], "kind": "profile", "id": pedal_id}
        threading.Thread(target=self._run_profile, args=(pedal,), daemon=True).start()
        return {"ok": True}

    def _profile_prompt(self, pedal):
        return (f"Research the guitar pedal \"{pedal['name']}\" (the owner says: {pedal.get('about') or 'no description'}) with a web search; "
                "read the manufacturer's manual page if you can find it. Do not edit any files. Reply with ONE JSON object and nothing else. "
                + local_llm.PROFILE_GUIDE)

    def _run_profile(self, pedal):
        try:
            cfg = local_llm.config(SETTINGS)
            if cfg:
                obj = local_llm.research_pedal(cfg, pedal, self._local_ctx(), self._note)
            else:
                obj, problems = None, ["no reply"]
                for attempt in range(2):
                    asyncio.run(self._agent(self._profile_prompt(pedal) if attempt == 0 else
                                            "Fix these problems and reply with the corrected JSON object only:\n- " + "\n- ".join(problems)))
                    try:
                        data = local_llm.extract_json(self._last_text)
                        obj = data.get("profile", data) if isinstance(data, dict) else data
                        problems = local_llm.check_profile(obj)
                    except Exception as e:
                        problems = [f"{type(e).__name__}: {e}"]
                    if not problems:
                        break
                else:
                    raise RuntimeError("Could not describe this pedal: " + problems[0][:200])
            if not self._store_profile(pedal["id"], obj):
                raise RuntimeError("The pedal was removed while it was being researched")
            self._job = {"state": "done", "id": pedal["id"], "kind": "profile"}
        except Exception as e:
            msg = str(e)
            if "Not logged in" in msg or "authentication" in msg.lower():
                msg = "Claude is not signed in. See the README: run the one-time login."
            self._job = {"state": "error", "error": msg, "kind": "profile", "id": pedal["id"]}

    def _active_pedals(self):
        rig, cat = self._rig(), self._catalog()
        allp = {x["id"]: x for x in cat["pedals"] + rig["custom"]}
        return [allp[i] for i in rig["pedals"] if i in allp]

    def _sections(self):
        """Song sections the AI writes when it creates a song: the guitar always, plus one per built-in pedal you own."""
        return ["guitar"] + [SECTION_OF_KIND[x.get("kind")] for x in self._active_pedals() if x.get("kind") in SECTION_OF_KIND]

    def _skill(self, sections):
        """The katana-patch skill text without the pedal sections you don't use."""
        text = re.sub(r"^---.*?---", "", open(os.path.join(ROOT, ".claude", "skills", "katana-patch", "SKILL.md"), encoding="utf8").read(), flags=re.S)
        head, *blocks = re.split(r"(?m)^(?=## )", text)
        drop = {"## Band setup": "band", "## Whammy DT setup": "whammy"}
        keep = [b for b in blocks if not any(b.startswith(h) and sec not in sections for h, sec in drop.items())]
        return (head + "".join(keep)).replace(".venv/bin/python", VPY)

    # -------------------------------------------------------------- cover art (iTunes Search API, cached)
    def cover(self, song_id):
        """Find and cache album art for a song. Returns {"cover": relative url or ""}."""
        if not _ok_sid(song_id):
            return {"cover": ""}
        dst, miss = os.path.join(COVERS, song_id + ".jpg"), os.path.join(COVERS, song_id + ".none")
        if os.path.exists(dst):
            return {"cover": _cover_url(song_id)}
        if os.path.exists(miss):
            return {"cover": ""}
        song = next((x for x in self.songs() if x["id"] == song_id), None)
        if not song or not song["artist"]:
            return {"cover": ""}
        title = re.sub(r"\s*\(.*?\)", "", song["title"]).strip()
        try:
            os.makedirs(COVERS, exist_ok=True)
            url = "https://itunes.apple.com/search?" + urllib.parse.urlencode(
                {"term": f"{song['artist']} {title}", "entity": "song", "limit": 8})
            res = json.load(urllib.request.urlopen(url, timeout=8))["results"]
            hit = next((r for r in res if r["artistName"].lower().startswith(song["artist"].lower()[:6])), None)
            if not hit:
                raise LookupError("no match")
            art = hit["artworkUrl100"].replace("100x100bb", "600x600bb")
            host = urllib.parse.urlparse(art)
            if host.scheme != "https" or not (host.hostname or "").endswith(".mzstatic.com"):
                raise LookupError("unexpected image host")       # only Apple's image servers
            data = urllib.request.urlopen(art, timeout=10).read(5_000_000)
            if not data.startswith(b"\xff\xd8"):
                raise LookupError("not a JPEG")
            open(dst, "wb").write(data)
            return {"cover": _cover_url(song_id)}
        except LookupError:
            open(miss, "w").close()
        except Exception:
            pass  # offline etc.: try again next launch
        return {"cover": ""}

    # -------------------------------------------------------------- song search (library + iTunes catalog)
    SEARCH_DEFAULT, SEARCH_MIN, SEARCH_MAX = 10, 3, 30

    def get_search_limit(self):
        try:
            v = int(self._settings().get("search_limit"))
        except (TypeError, ValueError):
            return self.SEARCH_DEFAULT
        return max(self.SEARCH_MIN, min(self.SEARCH_MAX, v))

    def set_search_limit(self, n):
        try:
            n = int(n)
        except (TypeError, ValueError):
            return {"ok": False, "error": "Enter a number"}
        if not self.SEARCH_MIN <= n <= self.SEARCH_MAX:
            return {"ok": False, "error": f"Choose between {self.SEARCH_MIN} and {self.SEARCH_MAX}"}
        cfg = self._settings()
        cfg["search_limit"] = n
        self._save_settings(cfg)
        return {"ok": True, "limit": n}

    def search_songs(self, text, limit=None, by_artist=False):
        """Up to `limit` results (setting "search_limit", default 10): songs already in your library first, then the
        best catalog matches (iTunes) for other songs, until the list is full.
        by_artist=True (clicking a band name): ALL your songs by exactly that artist, then up to `limit` more songs by them."""
        limit = int(limit) if limit else self.get_search_limit()
        norm = lambda t: re.sub(r"[^a-z0-9 ]", "", re.sub(r"\s*[\(\[].*?[\)\]]|\s+-\s+.*$", "", t.lower())).strip()
        if by_artist:
            return self._search_artist(text, limit, norm)
        words = [w for w in re.findall(r"[a-z0-9]+", text.lower())]
        lib = []
        for song in self.songs():
            hay = (song["artist"] + " " + song["title"]).lower()
            if words and all(w in hay for w in words):
                lib.append({"inLibrary": True, "id": song["id"], "artist": song["artist"], "title": song["title"],
                            "album": "", "art": "", "patches": len(song["patches"])})
        items, seen = lib[:limit], {(norm(x["artist"]), norm(x["title"])) for x in lib}
        if len(items) < limit:
            try:
                # ask for plenty: remasters, live cuts and songs you already own are dropped below
                url = "https://itunes.apple.com/search?" + urllib.parse.urlencode(
                    {"term": text, "entity": "song", "limit": min(200, max(25, limit * 4))})
                for r in json.load(urllib.request.urlopen(url, timeout=8))["results"]:
                    key = (norm(r["artistName"]), norm(r["trackName"]))
                    if key in seen:
                        continue
                    seen.add(key)
                    items.append({"inLibrary": False, "artist": r["artistName"], "title": r["trackName"],
                                  "album": r.get("collectionName", ""),
                                  "art": r["artworkUrl100"].replace("100x100bb", "300x300bb")})
                    if len(items) >= limit:
                        break
            except Exception as e:
                if not items:
                    return {"ok": False, "error": f"Search failed (offline?): {e}"}
        return {"ok": True, "items": items}

    def _search_artist(self, name, limit, norm):
        want = norm(name)
        lib = [{"inLibrary": True, "id": x["id"], "artist": x["artist"], "title": x["title"], "album": "", "art": "",
                "patches": len(x["patches"])} for x in self.songs() if norm(x["artist"]) == want]
        seen, items = {(want, norm(x["title"])) for x in lib}, list(lib)
        extra = []
        try:
            url = "https://itunes.apple.com/search?" + urllib.parse.urlencode(
                {"term": name, "entity": "song", "attribute": "artistTerm", "limit": min(200, max(50, limit * 5))})
            rows = json.load(urllib.request.urlopen(url, timeout=8))["results"]
            exact = [r for r in rows if norm(r["artistName"]) == want]
            for r in (exact or [r for r in rows if want in norm(r["artistName"]) or norm(r["artistName"]) in want]):
                key = (want, norm(r["trackName"]))
                if key in seen:
                    continue
                seen.add(key)
                extra.append({"inLibrary": False, "artist": r["artistName"], "title": r["trackName"],
                              "album": r.get("collectionName", ""), "art": r["artworkUrl100"].replace("100x100bb", "300x300bb")})
                if len(extra) >= limit:
                    break
        except Exception as e:
            if not items:
                return {"ok": False, "error": f"Search failed (offline?): {e}"}
        return {"ok": True, "items": items + extra, "artist": name, "inLibrary": len(lib), "toAdd": len(extra)}

    # -------------------------------------------------------------- delete (to the Trash / Recycle Bin, so it is reversible)
    def delete_song(self, song_id):
        if not re.fullmatch(r"[a-z0-9][a-z0-9\-]*", song_id or ""):
            return {"ok": False, "error": "Invalid song id"}
        files = [os.path.join(SONGS, song_id + ext) for ext in (".json", ".tsl")] + \
                [os.path.join(COVERS, song_id + ext) for ext in (".jpg", ".none")]
        files = [f for f in files if os.path.exists(f)]
        if not files:
            return {"ok": False, "error": "Song not found"}
        osutil.trash(files, os.path.join(ROOT, ".trash"))   # Trash / Recycle Bin; a recoverable copy in .trash if that fails
        return {"ok": True}

    # -------------------------------------------------------------- my guitars (local, per user)
    def guitars(self):
        try:
            return json.load(open(GUITARS))
        except (OSError, ValueError):
            return []

    def save_guitar(self, guitar):
        """Add or update a guitar: {id?, name, kind, pickups, notes}."""
        name = (guitar.get("name") or "").strip()
        if not name:
            return {"ok": False, "error": "Give the guitar a name"}
        pickups = guitar.get("pickups", "humbucker")
        if pickups not in ("humbucker", "single-coil", "p90", "sss", "hh", "hs", "hss", "hsh", "other", "acoustic"):
            return {"ok": False, "error": "Unknown pickup type"}
        items = self.guitars()
        gid = guitar.get("id") or re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "guitar"
        entry = {"id": gid, "name": name[:60], "brand": (guitar.get("brand") or "").strip()[:40],
                 "kind": (guitar.get("kind") or "")[:40], "pickups": pickups,
                 "pickups_other": (guitar.get("pickups_other") or "").strip()[:40] if pickups == "other" else "",
                 "finish": re.sub(r"[^a-z]", "", (guitar.get("finish") or "").lower())[:20],
                 "notes": (guitar.get("notes") or "")[:200]}
        items = [g for g in items if g["id"] != gid] + [entry]
        os.makedirs(DATA, exist_ok=True)
        json.dump(items, open(GUITARS, "w"), indent=1)
        return {"ok": True, "guitars": items}

    def delete_guitar(self, gid):
        items = [g for g in self.guitars() if g["id"] != gid]
        os.makedirs(DATA, exist_ok=True)
        json.dump(items, open(GUITARS, "w"), indent=1)
        return {"ok": True, "guitars": items}

    # -------------------------------------------------------------- livesets (set lists for the amp's banks)
    def _blocks_for(self, song_id, index):
        """Full patch blocks for a library patch (from its spec, or from an imported .tsl)."""
        path = os.path.join(SONGS, song_id + ".json")
        if os.path.exists(path):
            specs = json.load(open(path)); specs = specs if isinstance(specs, list) else [specs]
            return PT.build(specs[int(index)]), specs[int(index)]["name"]
        _, patches = PT.from_tsl(os.path.join(SONGS, song_id + ".tsl"))
        b = patches[int(index)]
        return b, bytes(b["PatchName"]).decode("ascii", "replace").strip()

    def livesets(self):
        try:
            return json.load(open(LIVESETS))
        except (OSError, ValueError):
            return []

    def _save_livesets(self, items):
        os.makedirs(DATA, exist_ok=True)
        json.dump(items, open(LIVESETS, "w"), indent=1)

    def liveset_save(self, ls):
        """Create or update a liveset: {id?, name, slots: {A1..B4: {song, index} | null}, notes}."""
        name = (ls.get("name") or "").strip()[:40]
        if not name:
            return {"ok": False, "error": "Give the liveset a name"}
        slots = {}
        for key in BANK_KEYS:
            v = (ls.get("slots") or {}).get(key)
            if v and v.get("song") and re.fullmatch(r"[a-z0-9][a-z0-9\-]*", v["song"]):
                slots[key] = {"song": v["song"], "index": int(v.get("index", 0))}
        items = self.livesets()
        lid = ls.get("id") or (re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "liveset") + "-" + str(int(time.time()) % 100000)
        entry = {"id": lid, "name": name, "slots": slots, "notes": (ls.get("notes") or "")[:300]}
        items = [x for x in items if x["id"] != lid] + [entry]
        self._save_livesets(items)
        return {"ok": True, "livesets": items, "id": lid}

    def liveset_delete(self, lid):
        items = [x for x in self.livesets() if x["id"] != lid]
        self._save_livesets(items)
        return {"ok": True, "livesets": items}

    def amp_slots(self):
        """Read the names stored in the amp's 9 memory slots (Panel, A1-A4, B1-B4)."""
        with self._lock:
            try:
                self._connect()
                k.dt1(self._out, 0x7F000001, [1]); time.sleep(0.15)
                res = []
                for i in range(9):
                    d = k.rq1(self._inp, self._out, P.slot_base(i), 16, tries=3)
                    res.append({"index": i, "key": P.SLOT_KEYS[i], "label": P.SLOT_LABELS[i],
                                "name": bytes(d).decode("ascii", "replace").strip() if len(d) == 16 else None})
                if all(r["name"] is None for r in res):
                    raise IOError("The amp did not answer")
                return {"ok": True, "slots": res}
            except Exception as e:
                self._drop()
                return {"ok": False, "error": str(e)}

    def liveset_send(self, lid, keys=None):
        """Write a liveset into the amp's banks. Each slot's previous contents are backed up first (data/backups)."""
        ls = next((x for x in self.livesets() if x["id"] == lid), None)
        if not ls:
            return {"ok": False, "error": "Liveset not found"}
        todo = [(key, ls["slots"][key]) for key in BANK_KEYS if key in ls["slots"] and (keys is None or key in keys)]
        if not todo:
            return {"ok": False, "error": "Nothing assigned to send"}
        try:
            prepared = [(key, v, *self._blocks_for(v["song"], v["index"])) for key, v in todo]
        except Exception as e:
            return {"ok": False, "error": f"Could not build a patch: {e}"}
        with self._lock:
            try:
                self._connect()
                os.makedirs(BACKUPS, exist_ok=True)
                stamp = time.strftime("%Y%m%d-%H%M%S"); saved = {}
                for key, v, blocks, name in prepared:                       # 1) back up what is there now (skip empty INIT slots)
                    idx = P.SLOT_KEYS.index(key); base = P.slot_base(idx)
                    cur = bytes(k.rq1(self._inp, self._out, base, 16, tries=3)).decode("ascii", "replace").strip()
                    if cur and "INIT TONE" not in cur.upper():
                        saved[key] = PT.snapshot(self._inp, self._out, base=base)
                if saved:
                    json.dump({k_: PT.to_tsl([b], k_)["data"][0][0]["paramSet"] for k_, b in saved.items()}, open(os.path.join(BACKUPS, f"slots-{stamp}.json"), "w"))
                k.dt1(self._out, 0x7F000001, [1]); time.sleep(0.15)
                for key, v, blocks, name in prepared:                       # 2) write the new patches
                    PT.write_blocks(blocks, self._out, P.slot_base(P.SLOT_KEYS.index(key)), [b for b in P.TSL_BLOCKS if b != "Status"])
                time.sleep(0.3)
                names = {}
                for key, v, blocks, name in prepared:                       # 3) read back to verify
                    names[key] = bytes(k.rq1(self._inp, self._out, P.slot_base(P.SLOT_KEYS.index(key)), 16, tries=3)).decode("ascii", "replace").strip()
                bad = [key for key, v, blocks, name in prepared if names.get(key, "") != name.strip()[:16]]
                return {"ok": not bad, "written": len(prepared), "names": names, "backup": bool(saved), "error": ("Verification failed for " + ", ".join(bad)) if bad else ""}
            except Exception as e:
                self._drop()
                return {"ok": False, "error": str(e)}

    def liveset_export(self, lid):
        """Export a liveset as a Tone Studio .tsl file (patches in A1..B4 order)."""
        ls = next((x for x in self.livesets() if x["id"] == lid), None)
        if not ls:
            return {"ok": False, "error": "Liveset not found"}
        patches = []
        for key in BANK_KEYS:
            if key in ls["slots"]:
                v = ls["slots"][key]
                blocks, name = self._blocks_for(v["song"], v["index"]); patches.append(blocks)
        if not patches:
            return {"ok": False, "error": "Nothing assigned to export"}
        os.makedirs(EXPORTS, exist_ok=True)
        path = os.path.join(EXPORTS, re.sub(r"[^A-Za-z0-9]+", "-", ls["name"]).strip("-") + ".tsl")
        json.dump(PT.to_tsl(patches, ls["name"]), open(path, "w"), indent=1)
        osutil.reveal(path)
        return {"ok": True, "path": path, "count": len(patches)}

    # -------------------------------------------------------------- guitar tabs links (your own tab site; nothing is downloaded)
    def tabs_config(self):
        try:
            return json.load(open(TABS_CFG))
        except (OSError, ValueError):
            return {"service": "", "template": "", "links": {}}

    def save_tabs_config(self, cfg):
        """{service: ultimate-guitar|songsterr|google|custom, template: URL with {q}, links: {song_id: URL}}"""
        cur = self.tabs_config()
        for key in ("service", "template"):
            if key in cfg:
                cur[key] = str(cfg[key] or "")[:300]
        if "links" in cfg:
            cur["links"] = {k: v for k, v in cfg["links"].items() if re.fullmatch(r"[a-z0-9][a-z0-9\-]*", k) and re.match(r"https?://", v or "")}
        os.makedirs(DATA, exist_ok=True)
        json.dump(cur, open(TABS_CFG, "w"), indent=1)
        return {"ok": True, "config": cur}

    def open_url(self, url):
        """Open a web page in the default browser (http/https only)."""
        if not re.match(r"https?://", url or ""):
            return {"ok": False, "error": "Not a web link"}
        osutil.open_url(url)
        return {"ok": True}

    # -------------------------------------------------------------- favourites (your own list, stored locally)
    def favorites(self):
        try:
            return [x for x in json.load(open(FAVS)) if isinstance(x, str)]
        except (OSError, ValueError):
            return []

    def set_favorite(self, song_id, on):
        if not re.fullmatch(r"[a-z0-9][a-z0-9\-]*", song_id or ""):
            return {"ok": False, "error": "Invalid song"}
        favs = [x for x in self.favorites() if x != song_id]
        if on:
            favs.insert(0, song_id)                # the most recently favourited first
        os.makedirs(DATA, exist_ok=True)
        json.dump(favs, open(FAVS, "w"), indent=1)
        return {"ok": True, "favorites": favs}
