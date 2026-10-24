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

## Whammy DT setup (DigiTech Whammy DT)
The user also has a Whammy DT in the chain. It is set by hand (5-pin MIDI in only, no USB). Add a `"whammy"` object to the FIRST
patch of every song file. Research the song's real tuning on the recording and where a pitch effect is actually used; never invent.
```json
"whammy": {"use": "yes|optional|no", "tuning": "D standard (D G C F A D)", "when": "Whole song",
           "settings": {"effect": null, "harmony": null, "transpose_down": 2, "transpose_up": null, "detune": null},
           "how": ["Tune your guitar to standard (E A D G B E).", "Turn the right knob to TRANSPOSE DOWN 2.", "Press DROPTUNE (light on) before the song."],
           "tips": "Play it as if in standard tuning; the pedal sounds it a whole step lower."}
```
Pedal facts (from the panel): left knob picks WHAMMY effects (2 OCT UP, OCT UP, 5TH UP, 4TH UP, 2ND UP, 4TH DOWN, 5TH DOWN,
OCT DOWN, 2 OCT DOWN, DIVE BOMB) or HARMONY pairs; DETUNE has SHALLOW/DEEP; right knob picks TRANSPOSE UP or DOWN: 1-7 semitones,
OCT, or OCT DRY. Footswitches: WHAMMY (on/off, heel-toe rocker sweeps the effect), DROPTUNE (latches the transpose on), MOMENTARY
(effect only while held).
- `effect` must be one of the ten whammy names above (or null); `harmony` a short text like "5th down / 4th up" (or null);
  `transpose_down` / `transpose_up` an integer 1-7, "OCT" or "OCT DRY" (or null); `detune` "SHALLOW", "DEEP" or null.
- The DT shifts the WHOLE guitar equally: a whole-step-down song = transpose down 2. It cannot make true Drop D (only the low
  string lowered) from standard; say so if the song is in Drop D and suggest retuning the 6th string instead.
- `use`: "yes" if the song needs it (tuning change, or a signature whammy/octave part), "optional" for a nice-to-have,
  "no" if not needed (still fill `tuning`).

## Guitar setup (original guitars + which of the user's to play)
Add a `"guitar"` object to the FIRST patch of every song file: the guitar(s) actually used on the recording, researched with a web
search (never invent; say `"confidence": "unknown"` if you cannot confirm).
```json
"guitar": {"originals": [{"guitar": "Gibson Les Paul Standard", "player": "Slash", "pickups": "humbucker"}],
           "pickups_needed": "humbucker", "position": "bridge pickup, tone knob around 8",
           "tone_notes": "Thick, mid-focused humbucker tone; a single-coil guitar will sound thinner - add gain and roll back treble.",
           "confidence": "confirmed"}
```
- `pickups` and `pickups_needed` use only: `humbucker`, `single-coil`, `p90`, `any` (a pickup type that matters little).
- `originals`: 1-3 entries; each has `guitar` (model), optional `player`, and `pickups`.
- `position`: which pickup(s) and any knob settings to use for this song.
- `confidence`: `confirmed` (several sources), `likely` (one source) or `unknown`.
