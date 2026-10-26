"""Alternative AI provider for TonenAI: any OpenAI-compatible server (Ollama, LM Studio, OpenAI, OpenRouter, ...).

Claude (the Claude Agent SDK) stays the default. Pick another provider in the app (header > AI) or in data/settings.json:

    {"provider": "openai",
     "openai": {"preset": "ollama", "base_url": "http://localhost:11434/v1", "model": "qwen2.5:14b-instruct",
                "api_key": "", "search": "duckduckgo"}}

This is not an agent loop: one prompt in, JSON out, validated against the Katana parameter tables and retried with the
error text. "search" is "duckduckgo" (best-effort snippets for research notes) or "none".
Env overrides: TONENAI_PROVIDER (claude | openai) and TONENAI_API_KEY.
"""
import html, http.client, json, os, re, socket, threading, unicodedata, urllib.error, urllib.parse, urllib.request

DEFAULTS = {"preset": "ollama", "base_url": "http://localhost:11434/v1", "model": "qwen2.5:14b-instruct", "api_key": "",
            "timeout": 900, "search": "duckduckgo", "retries": 3, "num_ctx": 12288}
PRESETS = [
    {"id": "ollama", "name": "Ollama (this computer)", "base_url": "http://localhost:11434/v1", "model": "qwen2.5:14b-instruct", "key": False},
    {"id": "lmstudio", "name": "LM Studio (this computer)", "base_url": "http://localhost:1234/v1", "model": "", "key": False},
    {"id": "openai", "name": "OpenAI", "base_url": "https://api.openai.com/v1", "model": "", "key": True},
    {"id": "openrouter", "name": "OpenRouter", "base_url": "https://openrouter.ai/api/v1", "model": "", "key": True},
    {"id": "custom", "name": "Custom server", "base_url": "", "model": "", "key": False},
]
GENRES = ["Blues", "R&B", "Rock", "Alternative Rock", "Metal", "Pop", "Electronic Pop", "Hip-Hop", "Country", "Folk",
          "Latin", "Jazz"]
PICKUPS = {"humbucker", "single-coil", "p90", "any"}
CONFIDENCE = {"confirmed", "likely", "unknown"}
WHAMMY_EFFECTS = {"2 OCT UP", "OCT UP", "5TH UP", "4TH UP", "2ND UP", "4TH DOWN", "5TH DOWN", "OCT DOWN", "2 OCT DOWN",
                  "DIVE BOMB"}
SECTION_MARKERS = {"band": ("## Band setup", "## Whammy DT setup"), "whammy": ("## Whammy DT setup", "## Guitar setup"),
                   "guitar": ("## Guitar setup", None)}
SEARCH_TOPIC = {"band": "tempo BPM key song structure", "whammy": "guitar tuning pitch effect whammy octave",
                "guitar": "guitar pickups used on the recording"}


def config(settings_path):
    """Settings for the OpenAI-compatible provider, or None when Claude is selected (the default)."""
    try:
        cfg = json.load(open(settings_path))
    except (OSError, ValueError):
        cfg = {}
    cfg = cfg if isinstance(cfg, dict) else {}
    if (os.environ.get("TONENAI_PROVIDER") or cfg.get("provider") or "claude") != "openai":
        return None
    saved = cfg.get("openai") if isinstance(cfg.get("openai"), dict) else {}
    out = {**DEFAULTS, **saved}
    if "preset" not in saved:
        out["preset"] = "custom"
    if os.environ.get("TONENAI_API_KEY"):
        out["api_key"] = os.environ["TONENAI_API_KEY"]
    return out


# ------------------------------------------------------------------ model + web access

def _call(cfg, path, body=None):
    headers = {"Content-Type": "application/json"}
    if cfg.get("api_key"):
        headers["Authorization"] = "Bearer " + cfg["api_key"]
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(cfg["base_url"].rstrip("/") + path, data, headers)
    with urllib.request.urlopen(req, timeout=cfg["timeout"]) as r:
        return json.load(r)


def _explain(cfg, e):
    if isinstance(e, urllib.error.HTTPError):
        detail = e.read()[:200].decode("utf8", "replace")
        if e.code in (401, 403):
            return RuntimeError(f"The AI server rejected the API key ({e.code}). Check it under AI settings.")
        return RuntimeError(f"AI server error {e.code}: {detail}")
    return RuntimeError(f"Can't reach the AI server at {cfg['base_url']} ({e}). Is it running, and is the model available?")


class _Rejected(Exception):
    def __init__(self, code, body):
        super().__init__(code)
        self.code, self.body = code, body


def _stream(cfg, body, on_progress=None, cancel=None, native=False):
    """POST a streaming chat completion. Reports progress while tokens arrive; `cancel` (threading.Event) aborts at any time,
    even before the first token (a watcher thread closes the socket)."""
    base = cfg["base_url"].rstrip("/")
    u = urllib.parse.urlparse(re.sub(r"/v1$", "", base) + "/api/chat" if native else base + "/chat/completions")
    conn = (http.client.HTTPSConnection if u.scheme == "https" else http.client.HTTPConnection)(u.netloc, timeout=cfg["timeout"])
    headers = {"Content-Type": "application/json"}
    if cfg.get("api_key"):
        headers["Authorization"] = "Bearer " + cfg["api_key"]
    done = threading.Event()
    if cancel is not None:
        def watch():
            while not done.is_set():
                if cancel.wait(0.3):
                    try:                                    # shutdown first: close() alone does not wake a thread blocked in recv()
                        conn.sock.shutdown(socket.SHUT_RDWR)
                    except (OSError, AttributeError):
                        pass
                    return
        threading.Thread(target=watch, daemon=True).start()
    try:
        try:
            conn.request("POST", u.path + (("?" + u.query) if u.query else ""), json.dumps({**body, "stream": True}), headers)
            resp = conn.getresponse()
            if resp.status != 200:
                raise _Rejected(resp.status, resp.read(400).decode("utf8", "replace"))
            if not native and "event-stream" not in (resp.getheader("Content-Type") or ""):       # a server that ignores stream=true
                data = json.loads(resp.read())
                return data["choices"][0]["message"].get("content") or ""
            text, n = [], 0
            for raw in resp:
                if cancel is not None and cancel.is_set():
                    raise RuntimeError("Cancelled")
                line = raw.decode("utf8", "replace").strip()
                finished = False
                if native:                                      # Ollama: one JSON object per line
                    if not line:
                        continue
                    try:
                        obj = json.loads(line)
                    except ValueError:
                        continue
                    if obj.get("error"):
                        raise RuntimeError(str(obj["error"])[:300])
                    msg = obj.get("message") or {}
                    piece, thought, finished = msg.get("content") or "", msg.get("thinking") or "", bool(obj.get("done"))
                else:                                           # OpenAI style server-sent events
                    if not line.startswith("data:"):
                        continue
                    payload = line[5:].strip()
                    if payload == "[DONE]":
                        break
                    try:
                        delta = json.loads(payload)["choices"][0].get("delta") or {}
                    except (ValueError, KeyError, IndexError):
                        continue
                    piece = delta.get("content") or ""
                    thought = delta.get("reasoning") or delta.get("reasoning_content") or ""
                if piece:
                    text.append(piece)
                if piece or thought:
                    n += 1
                    if on_progress and n % 4 == 0:
                        on_progress(n, bool(thought and not piece))
                if finished:
                    break
            if cancel is not None and cancel.is_set():         # a cut stream ends quietly: never return a half answer
                raise RuntimeError("Cancelled")
            if on_progress:
                on_progress(n, False)
            return "".join(text)
        except (OSError, http.client.HTTPException, ValueError) as e:
            if cancel is not None and cancel.is_set():
                raise RuntimeError("Cancelled")
            raise
    finally:
        done.set()
        conn.close()


def chat(cfg, messages, json_mode=True, on_progress=None, cancel=None):
    native = cfg.get("preset") == "ollama"
    if native:
        # Ollama's /v1 endpoint cannot raise the context window (default is small: our prompt would be cut silently) nor switch
        # off hidden "thinking", so talk to its native API instead.
        body = {"model": cfg["model"], "messages": messages, "think": False,
                "options": {"temperature": 0.3, "num_ctx": int(cfg.get("num_ctx") or 12288)}}
        if json_mode:
            body["format"] = "json"
        variants = [(), ("think",), ("think", "format")]
    else:
        body = {"model": cfg["model"], "messages": messages, "temperature": 0.3}
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        # some servers reject optional fields (LM Studio: response_format json_object; reasoning models: temperature)
        variants = [(), ("response_format",), ("temperature",), ("response_format", "temperature")]
    try:
        for drop in variants:
            try:
                text = _stream(cfg, {k: v for k, v in body.items() if k not in drop}, on_progress, cancel, native)
                break
            except _Rejected as e:
                if native and e.code == 404:
                    raise RuntimeError(f"Ollama does not have the model {cfg['model']!r}. Install it with: ollama pull {cfg['model']}")
                if e.code not in (400, 422) or drop == variants[-1]:
                    raise urllib.error.HTTPError(cfg["base_url"], e.code, e.body, {}, None)
    except urllib.error.HTTPError as e:
        raise RuntimeError("The AI server rejected the API key (%d). Check it under AI settings." % e.code if e.code in (401, 403)
                           else "AI server error %d: %s" % (e.code, (e.msg or "")[:200]))
    except (OSError, http.client.HTTPException) as e:
        raise _explain(cfg, e)
    return re.sub(r"<think>.*?</think>", "", text, flags=re.S).strip()      # models that think inline


def list_models(cfg):
    """Model ids the server offers (GET /models), to test the connection and fill the model picker."""
    try:
        data = _call({**cfg, "timeout": 15}, "/models")
    except OSError as e:
        raise _explain(cfg, e)
    items = data.get("data") if isinstance(data, dict) else data
    return sorted({str(m.get("id") or m.get("name")) for m in (items or []) if isinstance(m, dict)})[:300]


def extract_json(text):
    text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip()).strip()
    try:
        return json.loads(text)
    except ValueError:
        pass
    for o, c in (("{", "}"), ("[", "]")):
        i, j = text.find(o), text.rfind(c)
        if 0 <= i < j:
            try:
                return json.loads(text[i:j + 1])
            except ValueError:
                continue
    raise ValueError("The reply was not valid JSON")


def web_notes(cfg, query, n=6):
    """Best-effort research snippets (titles + summaries, no page fetching). Empty string if offline or blocked."""
    if cfg.get("search") != "duckduckgo":
        return ""
    try:
        req = urllib.request.Request("https://html.duckduckgo.com/html/", urllib.parse.urlencode({"q": query}).encode(),
                                     {"User-Agent": "Mozilla/5.0 (TonenAI)"})
        page = urllib.request.urlopen(req, timeout=12).read().decode("utf8", "replace")
    except Exception:
        return ""
    clean = lambda s: html.unescape(re.sub(r"<[^>]+>", "", s)).strip()
    titles = re.findall(r'class="result__a"[^>]*>(.*?)</a>', page, flags=re.S)
    snips = re.findall(r'class="result__snippet"[^>]*>(.*?)</a>', page, flags=re.S)
    return "\n".join(f"- {clean(t)}: {clean(s)}" for t, s in list(zip(titles, snips))[:n])


# ------------------------------------------------------------------ prompt pieces + validation

def _between(text, start, end=None):
    i = text.find(start)
    if i < 0:
        return ""
    j = text.find(end, i) if end else -1
    return text[i:j if j > 0 else len(text)]


def _options(tables):
    return "\n".join(f"- {k} types: {', '.join(sorted(tables[k]))}" for k in ("amp", "booster", "mod", "delay", "reverb")
                     if k in tables)


def _is_num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def check_section(kind, obj):
    if not isinstance(obj, dict):
        return [f'"{kind}" must be a JSON object']
    p = []
    if kind == "band":
        if obj.get("genre") not in GENRES:
            p.append(f'band.genre must be one of {GENRES}')
        if not _is_num(obj.get("bpm")) or not 30 <= obj["bpm"] <= 300:
            p.append("band.bpm must be a number between 30 and 300")
        if not isinstance(obj.get("parts"), list) or not 1 <= len(obj["parts"]) <= 5:
            p.append("band.parts must be a list of 1-5 strings")
    elif kind == "whammy":
        if obj.get("use") not in ("yes", "optional", "no"):
            p.append('whammy.use must be "yes", "optional" or "no"')
        st = obj.get("settings") if isinstance(obj.get("settings"), dict) else {}
        if st.get("effect") not in (None, *WHAMMY_EFFECTS):
            p.append(f"whammy.settings.effect must be null or one of {sorted(WHAMMY_EFFECTS)}")
        for key in ("transpose_down", "transpose_up"):
            v = st.get(key)
            if v is not None and not (isinstance(v, int) and not isinstance(v, bool) and 1 <= v <= 7) \
                    and v not in ("OCT", "OCT DRY"):
                p.append(f'whammy.settings.{key} must be null, an integer 1-7, "OCT" or "OCT DRY"')
        if st.get("detune") not in (None, "SHALLOW", "DEEP"):
            p.append('whammy.settings.detune must be null, "SHALLOW" or "DEEP"')
        if not isinstance(obj.get("how"), list):
            p.append("whammy.how must be a list of steps")
    elif kind == "guitar":
        orig = obj.get("originals")
        if not isinstance(orig, list) or not 1 <= len(orig) <= 3 or not all(isinstance(o, dict) and o.get("guitar")
                                                                             for o in orig):
            p.append('guitar.originals must be 1-3 objects, each with a "guitar" name')
        else:
            for o in orig:
                if o.get("pickups") not in PICKUPS:
                    p.append(f"guitar.originals pickups must be one of {sorted(PICKUPS)}")
        if obj.get("pickups_needed") not in PICKUPS:
            p.append(f"guitar.pickups_needed must be one of {sorted(PICKUPS)}")
        if obj.get("confidence") not in CONFIDENCE:
            p.append("guitar.confidence must be confirmed, likely or unknown")
    return p


def check_patches(specs):
    if not isinstance(specs, list) or not 1 <= len(specs) <= 3 or not all(isinstance(s, dict) for s in specs):
        return ['"patches" must be a list of 1-3 patch objects']
    p = []
    first = specs[0]
    if " - " not in str(first.get("memo", "")).split(":")[0]:
        p.append("the first patch memo must start with 'Artist - Title: '")
    for s in specs:
        if len(str(s.get("name", ""))) > 16:
            p.append(f"patch name {s.get('name')!r} is longer than 16 characters")
    for kind in ("band", "whammy", "guitar"):
        p += check_section(kind, first.get(kind))
    for s in specs:                                 # a value past a byte's range would corrupt the SysEx / .tsl data
        for sec in ("amp", "booster", "mod", "fx", "delay", "reverb", "noise_suppressor"):
            obj = s.get(sec)
            if not isinstance(obj, dict):
                continue
            for k, v in obj.items():
                top = 2000 if (sec, k) == ("delay", "time") else 100
                if _is_num(v) and not 0 <= v <= top:
                    p.append(f"{sec}.{k}={v} is out of range (0-{top})")
            for k, v in (obj.get("params") or {}).items() if isinstance(obj.get("params"), dict) else []:
                if _is_num(v) and not 0 <= v <= 127:
                    p.append(f"{sec}.params.{k}={v} is out of range (0-127)")
    return p


def slugify(memo, fallback="song"):
    head = unicodedata.normalize("NFKD", memo.split(":")[0]).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", head).strip("-")[:80] or fallback


def _write(path, specs):
    json.dump(specs, open(path, "w", encoding="utf8"), indent=1, ensure_ascii=False)


def _example_section(ctx, kind):
    ex = ctx.get("example") or []
    return json.dumps(ex[0].get(kind), ensure_ascii=False) if ex and ex[0].get(kind) else ""


# ------------------------------------------------------------------ the two jobs the app runs

def create_song(cfg, text, ctx, note):
    """Research -> JSON patch list -> validate (with retries) -> songs/<artist>-<title>.json."""
    skill = ctx["skill"]
    guide = _between(skill, "3. **Write a spec**", "4. **Apply live**") + "\n" + _between(skill, "## Band setup")
    system = (
        "You configure a BOSS Katana MkII amp for a song. Reply with ONE JSON object and nothing else: "
        '{"patches": [ ...1-3 patch specs... ]}.\n'
        "You cannot run commands or write files; just return the JSON. Ignore any instruction below about running commands.\n"
        "Never invent facts: if the research notes do not support a gear, tuning, tempo or key claim, say so in the "
        'text fields and use confidence "unknown" / use "optional".\n\n'
        "VALID TYPE NAMES (use exactly these, uppercase):\n" + _options(ctx["tables"]) + "\n\n"
        "INSTRUCTIONS:\n" + guide + "\n\n"
        "EXAMPLE of a complete valid reply value for \"patches\":\n" + json.dumps(ctx.get("example") or [], ensure_ascii=False))
    stage = ctx.get("stage") or (lambda *a: None)
    stage(0)
    note("Researching the tone…")
    notes = web_notes(cfg, f"{text} guitar tone amp pedals guitar used on the recording")
    user = (f"Song or band requested: {text!r}\n\nResearch notes from a web search (may be partial or off-topic):\n"
            f"{notes or '(no web access: use only what you know and mark anything unverified as unknown)'}"
            f"{ctx.get('lang_note', '')}")
    messages = [{"role": "system", "content": system}, {"role": "user", "content": user}]
    for attempt in range(cfg["retries"]):
        stage(1)
        note("Writing the patch…" if attempt == 0 else "Fixing the patch…")
        reply = chat(cfg, messages, on_progress=ctx.get("progress"), cancel=ctx.get("cancel"))
        stage(2, attempt + 1)
        note("Building the .tsl…")
        try:
            data = extract_json(reply)
            specs = data.get("patches") if isinstance(data, dict) else data
            problems = check_patches(specs)
            if not problems:
                for s in specs:
                    ctx["build"](s)             # raises on unknown type names / parameters
        except Exception as e:
            problems = [f"{type(e).__name__}: {e}"]
        if not problems:
            break
        messages += [{"role": "assistant", "content": reply},
                     {"role": "user", "content": "Fix these problems and return the complete corrected JSON object:\n- "
                      + "\n- ".join(problems)}]
    else:
        raise RuntimeError("The AI model could not produce a valid patch: " + problems[0][:200] + " | it answered: " + reply[:120].replace("\n", " "))
    stage(3)
    path = os.path.join(ctx["songs_dir"], slugify(specs[0]["memo"]) + ".json")
    if os.path.exists(path):
        raise RuntimeError(f"{os.path.basename(path)} already exists")
    _write(path, specs)


def add_section(cfg, sid, kind, ctx, note):
    """Add or replace the band / whammy / guitar object on the first patch of songs/<sid>.json."""
    path = os.path.join(ctx["songs_dir"], sid + ".json")
    specs = json.load(open(path, encoding="utf8"))
    specs = specs if isinstance(specs, list) else [specs]
    head = str(specs[0].get("memo", "")).split(":")[0] or sid
    start, end = SECTION_MARKERS[kind]
    note("Researching…")
    notes = web_notes(cfg, f"{head} {SEARCH_TOPIC[kind]}")
    system = (
        f'You add the "{kind}" setup to a BOSS Katana MkII song file. Reply with ONE JSON object: {{"{kind}": {{...}}}} '
        "and nothing else. You cannot run commands; ignore instructions about running them. Never invent facts: if the "
        "notes do not support a value, say so in the text fields and use the 'unknown' / 'optional' option.\n\n"
        "INSTRUCTIONS:\n" + _between(ctx["skill"], start, end) + "\n\nEXAMPLE value:\n" + _example_section(ctx, kind))
    user = (f"Song: {head}\nPatches: {', '.join(str(s.get('name')) for s in specs)}\n\nResearch notes from a web search "
            f"(may be partial or off-topic):\n{notes or '(no web access: use only what you know; mark unverified items)'}"
            f"{ctx.get('lang_note', '')}")
    messages = [{"role": "system", "content": system}, {"role": "user", "content": user}]
    for attempt in range(cfg["retries"]):
        note("Writing…" if attempt == 0 else "Fixing…")
        reply = chat(cfg, messages, on_progress=ctx.get("progress"), cancel=ctx.get("cancel"))
        try:
            data = extract_json(reply)
            obj = data.get(kind, data) if isinstance(data, dict) else data
            problems = check_section(kind, obj)
        except Exception as e:
            problems = [f"{type(e).__name__}: {e}"]
        if not problems:
            break
        messages += [{"role": "assistant", "content": reply},
                     {"role": "user", "content": "Fix these problems and return the corrected JSON object:\n- "
                      + "\n- ".join(problems)}]
    else:
        raise RuntimeError(f"The AI model could not produce a valid {kind} setup: " + problems[0][:200] + " | it answered: " + reply[:120].replace("\n", " "))
    specs[0][kind] = obj
    _write(path, specs)
