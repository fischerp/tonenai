---
name: katana-patch
description: Configure the BOSS Katana MkII for a song or band. Use when the user names a song/band/artist/tone they want to play, or asks to set, tweak, back up or export an amp patch.
---

# Katana MkII patch from a song

1. **Research the tone** (guitar, amp, pedals, effects on the recording; ask for the user's guitar/pickups if unknown).
2. **Backup first**: `.venv/bin/python tools/patch.py backup -o backups/<date>.tsl` (needs the amp on and USB connected).
3. **Write a spec** `songs/<artist>-<song>.json` (see `songs/gnr-welcome-to-the-jungle.json`). Keys: `name` (<=16 chars), `memo`,
   `amp{type ACOUSTIC|CLEAN|CRUNCH|LEAD|BROWN, gain, bass, mid, treble, presence, volume}`, `booster`, `mod`, `fx`
   (`type` + optional `params`, e.g. `{"RATE":40}` for `PRM_FX1_CHORUS_RATE`), `delay`, `reverb`, `noise_suppressor`,
   and `raw: [[block, "PRM_NAME", value]]` as an escape hatch. Valid type names: `tools/params.py` `TABLES`.
4. **Apply live**: `.venv/bin/python tools/patch.py apply songs/<file>.json` (writes the amp's temporary patch; not saved
   to a memory slot). Verify with `patch.py show`. Tell the user it is an informed approximation to tweak by ear.
5. **Keep it**: `patch.py export songs/<file>.json -o songs/<file>.tsl` and import in BOSS TONE STUDIO (Librarian).
   Multiple patches per song (rhythm/lead/clean) = a JSON list.

Rules: never overwrite the user's stored patches without a backup; keep rhythm/lead variants as separate patches;
Katana MkII has 5 amp types + Variation, 5 effect slots (booster, mod, fx, delay, reverb).
Hardware facts are in README.md. Commit straight to `main`; the user tags releases themselves.
