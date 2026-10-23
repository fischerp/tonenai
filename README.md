# mk2ai

Tell Claude a song or band; it builds a BOSS Katana MkII patch and applies it to the amp over USB MIDI,
or exports a `.tsl` liveset for BOSS TONE STUDIO.

## Setup
```
python3 -m venv .venv && .venv/bin/pip install mido python-rtmidi
.venv/bin/python tools/extract_catalog.py tools/catalog.json   # needs BOSS TONE STUDIO installed
```

## Desktop app
```
./run.command            # or: .venv/bin/python app/main.py
```
Netflix-style library of your songs: click a song, pick a patch, it is sent to the amp. **Import .tsl** loads any Tone Studio liveset file into the library. Search the library; if a
song is not found, **Create with Claude** researches the tone and adds `songs/<artist>-<title>.json` + `.tsl`.
One-time sign-in for that feature (the app uses the Claude Agent SDK bundled in the venv):
```
.venv/lib/python3.14/site-packages/claude_agent_sdk/_bundled/claude /login
```
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

## Roadmap
- Guitar/pickup profile so Claude tunes EQ per guitar; package as a double-click `.app`.
- Optional: tag songs (played / learning), notes, export selection as one `.tsl` liveset.
