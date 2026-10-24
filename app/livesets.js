/* Livesets: plan the amp's banks (A: CH-1..4, B: CH-1..4) as a set list, see what is on the amp, send and export.
   The amp's Panel slot cannot be written (same rule as BOSS TONE STUDIO), so it is shown for information only. */
let LS = [], LCUR = null, AMP = null, LSEL = null, LDRAG = null;   // AMP = last read of the amp's slots, LSEL = slot being filled
const SLOT_ORDER = { A: ["A1", "A2", "A3", "A4"], B: ["B1", "B2", "B3", "B4"] };
const slotLabel = k => (k[0] + " · CH-" + k[1]);
const isInit = n => !n || /INIT TONE/i.test(n);
const lsCur = () => LS.find(x => x.id === LCUR) || null;

async function openLivesets() {
  LS = await api().livesets(); if (!LCUR || !lsCur()) LCUR = LS[0] ? LS[0].id : null;
  lsRender(); $("#lmodal").classList.add("open"); lsReadAmp(true);
}
function lsClose() { $("#lmodal").classList.remove("open"); }

async function lsReadAmp(quiet) {
  const r = await api().amp_slots();
  AMP = r.ok ? r.slots : null;
  if (!r.ok && !quiet) toast("Could not read the amp: " + r.error, true);
  if ($("#lmodal").classList.contains("open")) lsRender();
}
const songOf = id => SONGS.find(s => s.id === id);
const patchName = v => { const s = v && songOf(v.song); const p = s && s.patches.find(q => q.index === v.index); return p ? p.name : null; };

function slotCard(key, ls) {
  const v = ls.slots[key], s = v && songOf(v.song), pn = v && patchName(v);
  const amp = AMP && AMP.find(x => x.key === key), ampName = amp ? amp.name : null;
  let state = "";
  if (AMP) {
    if (v && pn && ampName && ampName.trim() === pn.trim().slice(0, 16)) state = '<div class="sl-amp ok">✓ On your amp</div>';
    else if (v && ampName && !isInit(ampName)) state = `<div class="sl-amp warn">Amp has “${esc(ampName)}”: will be replaced</div>`;
    else if (v) state = '<div class="sl-amp">Amp slot is empty</div>';
    else state = `<div class="sl-amp">${isInit(ampName) ? "Empty on the amp" : "Amp has “" + esc(ampName) + "”"}</div>`;
  }
  if (!v || !s) return `<div class="slot" data-slot="${key}" draggable="false"><div class="sl-label"><span>${slotLabel(key)}</span></div><div class="sl-add">＋ Add a song</div>${state}</div>`;
  return `<div class="slot filled" data-slot="${key}" draggable="true" style="background-image:url('${s.cover || ""}'),${grad(s)}">
    <div class="sl-label"><span>${slotLabel(key)}</span><button class="sl-x" data-lclear="${key}" aria-label="Remove">×</button></div>
    <div><div class="sl-title">${esc(s.title)}</div><div class="sl-patch">${esc(pn || "Patch " + (v.index + 1))} · ${esc(s.artist)}</div></div>${state}</div>`;
}

function lsRender() {
  const sheet = $("#lsheet"), ls = lsCur();
  const tabs = LS.map(x => `<button class="ls-tab" role="tab" aria-selected="${x.id === LCUR}" data-ltab="${esc(x.id)}">${esc(x.name)}</button>`).join("") + '<button class="ls-tab new" data-lnew="1">＋ New liveset</button>';
  const head = `<div class="ls-head"><button class="ls-x" data-lclose="1" aria-label="Close">×</button><h3>Livesets</h3><div class="sub">Plan what goes into your amp's banks, then send it with one click</div><div class="ls-tabs" role="tablist">${tabs}</div></div>`;
  if (!ls) {
    sheet.innerHTML = `<div class="ls">${head}<div class="ls-body"><div class="ls-empty"><h4>Create your first liveset</h4><p>A liveset is a set list for your amp: 8 channels (Bank A and B, CH-1 to CH-4) filled with the songs you play.</p>
      <input id="ls-first" placeholder="e.g. Friday gig, Rock covers" maxlength="40"><button class="p main" data-lcreate="1">Create liveset</button></div></div></div>`;
    return;
  }
  const filled = BANK_KEYS_UI.filter(k => ls.slots[k]).length, amp0 = AMP && AMP[0];
  const bank = b => `<div><div class="bank-h"><span>BANK ${b}</span><span>CH-1 to CH-4</span></div><div class="slots">${SLOT_ORDER[b].map(k => slotCard(k, ls)).join("")}</div></div>`;
  sheet.innerHTML = `<div class="ls">${head}<div class="ls-body">
      <div class="ls-bar"><input id="ls-name" value="${esc(ls.name)}" maxlength="40" aria-label="Liveset name"><div class="grow"></div>
        <button class="p" data-lread="1" title="Read what is stored in the amp now">↻ Read amp</button><button class="p" data-lexport="1" title="Save as a BOSS TONE STUDIO .tsl file">⤓ Export .tsl</button><button class="p" data-ldelete="1">Delete</button></div>
      <div class="panel-row"><span class="tagp">PANEL</span><span>${AMP ? "On your amp: <b>" + esc((amp0 && amp0.name) || "—") + "</b>. " : ""}The Panel follows your knobs and can't be written, like in Tone Studio. Your sounds live in the 8 channels below.</span></div>
      <div class="banks">${bank("A")}${bank("B")}</div>
      <p class="ls-note" style="margin-top:14px">Click a card to choose a song and patch, drag cards to swap them. ${AMP ? "" : "Power on the amp and press <b>Read amp</b> to compare with what is stored now."}</p></div>
    <div class="ls-foot"><span class="grow">${filled} of 8 channels planned</span><button class="p main" data-lsend="1" ${filled ? "" : "disabled"}>Send to amp…</button></div></div>`;
  wireDrag();
}
const BANK_KEYS_UI = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"];

async function lsSave(ls) { const r = await api().liveset_save(ls); if (r.ok) { LS = r.livesets; LCUR = r.id; } else toast(r.error, true); return r; }

// ---- song picker
function openPicker(key) {
  LSEL = key; const ov = document.createElement("div"); ov.className = "ls-ov"; ov.id = "lpick";
  const cur = lsCur().slots[key];
  ov.innerHTML = `<div class="ls-card"><h4>${slotLabel(key)}: choose a patch</h4><div class="sub">Pick the song and the sound for this channel.</div><input class="q" id="pk-q" placeholder="Search songs or artists" autocomplete="off"><div class="pk-list" id="pk-list"></div>
    <div class="acts">${cur ? '<button class="p" data-lclear="' + key + '">Empty this channel</button>' : ""}<button class="p" data-lpickx="1">Cancel</button></div></div>`;
  $("#lsheet").appendChild(ov);
  const draw = () => { const q = $("#pk-q").value.toLowerCase();
    $("#pk-list").innerHTML = SONGS.filter(s => !q || (s.title + " " + s.artist).toLowerCase().includes(q)).map(s => `<div class="pk-song"><div class="pk-art" style="background-image:url('${s.cover || ""}'),${grad(s)}"></div>
      <div class="m"><div class="t">${esc(s.title)}</div><div class="a">${esc(s.artist)}</div></div><div class="pk-patches">${s.patches.map(p => `<button data-lassign="${esc(s.id)}" data-i="${p.index}">${esc(p.name)}</button>`).join("")}</div></div>`).join("") || '<div class="sub">No songs match.</div>'; };
  $("#pk-q").addEventListener("input", draw); draw(); $("#pk-q").focus();
}
const closePicker = () => { const o = $("#lpick"); if (o) o.remove(); LSEL = null; };

// ---- drag and drop to swap channels
function wireDrag() {
  document.querySelectorAll("#lsheet .slot").forEach(el => {
    el.addEventListener("dragstart", e => { LDRAG = el.dataset.slot; el.classList.add("dragging"); e.dataTransfer.effectAllowed = "move"; e.dataTransfer.setData("text/plain", LDRAG); });
    el.addEventListener("dragend", () => { el.classList.remove("dragging"); document.querySelectorAll(".slot.over").forEach(x => x.classList.remove("over")); });
    el.addEventListener("dragover", e => { if (LDRAG) { e.preventDefault(); el.classList.add("over"); } });
    el.addEventListener("dragleave", () => el.classList.remove("over"));
    el.addEventListener("drop", async e => { e.preventDefault(); el.classList.remove("over"); const from = LDRAG, to = el.dataset.slot; LDRAG = null; if (!from || from === to) return;
      const ls = lsCur(), slots = { ...ls.slots }, a = slots[from], b = slots[to]; if (b) slots[from] = b; else delete slots[from]; slots[to] = a; ls.slots = slots; await lsSave(ls); lsRender(); });
  });
}

// ---- send to amp (confirmation first: it overwrites what is stored in those channels)
function openSendConfirm() {
  const ls = lsCur(); const rows = BANK_KEYS_UI.filter(k => ls.slots[k]).map(k => {
    const v = ls.slots[k], s = songOf(v.song), pn = patchName(v) || "patch", amp = AMP && AMP.find(x => x.key === k);
    const rep = amp && amp.name && !isInit(amp.name) && amp.name.trim() !== pn.trim().slice(0, 16) ? `<span class="w">replaces “${esc(amp.name)}”</span>` : amp && amp.name && amp.name.trim() === pn.trim().slice(0, 16) ? '<span class="ok">already there</span>' : "";
    return `<div class="cf-row"><div class="k">${slotLabel(k)}</div><div>${esc(pn)} · ${esc(s ? s.title : v.song)} ${rep}</div></div>`; }).join("");
  const ov = document.createElement("div"); ov.className = "ls-ov"; ov.id = "lconfirm";
  ov.innerHTML = `<div class="ls-card"><h4>Write to your amp?</h4><div class="sub">These channels will be overwritten. What's stored in them now is backed up first (in <code>data/backups</code>).</div><div class="cf-table">${rows}</div>
    <div class="acts"><button class="p" data-lcancel="1">Cancel</button><button class="p main" data-lgo="1">Write ${BANK_KEYS_UI.filter(k => ls.slots[k]).length} patches</button></div></div>`;
  $("#lsheet").appendChild(ov);
}
async function doSend() {
  const ov = $("#lconfirm"); const ls = lsCur();
  ov.querySelector(".ls-card").innerHTML = '<h4><span class="spin"></span>Writing to your amp…</h4><div class="sub">Backing up and writing each channel, then checking it. This takes a few seconds per patch. Don\'t unplug the amp.</div>';
  const r = await api().liveset_send(ls.id); ov.remove();
  if (r.ok) toast(`Wrote ${r.written} patches to your amp and verified them` + (r.backup ? " (previous contents backed up)" : "")); else toast(r.error || "Could not write to the amp", true);
  await lsReadAmp(true);
}

// ---- events
(function wire() {
  const sheet = document.getElementById("lsheet");
  sheet.addEventListener("click", async e => {
    const t = e.target;
    if (t.closest("[data-lclose]")) return lsClose();
    if (t.closest("[data-lpickx]")) return closePicker();
    if (t.closest("[data-lcancel]")) { const o = $("#lconfirm"); if (o) o.remove(); return; }
    if (t.closest("[data-lgo]")) return doSend();
    const clr = t.closest("[data-lclear]"); if (clr) { e.stopPropagation(); const ls = lsCur(); delete ls.slots[clr.dataset.lclear]; await lsSave(ls); closePicker(); return lsRender(); }
    const asg = t.closest("[data-lassign]"); if (asg) { const ls = lsCur(); ls.slots[LSEL] = { song: asg.dataset.lassign, index: Number(asg.dataset.i) }; await lsSave(ls); closePicker(); return lsRender(); }
    const tab = t.closest("[data-ltab]"); if (tab) { LCUR = tab.dataset.ltab; return lsRender(); }
    if (t.closest("[data-lnew]")) { const r = await lsSave({ name: "New liveset " + (LS.length + 1), slots: {} }); lsRender(); const i = $("#ls-name"); if (i) { i.focus(); i.select(); } return; }
    if (t.closest("[data-lcreate]")) { const name = ($("#ls-first").value || "").trim() || "My first liveset"; await lsSave({ name, slots: {} }); return lsRender(); }
    if (t.closest("[data-lread]")) { toast("Reading the amp…"); return lsReadAmp(false); }
    if (t.closest("[data-lexport]")) { const r = await api().liveset_export(LCUR); return r.ok ? toast(`Exported ${r.count} patches as a .tsl file`) : toast(r.error, true); }
    if (t.closest("[data-ldelete]")) { const ls = lsCur(); const r = await api().liveset_delete(ls.id); LS = r.livesets; LCUR = LS[0] ? LS[0].id : null; lsRender();
      return gmToastUndo ? gmToastUndo(`Deleted “${ls.name}”`, async () => { const rr = await lsSave(ls); lsRender(); }) : null; }
    if (t.closest("[data-lsend]")) { const ls = lsCur(); if (!AMP) { toast("Reading the amp first…"); await lsReadAmp(false); } return openSendConfirm(); }
    const slot = t.closest(".slot"); if (slot) return openPicker(slot.dataset.slot);
  });
  sheet.addEventListener("change", async e => { if (e.target.id === "ls-name") { const ls = lsCur(); ls.name = e.target.value.trim() || ls.name; await lsSave(ls); lsRender(); } });
  sheet.addEventListener("keydown", e => { if (e.key === "Enter" && e.target.id === "ls-name") e.target.blur(); if (e.key === "Enter" && e.target.id === "ls-first") sheet.querySelector("[data-lcreate]").click(); });
})();
