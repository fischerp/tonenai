# mk2ai

Tell Claude a song or band; it builds a BOSS Katana MkII patch and applies it to the amp over USB MIDI,
or exports a `.tsl` liveset for BOSS TONE STUDIO.

## Setup
```
python3 -m venv .venv && .venv/bin/pip install mido python-rtmidi
.venv/bin/python tools/extract_catalog.py tools/catalog.json   # needs BOSS TONE STUDIO installed
```

## Use
```
.venv/bin/python tools/patch.py apply songs/example.json      # live, to the amp's temporary patch
.venv/bin/python tools/patch.py export songs/example.json -o out.tsl
.venv/bin/python tools/patch.py backup -o backups/current.tsl # save what the amp has now
```

## Findings (verified on a real Katana MkII)
- SysEx header `F0 41 <devid> 00 00 00 33`; device id comes from the identity reply (00 here), not 10.
- Send DT1 `7F 00 00 01` = 1 (editor mode) before reading/writing.
- Temp patch lives at `60 00 00 00`; panel amp type N (Acoustic..Brown = 0..4) sets preamp type 28+N.
- Parameter layout and option names are read from the installed Tone Studio bundle (`tools/params.py`).
