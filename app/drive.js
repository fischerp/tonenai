/* Google Drive sync dialog (optional). The backend is app/drive_sync.py; setup steps are in docs/google-drive-sync.md. */
let DRV = null, DRV_REV = null, DRV_TIMER = null, PHR = null;

function driveWhen(iso) { try { return new Date(iso).toLocaleString(LANG, { dateStyle: "medium", timeStyle: "short" }); } catch (e) { return iso; } }
function driveLine(s) {
  if (!s) return "";
  if (s.busy === "connect") return tx("Waiting for the Google sign-in in your browser…");
  if (s.busy) return tx("Syncing…");
  if (!s.connected) return tx("Not connected");
  return s.last_synced_at ? tx("Connected · last sync {0}", driveWhen(s.last_synced_at)) : tx("Connected · not synced yet");
}
function driveStatic() {
  driveBind();
  const b = $("#dbtn"); if (!b) return;
  b.innerHTML = `<span class="mic" aria-hidden="true">☁</span><span class="mt">Google Drive<small>${esc(DRV && DRV.connected ? tx("Connected") : tx("Not connected"))}</small></span>`;
  b.title = tx("Keep your songs in your own Google Drive, on all your devices");
}
function driveRequests(s) {      // song requests that arrive from the phone app through Drive
  if (!s.connected || !PHR) return "";
  const mode = (m, label) => `<option value="${m}"${PHR.mode === m ? " selected" : ""}>${esc(label)}</option>`;
  const rows = PHR.items.map(i => `<div class="gm-row" style="margin-top:6px"><div class="m"><div class="n">${esc(i.text)}</div>
    <div class="d">${esc(i.status === "working" ? tx("Creating it now…") : tx("Waiting for you"))}</div></div>
    ${i.status === "working" ? "" : `<div class="act"><button class="p" data-phr="approve" data-id="${esc(i.id)}">${tx("Create")}</button><button class="gm-link" data-phr="dismiss" data-id="${esc(i.id)}">${tx("Dismiss")}</button></div>`}</div>`).join("");
  return `<div style="margin-top:6px"><h4 style="margin:0 0 6px;font-size:13px;letter-spacing:1px;text-transform:uppercase;color:var(--muted)">${tx("Song requests from your phone")}</h4>
    <p class="tip" style="margin:0 0 8px">${esc(tx("The phone app can ask this computer to create a song. It is made here with your Claude sign-in, at most {0} a day, and only while TonenAI is open.", PHR.per_day))}</p>
    <select id="phr-mode" aria-label="${tx("Song requests from your phone")}">${mode("ask", tx("Ask me first"))}${mode("auto", tx("Create automatically"))}${mode("off", tx("Off"))}</select>${rows}</div>`;
}
function driveRender() {
  const s = DRV || {}, r = s.last_result && typeof s.last_result === "object" ? s.last_result : null;
  const notes = [];
  if (s.last_error) notes.push(`<p class="tip" style="margin:0;color:#ff8a85">${esc(tx("Sync error: {0}", s.last_error))}</p>`);
  else if (r) notes.push(`<p class="tip" style="margin:0">${esc(tx("Last sync: {0} sent, {1} received, {2} removed", r.uploaded, r.downloaded, r.deleted_here + r.deleted_in_drive))}${r.conflicts ? "<br>" + esc(tx("{0} kept in data/backups/sync-conflicts because both sides changed", r.conflicts)) : ""}</p>`);
  if (!s.client_configured) notes.push(`<p class="tip" style="margin:0">${esc(tx("One-time setup needed: create your own Google client and save its file as data/google_oauth_client.json. The steps are in docs/google-drive-sync.md."))}</p>`);
  const dis = s.busy ? " disabled" : "";
  $("#dsheet").innerHTML = `<div class="top"><h3>Google Drive</h3><div class="by">${esc(driveLine(s))}</div></div>
  <div class="aiform"><p style="margin:0;line-height:1.5">${esc(tx("Your songs are copied to a private folder of your own Google Drive, so the phone app and this computer show the same library. Nothing is shared with anyone."))}</p>
    ${notes.join("")}
    ${driveRequests(s)}
    <div class="aibtns"><span id="ai-msg"></span><button class="p" data-drv="close">${tx("Close")}</button>
      ${s.connected ? `<button class="p" data-drv="disconnect"${dis}>${tx("Disconnect")}</button><button class="p main" data-drv="sync"${dis}>${tx("Sync now")}</button>`
                    : `<button class="p main" data-drv="connect"${dis || (s.client_configured ? "" : " disabled")}>${tx("Connect Google Drive")}</button>`}</div>
  </div>`;
}
async function driveRefresh() {
  const a = api(); if (!a || !a.drive_status) return;
  DRV = await a.drive_status();
  if (DRV.connected && a.phone_requests_state) { try { PHR = await a.phone_requests_state(); } catch (e) { PHR = null; } }
  if (DRV_REV !== null && DRV.revision !== DRV_REV) {            // a sync brought songs in (or removed some): reload the library
    SONGS = await a.songs(); render(); loadCovers(); toast(tx("Library updated from Google Drive"));
  }
  DRV_REV = DRV.revision;
  driveStatic();
  if ($("#dmodal").classList.contains("open")) driveRender();
  clearTimeout(DRV_TIMER);                                         // quick while something runs or the dialog is open
  DRV_TIMER = setTimeout(driveRefresh, DRV.busy || $("#dmodal").classList.contains("open") ? 1500 : 20000);
}
function driveWatch() { driveRefresh(); }
async function driveOpen() { $("#dmodal").classList.add("open"); driveRender(); await driveRefresh(); }
function driveClose() { $("#dmodal").classList.remove("open"); }

let DRV_BOUND = false;   // the listeners are attached on first use: this file loads before ui.html defines $
function driveBind() {
  if (DRV_BOUND) return; DRV_BOUND = true;
  $("#dbtn").addEventListener("click", driveOpen);
  $("#dmodal").addEventListener("click", e => { if (e.target.id === "dmodal") driveClose(); });
  $("#dsheet").addEventListener("change", async e => {
    if (e.target.id !== "phr-mode") return; const a = api(); if (!a) return;
    const r = await a.set_phone_requests_mode(e.target.value); if (!r.ok) toast(r.error, true); driveRefresh();
  });
  $("#dsheet").addEventListener("click", async e => {
    const q = e.target.closest("[data-phr]");
    if (q) { const a = api(); const r = await a.phone_request_action(q.dataset.id, q.dataset.phr); if (!r.ok) toast(r.error, true); return driveRefresh(); }
    const b = e.target.closest("[data-drv]"); if (!b) return; const a = api(); if (!a) return;
    const what = b.dataset.drv;
    if (what === "close") return driveClose();
    const r = await (what === "connect" ? a.drive_connect() : what === "sync" ? a.drive_sync_now() : a.drive_disconnect());
    if (!r.ok) { const m = $("#dsheet #ai-msg"); if (m) { m.textContent = r.error; m.className = "err"; } return; }
    if (what === "disconnect") toast(tx("Disconnected. Your songs stay on this computer and in Drive."));
    driveRefresh();
  });
}
