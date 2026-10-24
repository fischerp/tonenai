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

## Band setup (DigiTech Trio+)
Add a `"band"` object to the FIRST patch of every song file (the Trio+ is a band-creator pedal the user plays along with;
it is set by hand, not by MIDI). Look up the real tempo/key/structure with a web search; never invent numbers.
```json
"band": {"genre": "Rock", "style_feel": "driving straight eighths, kick on 1 and 3", "bpm": 117, "time_signature": "4/4",
         "key": "F minor", "parts": ["Part A: intro/verse - quiet, sparse drums", "Part B: chorus - full band"],
         "tips": "Play the riff 2 bars cleanly before it starts learning; keep strumming steady."}
```
- `genre` must be one of the Trio+'s 12: Blues, R&B, Rock, Alternative Rock, Metal, Pop, Electronic Pop, Hip-Hop, Country,
  Folk, Latin, Jazz. Each genre has 12 styles (some 3/4): describe the feel, the user picks the style number.
- The Trio+ learns up to 5 song parts, so give at most 5 `parts` in play order.
