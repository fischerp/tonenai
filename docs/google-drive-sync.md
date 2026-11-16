# Google Drive sync

Optional. Keeps the songs on your computer and on the Android app the same, through a private folder of **your own**
Google Drive. A song you create on one device shows up on the other.

## How it works
| | |
|---|---|
| **Where** | The hidden *appDataFolder* of your Drive (scope `drive.appdata` only). TonenAI cannot list, read or change any other Drive file, and the songs do not appear in the Drive UI. |
| **What** | Your song files (`songs/*.json` and `songs/*.tsl`, one Drive file each) and your own lists and notes: livesets, guitars, gear, practice status and notes, tab links (`data/livesets.json`, `guitars.json`, `rig.json`, `song_meta.json`, `tabs.json`, one Drive file each). Not covers, not the app's settings, not API keys. |
| **When** | When the app starts, after you create, import or delete a song, and when you press **Sync now** (⚙ > Google Drive). |
| **Changes** | A song changed or deleted on one device is changed or deleted on the other (deleted songs go to the Trash on the computer). If both changed the same song, the newer copy wins and the other is kept in `data/backups/sync-conflicts/`. A device that lost its whole library at once never empties the other side. Each list file is synced as a whole: if both devices changed the same one, the newer file wins and the older one is kept in the same folder. A sync never deletes these files, it restores a missing copy. |
| **Secrets** | Computer: the Google refresh token is in the macOS Keychain (service `tonenai.google-drive`); on Windows in `~/.tonenai/secrets.json`. Nothing secret is in the project folder or in Git. |
| **Who** | Only you. Nothing is bundled: every user creates their own Google client below, so there is no shared server and no shared quota. |

The songs are not encrypted in Drive: they are amp settings and notes, readable only by your Google account.

## One-time setup: Google Cloud (about 10 minutes)
Any Google account works at <https://console.cloud.google.com/>; no billing account is needed. Sign in with the
account whose Drive you want to use.

1. **Project:** project picker (top bar) > **New project** > name `TonenAI` > Create. Select it in the top bar.
2. **API:** search **Google Drive API** > **Enable**.
3. **Consent screen:** search **Google Auth Platform** > *Get started*: app name `TonenAI`, your e-mail,
   audience **External**, your e-mail as contact > Create.
4. **Test user:** **Audience > Test users > Add users** > your Gmail > Save.
5. **Scope:** **Data Access > Add or remove scopes** > filter `drive.appdata` > tick only `.../auth/drive.appdata` > Update > Save.
6. **Computer client:** **Clients > Create client > Desktop app**, name `TonenAI desktop` > Create > **Download JSON**.
   Save that file as `data/google_oauth_client.json` in the TonenAI folder (`data/` is ignored by Git; never commit it):
   ```bash
   mv "$(grep -l '"installed"' ~/Downloads/*.json | head -1)" ~/Projects/TonenAi/data/google_oauth_client.json
   ```
   Download the JSON right when the client is created: later the Google console may no longer offer it, and you would
   create a new Desktop client instead. The file must contain an `"installed"` section.
7. **Android client:** get the SHA-1 of the key that signs the APK you install (the debug key of your computer):
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
   ```
   **Clients > Create client > Android**: name `TonenAI Android`, package `com.tonenai.app`, paste the SHA-1 > Create.
   Nothing to download. Google may take from 5 minutes to a few hours to activate it.

Every person who builds the app needs their **own** project and their own SHA-1 (the SHA-1 of someone else's key does
not work). If you change `applicationId`, use the new package name in the Android client.

Both clients **must be in the same project**: the hidden folder belongs to the project, which is how the computer
and the phone see the same songs.

## Connect
- **Computer:** ⚙ > **Google Drive** > **Connect Google Drive**. Your browser opens the Google sign-in. On
  *"Google hasn't verified this app"* choose **Advanced > Go to TonenAI** (expected: it is your own client).
- **Android:** **Settings > Google Drive > Connect** and pick the same Google account.

While the consent screen is in *Testing*, Google ends the sign-in after 7 days and you connect again. To stop that,
publish the app (**Audience > Publish app**). Google then asks, under **Branding**, for a homepage URL and a
privacy-policy URL on an authorized domain (two static pages are enough). With only the non-sensitive
`drive.appdata` scope no review is needed.

## Problems
- *"Save the Google OAuth client file as data/google_oauth_client.json first":* step 6.
- *"Access blocked" or error 403 `access_denied` when signing in:* your account is not in **Test users** (step 4).
- *Android: sign-in fails with code 10 or "developer error":* the package or SHA-1 of the Android client is wrong, or
  Google has not activated it yet (step 7). An APK built on another computer has another debug key.
- *"Google access expired or was revoked":* connect again (see the 7-day note above).
- *A song came back after you deleted it:* it was changed on the other device after your last sync, so the newer copy won.

Tests: `python3 -m unittest discover -s tests` (an in-memory Drive, no network).

## Song requests from the phone

The phone app can ask this computer to create a song (Settings → *Request a song from my computer*, or from a search with no result).
It uses the same private Drive folder, with one tiny file per request (`req-<id>.json`: one line of text, 200 characters at most) and one
answer file from the computer (`res-<id>.json`). The computer makes the song with your own Claude sign-in, exactly as if you had typed the
search in the desktop app, and the next sync brings it to the phone.

- It only happens while TonenAI is open on the computer (it checks Drive every few minutes).
- In the Google Drive dialog you choose **Ask me first** (default), **Create automatically** or **Off**. A limit of 5 requests a day applies
  (`phone_requests_per_day` in `data/settings.json`).
- Requests are plain text, validated on both sides, treated as the name of a song and never as instructions. Finished requests are removed after a week.
