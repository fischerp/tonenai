# TonenAI for Android

Native Android app (Kotlin, Jetpack Compose) for the BOSS Katana MkII: your song library on the phone, one tap
sends a patch to the amp over USB.

**Works today**
- Library with album covers, search and favourites; the starter songs of the repository come with the app.
- Song page: patches (tap to play on the amp), the guitars on the record and which pickup to use, tuning and
  Whammy notes, tempo and band notes.
- Amp over USB: finds the Katana when you plug it in, shows the patch that is playing, reconnects by itself.
- Import a Tone Studio `.tsl` (the + button, "Open with" or share it to the app).
- Livesets: plan the amp's A1 to B4 slots as a set list and send it over USB. The patches in those slots are saved on
  the phone first and "Undo last send" puts them back; the names are read back to check the write. The livesets
  travel through the Drive sync, like the songs.
- Optional sync of your songs with your own Google Drive, shared with the desktop app
  ([setup](../docs/google-drive-sync.md)).
- Settings > USB diagnostics: a log of what the phone sees, to send when an amp is not found.

**Not yet (the desktop app has them)**: creating songs with AI, My gear and guitars, practice lists and
notes, Portuguese and Spanish. Songs are created on the computer and arrive through the Drive sync.

How it talks to the amp: Android does not list the Katana as a MIDI device (its USB interfaces are
vendor-specific, class `FF`), so the app claims interface 3 itself and exchanges USB-MIDI event packets over its
bulk endpoints (`katana/UsbRawLink.kt`). Verified on a Samsung SM-S938B (Android 17) with a Katana MkII
(`0582:01D8`): identity, patch read and sending a patch. Only the amp's temporary (live) patch is written.

## Build and install
You need JDK 17+, the Android SDK (platform 37; set `sdk.dir` in `android/local.properties`) and a phone with
Android 8.0 or newer.

```bash
cd android
./install.command                   # tests, build, install on the phone plugged in with USB debugging, start
./gradlew :app:assembleDebug        # just the APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # SysEx, song files and sync rules
```

Then unplug the phone from the computer and plug the amp into it (the amp's USB-B cable plus a USB-C adapter).
Android asks whether TonenAI may use the amp; allow it.

The starter library is packed at build time from the songs Git tracks in `../songs/`. Your own songs are not
packed: connect Google Drive on the computer and on the phone.

## Code
| Where | What it does |
|---|---|
| `katana/Sysex.kt`, `katana/Katana.kt` | Roland DT1/RQ1 messages, 7-bit addresses, send a patch live (ports of `tools/katana.py` and `apply_live`) |
| `katana/Tsl.kt` | reads `.tsl` livesets; block address table (from `tools/params.py`) |
| `katana/UsbRawLink.kt`, `katana/MidiApiLink.kt` | the amp over direct USB (used) and over Android's MIDI API (diagnostics only) |
| `data/Songs.kt`, `data/Covers.kt` | the song files (same format as the desktop app), the library on the phone, album art |
| `data/DriveSync.kt` | Drive sync; same rules as `app/drive_sync.py` |
| `AppViewModel.kt` | library, amp connection and sync state |
| `ui/` | theme and screens: home, song, settings, USB diagnostics |

## Next steps
1. Parameter map import (generated on the computer from Tone Studio; BOSS data is not shipped) and the patch builder.
2. Create with AI (OpenAI-style servers, Anthropic API, Ollama/LM Studio on the local network).
3. Livesets, My gear, guitars, practice lists, Portuguese and Spanish.

- **Request a song** from your computer: type the song, and the request travels through your Google Drive to the desktop app, which creates it with your Claude sign-in and sends it back. Needs Drive connected and TonenAI open on the computer.
