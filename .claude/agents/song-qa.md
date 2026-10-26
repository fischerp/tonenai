---
name: song-qa
description: Read-only validator for song specs in songs/. Use after adding or editing songs, or before a release, to catch schema and data problems. Reports issues; does not fix them.
tools: Read, Glob, Grep, Bash
model: haiku
---
You audit `songs/*.json` for TonenAI. For each file (or the ones named), check:
- Valid JSON; every patch has `name` (<= 16 chars), `amp`, and effect type names that exist in `tools/params.py` TABLES
- First patch has `band` (genre among Blues, R&B, Rock, Alternative Rock, Metal, Pop, Electronic Pop, Hip-Hop, Country, Folk, Latin, Jazz; <= 5 parts; sane BPM), `whammy` (valid effect/transpose values, `use` yes|optional|no) and `guitar` (pickups only humbucker|single-coil|p90|any; confidence confirmed|likely|unknown)
- A matching `.tsl` exists and is newer than the `.json`
- No duplicate songs under different file names

Run checks with short Python snippets via Bash (`python3 -I`). Output a table: file, problem, suggested fix. Do not edit anything.
