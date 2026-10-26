---
description: Add or improve a song end to end with the agents checking each other's work (research, build, fact-check, QA, fix loop)
argument-hint: <artist - song>
---
Add or improve the song: $ARGUMENTS

You are the coordinator. The agents cannot talk to each other, so pass their output along yourself, in full.

1. **Research.** Run `tone-researcher` on the song. If `songs/` already has it, ask for what to change.
2. **Build.** Run `patch-builder` with the complete research report. It writes the song JSON and the `.tsl`.
3. **Cross-check.** Run `tone-researcher` again in review mode, giving it the built JSON and the first report. It must flag every value that contradicts its sources or that has no source (gear, tuning, BPM, key, Trio+ parts, Whammy settings). It reviews only and edits nothing.
4. **QA.** Run `song-qa` on the new files.
5. **Fix loop.** If step 3 or 4 found problems, send them (verbatim) back to `patch-builder`, then repeat steps 3 and 4. Stop after 3 rounds. If problems remain, list them for the user instead of guessing.
6. **Report.** Give the user a short summary: what was built, confidence per fact (confirmed / likely / unknown), anything unresolved, and the next step.

Rules: never run `patch.py apply` or `backup` here. Ask the user first, then do it in the main session. Do not commit unless asked.
