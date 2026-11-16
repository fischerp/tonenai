#!/bin/bash
# Rebuilds every songs/*.tsl from its songs/*.json with the real exporter (needs BOSS TONE STUDIO installed).
# Double-click this file in Finder. Output also goes to rebuild-tsl-log.txt.
cd "$(dirname "$0")" || exit 1
exec > >(tee rebuild-tsl-log.txt) 2>&1
PY=.venv/bin/python; [ -x "$PY" ] || PY=python3
ok=0; bad=0
for f in songs/*.json; do
    if "$PY" tools/patch.py export "$f" -o "${f%.json}.tsl" >/dev/null; then ok=$((ok+1)); else echo "FAILED: $f"; bad=$((bad+1)); fi
done
echo "== Rebuilt $ok .tsl files, $bad failed. Originals are in backups/songs-before-leveling."
