"""Extract parameter-to-name catalogs from the installed BOSS TONE STUDIO app bundle."""
import json, re, sys, html
R = "/Applications/BOSS/KATANA MkII/BOSS TONE STUDIO for KATANA MkII.app/Contents/Resources/html/export/"
items = json.load(open(R + "item.json"))
div = open(R + "layout.div", encoding="utf8", errors="ignore").read()
out = {}
for m in re.finditer(r'<div id="([^"]+-select-box)" class="[^"]*elf-select-box-control[^"]*".*?<div id="\1-box"[^>]*>(.*?)</div></div>', div):
    sid, body = m.group(1), m.group(2)
    opts = [html.unescape(o) for o in re.findall(r'msg="">([^<]*)</a>', body)]
    it = items.get(sid, {})
    out[sid] = {"pid": it.get("pid"), "size": it.get("size"), "vofs": it.get("vofs"), "options": opts}
json.dump(out, open(sys.argv[1], "w"), indent=1)
print(len(out), "select boxes")
