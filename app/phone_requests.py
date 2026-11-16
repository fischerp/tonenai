"""Song requests that come from the phone through your Google Drive.

The phone writes one small file per request (req-<id>.json: the words to look up). This computer reads them, makes the song
the same way as when you type a search here, and writes a result file (res-<id>.json) the phone shows. Each file has exactly
one writer, so the two devices never overwrite each other. A request is only ever a short single line of plain text:
the name of a song to look up, handled like something you typed yourself.
"""
from __future__ import annotations

import json, re, time
from pathlib import Path

REQUEST_FILE = re.compile(r"(req|res)-([0-9a-f]{12})\.json")
MAX_TEXT = 200
MAX_NOTE = 200
MAX_BYTES = 4096
STATUSES = ("awaiting-approval", "working", "done", "failed", "declined")
MODES = ("off", "ask", "auto")
DEFAULT_MODE = "ask"
DEFAULT_PER_DAY = 5
KEEP_DAYS = 7
EXPIRE_DAYS = 3                  # a request nobody picked up for this long is not made any more
SONG_ID = re.compile(r"[a-z0-9][a-z0-9\-]{0,99}")


def clean_text(text):
    """The request as one line of plain text, or None when it is not acceptable."""
    if not isinstance(text, str):
        return None
    text = " ".join(text.split())
    if not text or len(text) > MAX_TEXT or any(ord(c) < 32 or ord(c) == 127 for c in text):
        return None
    return text


def check(name, data):
    """Raises ValueError unless `data` is a valid request/result file for this file name."""
    m = REQUEST_FILE.fullmatch(name)
    if not m or not isinstance(data, dict) or data.get("id") != m.group(2):
        raise ValueError("not a song request file")
    if not isinstance(data.get("created", data.get("updated")), int):
        raise ValueError("missing time")
    if m.group(1) == "req":
        if clean_text(data.get("text")) != data.get("text") or not isinstance(data.get("created"), int):
            raise ValueError("bad request text")
        return
    if data.get("status") not in STATUSES:
        raise ValueError("bad status")
    if not isinstance(data.get("note", ""), str) or len(data.get("note", "")) > MAX_NOTE:
        raise ValueError("bad note")
    if data.get("song", "") and not (isinstance(data["song"], str) and SONG_ID.fullmatch(data["song"])):
        raise ValueError("bad song id")


class Inbox:
    def __init__(self, directory):
        self.dir = Path(directory)

    def _read(self, path):
        try:
            if path.stat().st_size > MAX_BYTES:
                return None
            data = json.loads(path.read_text(encoding="utf-8"))
            check(path.name, data)
            return data
        except (OSError, ValueError):
            return None

    def items(self):
        """Every valid request with its result merged in, oldest first."""
        out = []
        if not self.dir.is_dir():
            return out
        for path in self.dir.glob("req-*.json"):
            req = self._read(path)
            if not req:
                continue
            res = self._read(self.dir / f"res-{req['id']}.json") or {}
            out.append({"id": req["id"], "text": req["text"], "created": req["created"], "status": res.get("status", "new"),
                        "note": res.get("note", ""), "song": res.get("song", ""), "updated": res.get("updated", 0)})
        return sorted(out, key=lambda x: (x["created"], x["id"]))

    def write_result(self, rid, status, note="", song="", now=None):
        if status not in STATUSES:
            raise ValueError(status)
        self.dir.mkdir(parents=True, exist_ok=True)
        data = {"id": rid, "status": status, "note": str(note)[:MAX_NOTE], "updated": int((now or time.time()) * 1000)}
        if song:
            data["song"] = song
        check(f"res-{rid}.json", data)
        path = self.dir / f"res-{rid}.json"
        temp = path.with_name(path.name + ".part")
        temp.write_text(json.dumps(data, indent=1), encoding="utf-8")
        temp.replace(path)

    def made_today(self, now=None):
        """How many requests were started in the last 24 hours (the daily limit counts these)."""
        cutoff = ((now or time.time()) - 86400) * 1000
        return sum(1 for i in self.items() if i["status"] in ("working", "done", "failed") and i["updated"] >= cutoff)

    def cleanup(self, now=None):
        """Removes requests and results that are final and older than KEEP_DAYS (the next sync removes them from Drive and the phone)."""
        now = now or time.time()
        gone = 0
        for i in self.items():
            final = i["status"] in ("done", "failed", "declined")
            age = now - max(i["updated"], i["created"]) / 1000
            if (final and age > KEEP_DAYS * 86400) or age > (KEEP_DAYS + EXPIRE_DAYS) * 86400:
                for name in (f"req-{i['id']}.json", f"res-{i['id']}.json"):
                    try:
                        (self.dir / name).unlink()
                        gone += 1
                    except OSError:
                        pass
        return gone
