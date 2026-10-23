# mk2ai

Katana MkII tone tooling. Use the `katana-patch` skill (`.claude/skills/katana-patch/SKILL.md`) when the user names a song.

- Python in `.venv` (mido, python-rtmidi). Amp must be on, USB connected; run MIDI commands unsandboxed.
- Workflow: commit directly to `main`; the user creates tags now and then. No feature branches.
- `tools/catalog.json` is generated from the installed Tone Studio app (gitignored): `tools/extract_catalog.py`.
- Hardware findings and SysEx notes: README.md.
