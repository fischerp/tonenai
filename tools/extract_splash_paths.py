"""Copies the logo drawing of the desktop opening splash (app/ui.html) into the Android app's assets as JSON:
    python3 tools/extract_splash_paths.py
Output: android/app/src/main/assets/splash/paths.json  (ring, wave, w1, w2: SVG path data; the app animates them itself)."""
import json, pathlib, re
root = pathlib.Path(__file__).resolve().parent.parent
html = (root / "app" / "ui.html").read_text(encoding="utf8")
svg = re.search(r'<div id="splash".*?</svg>', html, re.S).group(0)
def d(pattern):
    return re.search(pattern, svg, re.S).group(1)
paths = {
    "ring": d(r'id="sp-ring"[^>]*? d="([^"]+)"'),
    "wave": d(r'id="sp-wave"[^>]*? d="([^"]+)"'),
    "w1": d(r'class="sp-w1" d="([^"]+)"'),
    "w2": d(r'class="sp-w2" d="([^"]+)"'),
}
out = root / "android" / "app" / "src" / "main" / "assets" / "splash"
out.mkdir(parents=True, exist_ok=True)
(out / "paths.json").write_text(json.dumps(paths))
print({k: len(v) for k, v in paths.items()})
