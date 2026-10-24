"""Backend for the Tonai desktop app: song library + amp control (exposed to the UI via pywebview)."""
import asyncio, urllib.parse, urllib.request, glob, json, os, re, subprocess, sys, threading, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import katana as k      # noqa: E402
import params as P      # noqa: E402
import patch as PT      # noqa: E402

SONGS = os.path.join(ROOT, "songs")
COVERS = os.path.join(SONGS, "covers")


def _split_title(memo, fallback):
    head = (memo or "").split(":")[0]
    if " - " in head:
        artist, title = head.split(" - ", 1)
        return artist.strip(), title.strip()
    return "", fallback


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
BACKUPS = os.path.join(DATA, "backups")
EXPORTS = os.path.join(DATA, "exports")
BANK_KEYS = P.SLOT_KEYS[1:]            # A1..A4, B1..B4: the 8 channels that can be written (the Panel cannot)


class Service:
    def __init__(self):
        self._lock = threading.Lock()
        self._inp = self._out = None
        self._job = {"state": "idle"}
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
                "id": sid, "artist": artist, "title": title, "cover": "", "band": specs[0].get("band"), "whammy": specs[0].get("whammy"), "guitar": specs[0].get("guitar"),
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
            res.append({"id": sid, "artist": "Imported", "title": name or sid, "cover": "",
                        "patches": [{"index": i, "name": bytes(b["PatchName"]).decode("ascii", "replace").strip() or f"Patch {i + 1}",
                                     "amp": "", "note": "From a .tsl file"} for i, b in enumerate(patches)]})
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
        return {"ok": True, "patch": label}

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
            asyncio.run(self._agent(self._create_prompt(text)))
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

    async def _agent(self, prompt):
        from claude_agent_sdk import query, ClaudeAgentOptions, AssistantMessage, ToolUseBlock, TextBlock
        self._last_text = ""
        skill = open(os.path.join(ROOT, ".claude", "skills", "katana-patch", "SKILL.md")).read()
        skill = re.sub(r"^---.*?---", "", skill, flags=re.S)
        opts = ClaudeAgentOptions(
            cwd=ROOT, system_prompt={"type": "preset", "preset": "claude_code", "append": skill},
            allowed_tools=["Read", "Write", "Edit", "WebSearch", "WebFetch",
                           "Bash(.venv/bin/python tools/patch.py export:*)"],
            permission_mode="acceptEdits", max_turns=25)
        labels = {"WebSearch": "Researching the tone…", "WebFetch": "Reading about the gear…",
                  "Write": "Writing the patch…", "Bash": "Building the .tsl…", "Read": "Reading notes…"}
        async for msg in query(prompt=prompt, options=opts):
            if isinstance(msg, AssistantMessage):
                for b in msg.content:
                    if isinstance(b, ToolUseBlock):
                        self._job = {**self._job, "note": labels.get(b.name, "Working…")}
                    elif isinstance(b, TextBlock) and b.text.strip():
                        self._last_text = b.text.strip()

    def _create_prompt(self, text):
        return (
            f"The user searched their song library for: {text!r} and nothing matched. "
            "Figure out the song (artist and title) and research the guitar tone (amp, pedals, effects, "
            "rhythm vs lead parts) with a web search. Then create ONE new file songs/<artist>-<title>.json "
            "(lowercase slug) holding a JSON list of 1-3 patch specs, following the instructions. "
            "The first patch memo must start with 'Artist - Title: ' . Also add a \"band\" object and a \"whammy\" object "
            "and a \"guitar\" object to the FIRST patch (see 'Band setup', 'Whammy DT setup' and 'Guitar setup' in the instructions). Validate and export by running "
            "`.venv/bin/python tools/patch.py export songs/<slug>.json -o songs/<slug>.tsl`; fix any error it "
            "reports. Do NOT apply to the amp and do not touch other files. Reply with one short line when done.")

    def _band_prompt(self, sid):
        return (
            f"Add (or replace) the \"band\" object on the FIRST patch of songs/{sid}.json, following 'Band setup' in the "
            "instructions. Look up the song's real tempo, key and structure with a web search. Edit only that file, "
            f"then run `.venv/bin/python tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "Reply with one short line when done.")

    def _whammy_prompt(self, sid):
        return (
            f"Add (or replace) the \"whammy\" object on the FIRST patch of songs/{sid}.json, following 'Whammy DT setup' in the "
            "instructions. Use a web search to find the tuning actually used on the recording and where a whammy/octave/harmony "
            "effect is used. Edit only that file, "
            f"then run `.venv/bin/python tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "If you cannot verify the tuning, say so in tips and set use to optional. Reply with one short line when done.")

    def _guitar_prompt(self, sid):
        return (
            f"Add (or replace) the \"guitar\" object on the FIRST patch of songs/{sid}.json, following 'Guitar setup' in the "
            "instructions. Use a web search to find which guitar(s) and pickups were really used on the recording and which "
            "pickup position sounds right. Edit only that file, "
            f"then run `.venv/bin/python tools/patch.py export songs/{sid}.json -o songs/{sid}.tsl` to validate. "
            "If you cannot confirm the guitar, set confidence to unknown. Reply with one short line when done.")

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
        self._job = {"state": "running", "note": "Starting Claude…", "query": kind + " setup", "kind": kind, "id": song_id}
        threading.Thread(target=self._run_band, args=(song_id, kind), daemon=True).start()
        return {"ok": True}

    def _run_band(self, sid, kind="band"):
        try:
            for attempt in range(2):          # the agent occasionally finishes without saving; try once more
                asyncio.run(self._agent({"band": self._band_prompt, "whammy": self._whammy_prompt, "guitar": self._guitar_prompt}[kind](sid)))
                self._validate(sid)
                specs = json.load(open(os.path.join(SONGS, sid + ".json")))
                specs = specs if isinstance(specs, list) else [specs]
                if specs[0].get(kind):
                    break
            else:
                raise RuntimeError((self._last_text or f"Claude could not add the {kind} setup for this song")[:300])
            self._job = {"state": "done", "id": sid, "kind": kind}
        except Exception as e:
            msg = str(e)
            if "Not logged in" in msg or "authentication" in msg.lower():
                msg = "Claude is not signed in. See the README: run the one-time login."
            self._job = {"state": "error", "error": msg, "kind": kind}

    # -------------------------------------------------------------- cover art (iTunes Search API, cached)
    def cover(self, song_id):
        """Find and cache album art for a song. Returns {"cover": relative url or ""}."""
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
            data = urllib.request.urlopen(art, timeout=10).read()
            open(dst, "wb").write(data)
            return {"cover": _cover_url(song_id)}
        except LookupError:
            open(miss, "w").close()
        except Exception:
            pass  # offline etc.: try again next launch
        return {"cover": ""}

    # -------------------------------------------------------------- song search (library + iTunes catalog)
    def search_songs(self, text, limit=5):
        """Top matches: songs already in the library first, then catalog hits (iTunes). Max `limit` items."""
        norm = lambda t: re.sub(r"[^a-z0-9 ]", "", re.sub(r"\s*[\(\[].*?[\)\]]|\s+-\s+.*$", "", t.lower())).strip()
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
                url = "https://itunes.apple.com/search?" + urllib.parse.urlencode(
                    {"term": text, "entity": "song", "limit": 25})
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

    # -------------------------------------------------------------- delete (to the macOS Trash, so it is reversible)
    def delete_song(self, song_id):
        if not re.fullmatch(r"[a-z0-9][a-z0-9\-]*", song_id or ""):
            return {"ok": False, "error": "Invalid song id"}
        files = [os.path.join(SONGS, song_id + ext) for ext in (".json", ".tsl")] + \
                [os.path.join(COVERS, song_id + ext) for ext in (".jpg", ".none")]
        files = [f for f in files if os.path.exists(f)]
        if not files:
            return {"ok": False, "error": "Song not found"}
        items = ", ".join(f'POSIX file "{f}"' for f in files)
        r = subprocess.run(["osascript", "-e", f'tell application "Finder" to delete {{{items}}}'],
                           capture_output=True, text=True)
        if r.returncode != 0:                      # Finder not allowed: keep a recoverable copy instead
            import shutil
            keep = os.path.join(ROOT, ".trash")
            os.makedirs(keep, exist_ok=True)
            for f in files:
                shutil.move(f, os.path.join(keep, os.path.basename(f)))
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
        if pickups not in ("humbucker", "single-coil", "p90", "hss", "hsh", "acoustic"):
            return {"ok": False, "error": "Unknown pickup type"}
        items = self.guitars()
        gid = guitar.get("id") or re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "guitar"
        entry = {"id": gid, "name": name[:60], "brand": (guitar.get("brand") or "").strip()[:40],
                 "kind": (guitar.get("kind") or "")[:40], "pickups": pickups,
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
        subprocess.run(["open", "-R", path])
        return {"ok": True, "path": path, "count": len(patches)}
