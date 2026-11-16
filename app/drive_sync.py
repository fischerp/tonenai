"""Google Drive sync for the song library (optional).

Your songs (songs/*.json and songs/*.tsl) are mirrored, one Drive file per local file, in the hidden
*appDataFolder* of your own Google Drive (scope ``drive.appdata``): TonenAI can neither see nor change anything
else in your Drive. The desktop app and the Android app use the same folder, so a song created on one shows up
on the other. Nothing is shared with anyone: every user connects their own Google Cloud client
(docs/google-drive-sync.md).

Rules
- A file that changed on one side since the last sync is copied to the other side.
- A file deleted on one side since the last sync is deleted on the other (locally it goes to the Trash).
- A file that changed on both sides: the newer copy wins and the older one is kept in data/backups/sync-conflicts/.
- A side that lost its whole library at once never deletes the other: a new device downloads, an emptied Drive is filled again.
- Secrets (the OAuth refresh token) live in the macOS Keychain, elsewhere in a private file outside the project.
"""
from __future__ import annotations

import base64, hashlib, json, os, platform, re, secrets, subprocess, threading, time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen

import phone_requests

SCOPE = "https://www.googleapis.com/auth/drive.appdata"
AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URL = "https://oauth2.googleapis.com/token"
REVOKE_URL = "https://oauth2.googleapis.com/revoke"
FILES_URL = "https://www.googleapis.com/drive/v3/files"
UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
SONG_FILE = re.compile(r"[a-z0-9][a-z0-9\-]*\.(json|tsl)")     # the only names that are ever read or written
# Your own lists and notes live in data/, not in songs/; each is one Drive file. "list" files hold a JSON list of objects, "dict" files an object.
DATA_FILES = {"livesets.json": list, "guitars.json": list, "song_meta.json": dict, "tabs.json": dict, "rig.json": dict}
MAX_BYTES = 2 * 1024 * 1024                                     # a song file is tens of kilobytes
KEYCHAIN_SERVICE = "tonenai.google-drive"
REFRESH_TOKEN = "refresh_token"
HTTP_TIMEOUT = 30


class SyncError(Exception):
    """A problem the user can read and act on."""


# ------------------------------------------------------------------------------------------- secrets
def _kc_encode(value):
    return "b64:" + base64.b64encode(value.encode("utf-8")).decode("ascii")   # keeps quotes out of the security command


def _kc_decode(stored):
    return base64.b64decode(stored[4:]).decode("utf-8") if stored.startswith("b64:") else stored


class SecretStore:
    """macOS Keychain via the ``security`` tool; a private file in the home folder elsewhere (and in tests)."""

    def __init__(self, fallback_path=None):
        self.use_keychain = platform.system() == "Darwin" and fallback_path is None
        self.fallback_path = Path(fallback_path) if fallback_path else Path.home() / ".tonenai" / "secrets.json"

    def _read_file(self):
        try:
            return json.loads(self.fallback_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return {}

    def _write_file(self, data):
        self.fallback_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temp = self.fallback_path.with_suffix(".tmp")
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(data, handle)
        temp.replace(self.fallback_path)

    def get(self, name):
        if not self.use_keychain:
            return self._read_file().get(name)
        r = subprocess.run(["security", "find-generic-password", "-s", KEYCHAIN_SERVICE, "-a", name, "-w"],
                           capture_output=True, text=True)
        return _kc_decode(r.stdout.strip()) if r.returncode == 0 else None

    def set(self, name, value):
        if not self.use_keychain:
            data = self._read_file()
            data[name] = value
            return self._write_file(data)
        # interactive mode reads the command from stdin, so the secret never shows in the process list
        command = f'add-generic-password -U -s "{KEYCHAIN_SERVICE}" -a "{name}" -w "{_kc_encode(value)}"\n'
        if subprocess.run(["security", "-i"], input=command, capture_output=True, text=True).returncode != 0:
            raise SyncError("Could not save the Drive sign-in in the macOS Keychain.")

    def delete(self, name):
        if not self.use_keychain:
            data = self._read_file()
            data.pop(name, None)
            return self._write_file(data)
        subprocess.run(["security", "delete-generic-password", "-s", KEYCHAIN_SERVICE, "-a", name], capture_output=True)


# ------------------------------------------------------------------------------------------- http
def _http(method, url, data=None, headers=None):
    try:
        with urlopen(Request(url, data=data, method=method, headers=headers or {}), timeout=HTTP_TIMEOUT) as response:
            return response.status, response.read()
    except HTTPError as error:
        return error.code, error.read()
    except (URLError, OSError) as error:
        raise SyncError(f"Could not reach Google: {getattr(error, 'reason', error)}") from error


def _json_or_error(status, body, action):
    try:
        payload = json.loads(body.decode("utf-8")) if body else {}
    except ValueError:
        payload = {}
    if status >= 400:
        error = payload.get("error")
        detail = payload.get("error_description") or (error.get("message") if isinstance(error, dict) else error)
        raise SyncError(f"{action} failed ({status}){': ' + str(detail) if detail else ''}.")
    return payload


def load_client_config(path):
    """Reads the OAuth client JSON downloaded from Google Cloud (type: Desktop app)."""
    try:
        raw = json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise SyncError(f"Save the Google OAuth client file as data/{Path(path).name} first (see docs/google-drive-sync.md).") from error
    except (OSError, ValueError) as error:
        raise SyncError(f"{Path(path).name} is not a valid Google OAuth client file.") from error
    section = raw.get("installed") if isinstance(raw, dict) else None
    if not isinstance(section, dict) or not section.get("client_id"):
        raise SyncError("The OAuth client must be of type 'Desktop app' (its JSON has an 'installed' section).")
    return {"client_id": section["client_id"], "client_secret": section.get("client_secret", "")}


def _b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _md5(data):
    return hashlib.md5(data).hexdigest()


def _epoch(rfc3339):
    try:
        return datetime.fromisoformat((rfc3339 or "").replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0.0


def plan(local, remote, base):
    """What to do with each file. `local`, `remote`, `base` map name -> md5 (base = both sides at the last sync).

    Returns {name: action}; actions: upload, download, delete_local, delete_remote, conflict. Files that already
    match are left out.
    """
    # A side that lost everything at once is treated as new, not as "the user deleted every song":
    # Drive emptied or another account -> fill it again; a fresh folder on this device -> download.
    if len(base) >= 3 and (not remote or not local):
        base = {}
    actions = {}
    for name in sorted(set(local) | set(remote)):
        here, there, was = local.get(name), remote.get(name), base.get(name)
        if here == there:
            continue
        if there is None:
            actions[name] = "delete_local" if here == was else "upload"
        elif here is None:
            actions[name] = "delete_remote" if there == was else "download"
        elif here == was:
            actions[name] = "download"
        elif there == was:
            actions[name] = "upload"
        else:
            actions[name] = "conflict"
    return actions


class DriveSync:
    def __init__(self, songs_dir, data_dir, secret_store=None, http=None, trash=None, open_browser=None):
        self.songs_dir, self.data_dir = Path(songs_dir), Path(data_dir)
        self.client_path = self.data_dir / "google_oauth_client.json"
        self.state_path = self.data_dir / "drive_state.json"
        self.conflicts_dir = self.data_dir / "backups" / "sync-conflicts"
        self.secrets = secret_store or SecretStore()
        self.http = http or _http
        self.trash = trash or self._trash
        self.open_browser = open_browser
        self.lock = threading.RLock()
        self.busy = ""                      # "connect" or "sync" while one runs in the background
        self.revision = 0                   # goes up whenever a sync changed local files, so the UI can reload
        self._access_token, self._access_expiry = None, 0.0

    # ---- state
    def _state(self):
        try:
            state = json.loads(self.state_path.read_text(encoding="utf-8"))
            return state if isinstance(state, dict) else {}
        except (OSError, ValueError):
            return {}

    def _save_state(self, state):
        self.data_dir.mkdir(parents=True, exist_ok=True)
        temp = self.state_path.with_suffix(".tmp")
        temp.write_text(json.dumps(state, indent=1) + "\n", encoding="utf-8")
        temp.replace(self.state_path)

    def _update_state(self, **changes):
        state = self._state()
        state.update(changes)
        self._save_state(state)

    def connected(self):
        return bool(self.secrets.get(REFRESH_TOKEN))

    def status(self):
        state = self._state()
        return {"client_configured": self.client_path.exists(), "connected": self.connected(), "busy": self.busy,
                "last_synced_at": state.get("last_synced_at"), "last_result": state.get("last_result"),
                "last_error": state.get("last_error"), "files": len(state.get("files") or {}), "revision": self.revision}

    # ---- OAuth (installed-app flow with PKCE; Google redirects to a one-shot server on this computer)
    def connect(self, timeout=300):
        """Opens the Google sign-in in the browser and waits for it. Blocks: call it from a worker thread."""
        client = load_client_config(self.client_path)
        verifier = _b64url(secrets.token_bytes(48))
        state_token = secrets.token_urlsafe(24)
        result = {}

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                query = parse_qs(urlparse(self.path).query)
                ok = secrets.compare_digest(query.get("state", [""])[0], state_token)
                if ok:
                    result.update(code=query.get("code", [""])[0], error=query.get("error", [""])[0])
                done = ok and bool(result.get("code"))
                title, text = (("Connected to Google Drive", "Your songs will now stay in sync across your devices. You can close this tab and go back to TonenAI.") if done
                               else ("Sign-in not completed", "Go back to TonenAI and press Connect again."))
                mark = "#3ddc84" if done else "#e11d2e"
                glyph = "&#10003;" if done else "&#33;"
                body = (f"<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>TonenAI</title>"
                        f"<style>*{{box-sizing:border-box}}body{{margin:0;min-height:100vh;display:grid;place-items:center;background:radial-gradient(ellipse at 50% 0,#2a0b10 0,#0b0b0f 60%);"
                        f"color:#f2f2f5;font:16px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif}}"
                        f".card{{width:min(440px,calc(100vw - 32px));padding:40px 36px;text-align:center;background:#16161d;border:1px solid #ffffff14;border-radius:20px;box-shadow:0 24px 60px #0008}}"
                        f".brand{{font-weight:800;letter-spacing:.18em;font-size:13px;margin-bottom:28px}}.brand b{{color:#e11d2e}}"
                        f".mark{{width:64px;height:64px;margin:0 auto 20px;border-radius:50%;display:grid;place-items:center;font-size:30px;font-weight:700;color:{mark};background:{mark}1f;border:2px solid {mark}}}"
                        f"h1{{margin:0 0 8px;font-size:22px}}p{{margin:0;color:#a6a6b3}}</style>"
                        f"<div class=card><div class=brand>TONEN<b>AI</b></div><div class=mark>{glyph}</div><h1>{title}</h1><p>{text}</p></div>").encode()
                self.send_response(200 if ok else 400)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        server.timeout = 1
        redirect_uri = f"http://127.0.0.1:{server.server_address[1]}/"
        url = AUTH_URL + "?" + urlencode({
            "client_id": client["client_id"], "redirect_uri": redirect_uri, "response_type": "code", "scope": SCOPE,
            "code_challenge": _b64url(hashlib.sha256(verifier.encode("ascii")).digest()), "code_challenge_method": "S256",
            "state": state_token, "access_type": "offline", "prompt": "consent"})
        try:
            (self.open_browser or __import__("webbrowser").open)(url)
            deadline = time.time() + timeout
            while not result and time.time() < deadline:
                server.handle_request()
        finally:
            server.server_close()
        if not result:
            raise SyncError("The Google sign-in was not finished in time. Try Connect again.")
        if result.get("error") or not result.get("code"):
            raise SyncError("Google sign-in was cancelled." if result.get("error") == "access_denied"
                            else f"Google sign-in failed: {result.get('error') or 'no code'}")
        status, body = self.http("POST", TOKEN_URL, urlencode({
            "code": result["code"], "client_id": client["client_id"], "client_secret": client["client_secret"],
            "redirect_uri": redirect_uri, "grant_type": "authorization_code", "code_verifier": verifier,
        }).encode("ascii"), {"Content-Type": "application/x-www-form-urlencoded"})
        payload = _json_or_error(status, body, "Google sign-in")
        if SCOPE not in str(payload.get("scope", "")).split():
            raise SyncError("Google did not grant access to the app's Drive folder.")
        if not payload.get("refresh_token"):
            raise SyncError("Google did not return a refresh token. Remove TonenAI from your Google account's third-party access and connect again.")
        self.secrets.set(REFRESH_TOKEN, payload["refresh_token"])
        self._access_token = payload.get("access_token")
        self._access_expiry = time.time() + int(payload.get("expires_in", 0)) - 60
        self._update_state(last_error=None)

    def _token(self):
        if self._access_token and time.time() < self._access_expiry:
            return self._access_token
        refresh_token = self.secrets.get(REFRESH_TOKEN)
        if not refresh_token:
            raise SyncError("Connect Google Drive first.")
        client = load_client_config(self.client_path)
        status, body = self.http("POST", TOKEN_URL, urlencode({
            "client_id": client["client_id"], "client_secret": client["client_secret"],
            "refresh_token": refresh_token, "grant_type": "refresh_token",
        }).encode("ascii"), {"Content-Type": "application/x-www-form-urlencoded"})
        if status == 400 and b"invalid_grant" in body:
            self.secrets.delete(REFRESH_TOKEN)
            raise SyncError("Google access expired or was revoked. Connect Google Drive again.")
        payload = _json_or_error(status, body, "Refreshing Google access")
        self._access_token = payload["access_token"]
        self._access_expiry = time.time() + int(payload.get("expires_in", 0)) - 60
        return self._access_token

    def disconnect(self):
        """Signs this computer out. The songs stay here and in Drive."""
        with self.lock:
            refresh_token = self.secrets.get(REFRESH_TOKEN)
            if refresh_token:
                try:
                    self.http("POST", REVOKE_URL, urlencode({"token": refresh_token}).encode("ascii"),
                              {"Content-Type": "application/x-www-form-urlencoded"})
                except SyncError:
                    pass
            self.secrets.delete(REFRESH_TOKEN)
            self._access_token = None
            self._save_state({})

    # ---- Drive files
    def _headers(self, extra=None):
        return {"Authorization": f"Bearer {self._token()}", **(extra or {})}

    def _list_remote(self):
        """name -> {id, md5Checksum, modifiedTime}; when a name exists twice the newest wins and the rest are removed."""
        found, token = {}, None
        while True:
            query = {"spaces": "appDataFolder", "q": "trashed = false", "pageSize": "1000",
                     "fields": "nextPageToken,files(id,name,md5Checksum,modifiedTime,size)", "orderBy": "modifiedTime desc"}
            if token:
                query["pageToken"] = token
            status, body = self.http("GET", f"{FILES_URL}?{urlencode(query)}", None, self._headers())
            payload = _json_or_error(status, body, "Reading Google Drive")
            for f in payload.get("files", []):
                name = f.get("name", "")
                if not (name in DATA_FILES or SONG_FILE.fullmatch(name)) or int(f.get("size") or 0) > MAX_BYTES:
                    continue
                if phone_requests.REQUEST_FILE.fullmatch(name) and int(f.get("size") or 0) > phone_requests.MAX_BYTES:
                    continue
                if name in found:
                    self._delete_remote(f["id"])
                else:
                    found[name] = f
            token = payload.get("nextPageToken")
            if not token:
                return found

    def _download(self, file_id):
        status, body = self.http("GET", f"{FILES_URL}/{file_id}?alt=media", None, self._headers())
        if status >= 400:
            _json_or_error(status, body, "Downloading a song")
        if len(body) > MAX_BYTES:
            raise SyncError("A file in Google Drive is too large to be a song, so it was not used.")
        return body

    def _upload(self, name, content, file_id=None):
        if file_id:
            status, body = self.http("PATCH", f"{UPLOAD_URL}/{file_id}?uploadType=media&fields=id,md5Checksum", content,
                                     self._headers({"Content-Type": "application/json"}))
            return _json_or_error(status, body, "Uploading to Google Drive")
        boundary = "tonenai-" + secrets.token_hex(12)
        metadata = json.dumps({"name": name, "parents": ["appDataFolder"], "mimeType": "application/json"})
        multipart = (f"--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{metadata}\r\n"
                     f"--{boundary}\r\nContent-Type: application/json\r\n\r\n").encode("utf-8") + content + f"\r\n--{boundary}--\r\n".encode("utf-8")
        status, body = self.http("POST", f"{UPLOAD_URL}?uploadType=multipart&fields=id,md5Checksum", multipart,
                                 self._headers({"Content-Type": f"multipart/related; boundary={boundary}"}))
        return _json_or_error(status, body, "Uploading to Google Drive")

    def _delete_remote(self, file_id):
        status, body = self.http("DELETE", f"{FILES_URL}/{file_id}", None, self._headers())
        if status >= 400 and status != 404:
            _json_or_error(status, body, "Deleting a song in Google Drive")

    # ---- local files
    def _path(self, name):
        if phone_requests.REQUEST_FILE.fullmatch(name):          # song requests from the phone, and their results
            return self.data_dir / "requests" / name
        return self.data_dir / name if name in DATA_FILES else self.songs_dir / name

    def _local(self):
        files = {}
        if self.songs_dir.is_dir():
            for path in self.songs_dir.iterdir():
                if (path.is_file() and path.name not in DATA_FILES and not phone_requests.REQUEST_FILE.fullmatch(path.name)
                        and SONG_FILE.fullmatch(path.name) and path.stat().st_size <= MAX_BYTES):
                    files[path.name] = _md5(path.read_bytes())
        for name in DATA_FILES:
            path = self._path(name)
            if path.is_file() and path.stat().st_size <= MAX_BYTES:
                files[name] = _md5(path.read_bytes())
        requests = self.data_dir / "requests"
        if requests.is_dir():
            for path in requests.iterdir():
                if path.is_file() and phone_requests.REQUEST_FILE.fullmatch(path.name) and path.stat().st_size <= phone_requests.MAX_BYTES:
                    files[path.name] = _md5(path.read_bytes())
        return files

    def _write_local(self, name, content):
        data = json.loads(content.decode("utf-8"))      # both .json and .tsl are JSON: never store anything else
        kind = DATA_FILES.get(name)
        if kind and not (isinstance(data, kind) and (kind is dict or all(isinstance(x, dict) for x in data))):
            raise SyncError(f"{name} in Google Drive is not in the expected format, so nothing was changed.")
        if phone_requests.REQUEST_FILE.fullmatch(name):
            try:
                phone_requests.check(name, data)
            except ValueError:
                raise SyncError(f"{name} in Google Drive is not a valid song request, so it was ignored.")
        target = self._path(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        temp = target.with_name(name + ".part")
        temp.write_bytes(content)
        temp.replace(target)

    def _trash(self, path):
        try:
            from send2trash import send2trash
            send2trash(str(path))
        except Exception:                               # no Trash here: keep a copy instead of deleting
            keep = self.data_dir / "backups" / "sync-deleted"
            keep.mkdir(parents=True, exist_ok=True)
            Path(path).replace(keep / Path(path).name)

    def _keep_conflict(self, name, content):
        self.conflicts_dir.mkdir(parents=True, exist_ok=True)
        (self.conflicts_dir / f"{datetime.now().strftime('%Y%m%d-%H%M%S')}-{name}").write_bytes(content)

    # ---- sync
    def sync(self):
        """Brings this computer and Drive to the same set of songs. Returns counts of what happened."""
        with self.lock:
            try:
                counts = self._sync()
            except SyncError as error:
                self._update_state(last_error=str(error), last_result="error")
                raise
            except Exception as error:                 # a bad file or a bug must not leave "syncing" on screen
                self._update_state(last_error=str(error), last_result="error")
                raise SyncError(f"Sync failed: {error}") from error
            return counts

    def _sync(self):
        remote_files = self._list_remote()
        local = self._local()
        remote = {n: f.get("md5Checksum") for n, f in remote_files.items()}
        counts = {"uploaded": 0, "downloaded": 0, "deleted_here": 0, "deleted_in_drive": 0, "conflicts": 0}
        for name, action in plan(local, remote, self._state().get("files") or {}).items():
            path, rf = self._path(name), remote_files.get(name)
            if name in DATA_FILES:                      # your lists and notes are never deleted by a sync: a missing copy is restored
                action = {"delete_local": "upload", "delete_remote": "download"}.get(action, action)
            if action == "conflict":
                counts["conflicts"] += 1
                if _epoch(rf.get("modifiedTime")) >= path.stat().st_mtime:
                    self._keep_conflict(name, path.read_bytes())
                    action = "download"
                else:
                    self._keep_conflict(name, self._download(rf["id"]))
                    action = "upload"
            if action == "upload":
                self._upload(name, path.read_bytes(), rf and rf["id"])
                counts["uploaded"] += 1
            elif action == "download":
                try:
                    self._write_local(name, self._download(rf["id"]))
                except SyncError:
                    if not phone_requests.REQUEST_FILE.fullmatch(name):
                        raise                           # a malformed request file is skipped; it must never block the sync
                    continue
                counts["downloaded"] += 1
            elif action == "delete_local":
                if phone_requests.REQUEST_FILE.fullmatch(name):
                    path.unlink()                       # a finished request: nothing worth keeping in the Trash
                else:
                    self.trash(path)
                counts["deleted_here"] += 1
            elif action == "delete_remote":
                self._delete_remote(rf["id"])
                counts["deleted_in_drive"] += 1
        if counts["downloaded"] or counts["deleted_here"]:
            self.revision += 1
        self._save_state({"files": self._local(), "last_synced_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                          "last_result": counts, "last_error": None})
        return counts
