# Tonai

Tell Claude a song or band; it builds a BOSS Katana MkII patch and applies it to the amp over USB MIDI,
or exports a `.tsl` liveset for BOSS TONE STUDIO. Includes a Netflix-style desktop app to pick songs.

> **Unofficial.** This project is not affiliated with, endorsed by or sponsored by BOSS or Roland. "BOSS",
> "Roland", "KATANA" and "TONE STUDIO" are trademarks of Roland Corporation, used here only to say which
> hardware and software this works with. Tones are informed approximations of how a song sounds, not copies.

**Status:** tested only on a Katana MkII. Other Katana models may or may not work; see Compatibility.

## What is a `.tsl` file?
The file format BOSS TONE STUDIO uses to save a set of patches (a "liveset"; the name is inferred from the app,
BOSS does not spell it out). It is plain JSON: a name, format revision `0002`, the device name and each patch as lists
of hex bytes. Tone Studio's full backups use `.alb`.

## Compatibility
- Tested: Katana MkII. Likely: other MkII sizes. Unknown: original Katana (MkI), Gen 3, Air, Go, Artist.
- macOS only so far (Core MIDI + pywebview). PRs welcome.

## Requirements
- BOSS TONE STUDIO for KATANA MkII installed (the parameter list is read from the installed app at setup; no
  BOSS files are shipped in this repo, and `tools/catalog.json` and `songs/covers/` are git-ignored on purpose).
- The KATANA USB driver, Python 3.12+, and the amp connected over USB.
- Optional, for "Create with Claude": a Claude sign-in (below) or your own Anthropic API key.

## Setup
```
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python tools/extract_catalog.py tools/catalog.json   # needs BOSS TONE STUDIO installed
```

## Desktop app
```
Double-click Tonai.app (keep it inside the project folder; drag it to the Dock if you like)
./run.command            # or from a terminal: .venv/bin/python app/main.py
```
Netflix-style library of your songs: click a song, pick a patch, it is sent to the amp. **Import .tsl** loads any Tone Studio liveset file into the library. Search the library; if a
song is not found, **Create with Claude** researches the tone and adds `songs/<artist>-<title>.json` + `.tsl`.
One-time sign-in for that feature (the app uses the Claude Agent SDK bundled in the venv):
```
.venv/lib/python3.14/site-packages/claude_agent_sdk/_bundled/claude /login
```
**My guitars:** register the guitars you own (stored locally in `data/`, not in git). Each song lists the original
guitars and pickups used on the record (researched by Claude, with a confidence level) and ranks your guitars for it,
with the pickup position to use.

**Also per song:** Trio+ band setup (genre, BPM, click track, steps) and Whammy DT setup (tuning, when to use it).

UI dev without the amp: `python3 -m http.server 8765` then open `http://localhost:8765/app/preview.html` (mock amp).

## CLI
```
.venv/bin/python tools/patch.py apply songs/gnr-welcome-to-the-jungle.json      # live, to the amp's temporary patch
.venv/bin/python tools/patch.py export songs/gnr-welcome-to-the-jungle.json -o out.tsl
.venv/bin/python tools/patch.py backup -o backups/current.tsl # save what the amp has now
```

## Findings (verified on a real Katana MkII)
- SysEx header `F0 41 <devid> 00 00 00 33`; device id comes from the identity reply (00 here), not 10.
- Send DT1 `7F 00 00 01` = 1 (editor mode) before reading/writing.
- Temp patch lives at `60 00 00 00`; panel amp type N (Acoustic..Brown = 0..4) sets preamp type 28+N.
- Parameter layout and option names are read from the installed Tone Studio bundle (`tools/params.py`).

## Developer: regenerating the guitar outlines
`tools/trace_guitars.py` re-creates `app/guitar-shapes.js` from reference photos (download them into `/tmp/mkprev/ref/` first;
file names are listed in the credits above). Needs `pip install opencv-python-headless numpy`, then `EXPORT=1 python tools/trace_guitars.py`.

## Roadmap
- Guitar/pickup profile so Claude tunes EQ per guitar; package as a double-click `.app`.
- Optional: tag songs (played / learning), notes, export selection as one `.tsl` liveset.

## Credits and data sources
- Guitar pictures in My guitars are original vector illustrations drawn for Tonai. The **body outlines** of seven models
  (and a few pickguard shapes) were traced from silhouettes of photographs on Wikimedia Commons with `tools/trace_guitars.py`
  (needs OpenCV; only vector paths in `app/guitar-shapes.js` ship, no photo pixels): Fender Stratocaster 004-2 (public domain),
  Fender Telecaster American Vintage 1952 (CC BY-SA 3.0), Full front R9 Les Paul (CC BY-SA 4.0), Iommi SG (CC BY-SA 2.0),
  1960 Gibson ES-335TD (CC BY-SA 4.0), Fender Jaguar 3 tone burst (CC BY-SA 4.0), PRS Custom (1988) Santana (CC BY-SA 4.0).
  Gibson Explorer dark red (CC0) was traced for the Explorer, Martin D-28 Acoustic Guitar (CC BY-SA 4.0) for the Dreadnought, Yamaha Acoustic Guitar FG-331 (CC0) for the Concert/OM and Classical Guitar (CC BY-SA 3.0) for the Classical. The Les Paul outline was redrawn by hand from measurements of the traced shape; the Flying V outline is hand-drawn. Credit and share-alike apply to `app/guitar-shapes.js`.
- Album art and song search use Apple's public iTunes Search API; covers are downloaded to a local cache for your own
  use and are not part of this repo. Artwork belongs to its respective owners.
- Create with Claude uses the Claude Agent SDK; each user signs in themselves or supplies their own API key.
- Amp parameter layout was learned from the installed BOSS TONE STUDIO app and from testing on real hardware, for
  interoperability.

## Safety
Patches are written to the amp's temporary (live) patch, not to its memory slots, and power-cycling restores
your stored sounds. Still, back up first (`patch.py backup`) and use at your own risk; see the license.

## License
PolyForm Noncommercial 1.0.0 (commercial use needs a separate written licence from the author), see [LICENSE](LICENSE).
