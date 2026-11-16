"""Even out loudness across song patches by editing only each amp's "volume" in songs/*.json.

Why: high-gain amps (BROWN/LEAD) come out much louder than CLEAN/ACOUSTIC at the same volume number, so the volume is set
from the amp type, nudged by gain, with lead/solo patches a few points above rhythm. Booster level is left alone.
Dry run by default; pass --apply to write. Afterwards re-export the .tsl files (see README: patch.py export).
"""
import glob, json, os, re, sys

BASE = {"ACOUSTIC": 54, "CLEAN": 56, "CRUNCH": 50, "LEAD": 46, "BROWN": 44}
LEAD_BUMP = 4
LEADISH = re.compile(r"\b(lead|solo|wah)\b|\b(ld)\b", re.I)


def target(patch):
    a = patch.get("amp") or {}
    if a.get("type") not in BASE or not isinstance(a.get("gain"), (int, float)):
        return None
    v = BASE[a["type"]] - round((a["gain"] - 50) / 10)           # more gain = louder, so pull the volume down
    if LEADISH.search(str(patch.get("name", ""))):
        v += LEAD_BUMP
    return max(25, min(75, v))


def main(apply=False, root=None):
    root = root or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "songs")
    changed = 0
    for f in sorted(glob.glob(os.path.join(root, "*.json"))):
        text = open(f, encoding="utf8").read()
        data = json.loads(text)
        patches = data if isinstance(data, list) else [data]
        news = [target(p) for p in patches]
        olds = [(p.get("amp") or {}).get("volume") for p in patches]
        found = list(re.finditer(r'("volume"\s*:\s*)(\d+)', text))
        if len(found) != len(patches) or any(m.group(2) != str(o) for m, o in zip(found, olds)):
            print("skip (layout not understood):", os.path.basename(f)); continue
        it = iter(news)
        def sub(m):
            n = next(it)
            return m.group(0) if n is None else f"{m.group(1)}{n}"
        out = re.sub(r'("volume"\s*:\s*)(\d+)', sub, text)
        for p, o, n in zip(patches, olds, news):
            if n is not None and n != o:
                print(f"{os.path.basename(f)[:34]:34} {str(p.get('name'))[:17]:17} {p['amp']['type']:8} gain {p['amp']['gain']:>3}  volume {o} -> {n}")
        if out != text:
            changed += 1
            if apply:
                open(f, "w", encoding="utf8").write(out)
    print(("applied to" if apply else "would change") + f" {changed} files")


if __name__ == "__main__":
    main("--apply" in sys.argv)
