"""Find the installed BOSS TONE STUDIO for KATANA MkII resources (macOS or Windows).

We need the folder that contains js/config/address_map.js and export/item.json. Nothing from it is copied
into this repo. If it is not found automatically, set TONENAI_TONE_STUDIO to the Tone Studio install folder
(any folder above the 'html' resources folder works; the search goes downwards from it).
"""
import os, sys

MARK = os.path.join("js", "config", "address_map.js")
MAC_DEFAULT = ("/Applications/BOSS/KATANA MkII/BOSS TONE STUDIO for KATANA MkII.app"
               "/Contents/Resources/html/")


def _roots():
    hint = os.environ.get("TONENAI_TONE_STUDIO")
    if hint:
        yield hint
    if sys.platform == "darwin":
        yield "/Applications/BOSS"
    elif sys.platform == "win32":
        for var in ("ProgramFiles", "ProgramFiles(x86)", "ProgramW6432", "LOCALAPPDATA", "APPDATA"):
            base = os.environ.get(var)
            if not base:
                continue
            if var in ("LOCALAPPDATA",):
                base = os.path.join(base, "Programs")
            try:
                for d in os.listdir(base):
                    if any(k in d.lower() for k in ("boss", "roland", "katana")):
                        yield os.path.join(base, d)
            except OSError:
                pass


def _search(root, max_depth=8):
    root = os.path.abspath(root)
    base_depth = root.rstrip(os.sep).count(os.sep)
    for dirpath, dirs, files in os.walk(root):
        if dirpath.count(os.sep) - base_depth >= max_depth:
            dirs[:] = []
        if dirpath.endswith(os.path.dirname(MARK)) and os.path.basename(MARK) in files:
            return os.path.dirname(os.path.dirname(dirpath))     # the folder holding js/ and export/
    return None


def find_html():
    """The Tone Studio resources folder (with a trailing separator), or the macOS default path if nothing is found,
    so the error message still says where we looked."""
    if os.path.isfile(os.path.join(MAC_DEFAULT, MARK)):
        return MAC_DEFAULT
    for root in _roots():
        found = _search(root)
        if found:
            return os.path.join(found, "")
    return MAC_DEFAULT


def found():
    return os.path.isfile(os.path.join(find_html(), MARK))
