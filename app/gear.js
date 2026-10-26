/* My gear: the amp and pedals you own. The song screen, the AI prompts and the generators follow this list.
   The catalog lives in gear/catalog.json; your own pedals are kept in data/rig.json with your choices. */
let GADD = false, GEAR_BACK = false, GEXP = new Set(), GPOLL = null;   // GEAR_BACK: the guitar editor was opened from here, so come back when it closes

function openGear() { GADD = false; gearRender(); $("#gearmodal").classList.add("open"); }
function gearClose() { $("#gearmodal").classList.remove("open"); }

async function gearSave(patch) {
  const a = api(); if (!a || !a.set_gear) return;
  const rig = { amp: GEAR.rig.amp, pedals: GEAR.rig.pedals, custom: GEAR.pedals.filter(p => p.custom).map(p => ({ id: p.id, name: p.name, about: p.about })), ...patch };
  const r = await a.set_gear(rig);
  if (!r.ok) return toast(r.error || tx("Could not save"), true);
  GEAR = { amps: r.amps, pedals: r.pedals, rig: r.rig };
  gearRender(); CAR.sig = ""; render();
}

const CONF = { confirmed: "Confirmed", likely: "Likely", unknown: "Unverified" };
function profileBlock(p) {
  if (!p.custom) return "";
  const pr = p.profile, rs = p.research || { state: "none" };
  if (rs.state === "running") return `<div class="gr-prof"><span class="spin"></span>${tx("Researching this pedal…")}</div>`;
  const again = `<button class="gr-rm" data-grresearch="${esc(p.id)}">${tx(pr ? "Research again" : "Research this pedal")}</button>`;
  const err = rs.state === "failed" ? `<div class="gr-err">${tx("Could not research this pedal: {0}", esc(rs.error || ""))}</div>` : "";
  if (!pr) return `<div class="gr-prof">${err || `<span class="gr-dim">${tx("Not researched yet")}</span>`} ${again}</div>`;
  const open = GEXP.has(p.id), n = (pr.controls || []).length;
  const ctl = (pr.controls || []).map((c, i) => `<span class="chip"><b>${esc(c.name)}</b>${esc([c.range, (c.options || []).join(" / ")].filter(Boolean).join(" · ") || tx(c.type))}${n > 1 ? `<button class="gr-x" data-grctl="${esc(p.id)}|${i}" aria-label="${tx("Remove this control")}">×</button>` : ""}</span>`).join("");
  return `<div class="gr-prof">${err}
    <div class="gr-sum">${esc(pr.summary)} <span class="gr-badge ${esc(pr.confidence || "unknown")}">${tx(pr.reviewed ? "Checked by you" : CONF[pr.confidence] || "Unverified")}</span></div>
    <div class="gr-acts"><button class="gr-rm" data-grexp="${esc(p.id)}" aria-expanded="${open}">${tx("{0} controls", n)} ${open ? "▴" : "▾"}</button>${again}</div>
    ${open ? `<div class="chips">${ctl}</div><div class="gr-dim">${tx("MIDI: " + (pr.midi || "unknown"))}</div>
      ${(pr.setup || []).length ? `<div class="gr-h">${tx("First-time setup")}</div><ol class="steps">${pr.setup.map(x => `<li>${esc(x)}</li>`).join("")}</ol>` : ""}
      ${pr.watch ? `<div class="gr-h">${tx("Watch out")}</div><div class="tip">${esc(pr.watch)}</div>` : ""}` : ""}</div>`;
}
function gearPoll() {      // while a pedal is being researched, look again every couple of seconds
  const busy = GEAR.pedals.some(p => p.research && p.research.state === "running");
  if (!busy || !$("#gearmodal").classList.contains("open")) { if (GPOLL) { clearInterval(GPOLL); GPOLL = null; } return; }
  if (GPOLL) return;
  GPOLL = setInterval(async () => {
    const a = api(); if (!a || !$("#gearmodal").classList.contains("open")) return gearPoll();
    const r = await a.get_gear(); GEAR = r; gearRender();
  }, 2000);
}

function gearRender() {
  const keep = document.querySelector("#gearsheet .gr-body"), top = keep ? keep.scrollTop : 0;
  const amp = GEAR.amps.find(x => x.id === GEAR.rig.amp);
  const ampRows = GEAR.amps.map(x => `<label class="gr-row"><input type="radio" name="gramp" value="${esc(x.id)}" ${x.id === GEAR.rig.amp ? "checked" : ""}>
    <span class="gr-t"><span><b>${esc(tx(x.name))}</b>${x.tested ? "" : ` <span class="gr-badge">${tx("Not tested")}</span>`}</span><small>${esc(tx(x.about || ""))}</small></span></label>`).join("");
  const pedRows = GEAR.pedals.map(p => `<div class="gr-card"><div class="gr-row gr-nomb"><label class="gr-lab"><input type="checkbox" data-gped="${esc(p.id)}" ${GEAR.rig.pedals.includes(p.id) ? "checked" : ""}>
    <span class="gr-ic">${esc(p.icon || "🎛")}</span><span class="gr-t"><b>${esc(p.name)}</b><small>${esc(p.custom ? p.about : tx(p.about || ""))}</small></span></label>
    ${p.custom ? `<button class="gr-rm" data-grm="${esc(p.id)}" aria-label="${tx("Remove")}">${tx("Remove")}</button>` : ""}</div>${profileBlock(p)}</div>`).join("");
  const add = GADD
    ? `<div class="gr-form"><input id="gr-name" maxlength="40" placeholder="${tx("Pedal name")}" aria-label="${tx("Pedal name")}">
        <input id="gr-about" maxlength="200" placeholder="${tx("What does it do? (delay, overdrive, looper…)")}" aria-label="${tx("What does it do? (delay, overdrive, looper…)")}">
        <div class="gr-btns"><button class="p" data-gradd="1">${tx("Add")}</button><button class="p ghost" data-grcancel="1">${tx("Cancel")}</button></div></div>`
    : `<button class="p ghost" data-gropen="1">${tx("＋ Add a pedal")}</button>`;
  const guitarRows = GUITARS.map(g => `<div class="gr-row"><div class="gthumb">${guitarPic(g.kind, g.name, g.finish)}</div>
    <span class="gr-t"><b>${esc(g.name)}</b><small>${[g.brand, PK[g.pickups] || g.pickups, fin(defFinish(g)).label].filter(Boolean).map(esc).join(" · ")}</small></span>
    <button class="gr-rm" data-gredit="${esc(g.id)}">${tx("Edit")}</button></div>`).join("");
  $("#gearsheet").innerHTML = `<div class="gr">
    <div class="gr-head"><h3>${tx("My gear")}</h3><div class="sub">${tx("TonenAI only shows and builds setups for gear you own.")}</div>
      <button class="ls-x" data-grclose="1" aria-label="${tx("Close")}">×</button></div>
    <div class="gr-body">
      <h4>${tx("Guitars")}</h4>${guitarRows}
      <button class="p ghost" data-gradg="1">${tx("＋ Add guitar")}</button>
      <p class="gr-note">${tx("The app recommends which one to play for each song.")}</p>
      <h4>${tx("Pedals")}</h4>${pedRows}${add}
      <p class="gr-note">${tx("Your own pedals: the AI works out the settings for each song from the name and what it does.")}</p>
      <h4>${tx("Amp")}</h4>${ampRows}
      ${amp && !amp.tested ? `<p class="gr-warn">${tx("Only the Katana MkII is tested. Check each patch by ear before you trust it on another amp.")}</p>` : ""}
    </div></div>`;
  const nb = document.querySelector("#gearsheet .gr-body"); if (nb) nb.scrollTop = top;
  if (GADD) { const n = $("#gr-name"); if (n) n.focus(); }
  gearPoll();
}

document.getElementById("gearmodal").addEventListener("change", e => {
  const t = e.target;
  if (t.name === "gramp") return gearSave({ amp: t.value });
  if (t.dataset && t.dataset.gped) {
    const on = new Set(GEAR.rig.pedals); t.checked ? on.add(t.dataset.gped) : on.delete(t.dataset.gped);
    return gearSave({ pedals: GEAR.pedals.map(p => p.id).filter(id => on.has(id)) });
  }
});
document.getElementById("gearmodal").addEventListener("click", async e => {
  const b = e.target.closest("button"); if (!b) return;
  if (b.dataset.grclose) return gearClose();
  if (b.dataset.gropen) { GADD = true; return gearRender(); }
  if (b.dataset.grcancel) { GADD = false; return gearRender(); }
  if (b.dataset.grexp) { const id = b.dataset.grexp; GEXP.has(id) ? GEXP.delete(id) : GEXP.add(id); return gearRender(); }
  if (b.dataset.grresearch) {
    const r = await api().research_pedal(b.dataset.grresearch);
    if (!r.ok) return toast(r.error, true);
    GEAR = await api().get_gear(); return gearRender();
  }
  if (b.dataset.grctl) {
    const [id, i] = b.dataset.grctl.split("|"), p = GEAR.pedals.find(x => x.id === id); if (!p || !p.profile) return;
    const prof = { ...p.profile, controls: p.profile.controls.filter((_, k) => k !== Number(i)) };
    const r = await api().save_pedal_profile(id, prof);
    if (!r.ok) return toast(r.error, true);
    GEAR = { amps: r.amps, pedals: r.pedals, rig: r.rig }; return gearRender();
  }
  if (b.dataset.gredit) { gearClose(); GEAR_BACK = true; return openGuitars(b.dataset.gredit); }
  if (b.dataset.gradg) { gearClose(); GEAR_BACK = true; openGuitars(); if (GM.view === "list") { GM = { view: "form", f: newGuitarState("Stratocaster") }; gmRender(); } return; }
  if (b.dataset.grm) {
    const id = b.dataset.grm;
    return gearSave({ pedals: GEAR.rig.pedals.filter(x => x !== id), custom: GEAR.pedals.filter(p => p.custom && p.id !== id).map(p => ({ id: p.id, name: p.name, about: p.about })) });
  }
  if (b.dataset.gradd) {
    const name = $("#gr-name").value.trim(), about = $("#gr-about").value.trim();
    if (!name) return $("#gr-name").focus();
    const custom = [...GEAR.pedals.filter(p => p.custom).map(p => ({ id: p.id, name: p.name, about: p.about })), { name, about }];
    GADD = false;
    const a = api(), r = await a.set_gear({ amp: GEAR.rig.amp, pedals: GEAR.rig.pedals, custom });
    if (!r.ok) return toast(r.error, true);
    const made = r.pedals.find(p => p.custom && p.name === name.slice(0, 40));      // new pedals start switched on
    const pedals = made && !r.rig.pedals.includes(made.id) ? [...r.rig.pedals, made.id] : r.rig.pedals;
    return gearSave({ pedals, custom });
  }
});
