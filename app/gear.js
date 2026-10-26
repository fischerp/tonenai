/* My gear: the amp and pedals you own. The song screen, the AI prompts and the generators follow this list.
   The catalog lives in gear/catalog.json; your own pedals are kept in data/rig.json with your choices. */
let GADD = false;

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

function gearRender() {
  const amp = GEAR.amps.find(x => x.id === GEAR.rig.amp);
  const ampRows = GEAR.amps.map(x => `<label class="gr-row"><input type="radio" name="gramp" value="${esc(x.id)}" ${x.id === GEAR.rig.amp ? "checked" : ""}>
    <span class="gr-t"><span><b>${esc(tx(x.name))}</b>${x.tested ? "" : ` <span class="gr-badge">${tx("Not tested")}</span>`}</span><small>${esc(tx(x.about || ""))}</small></span></label>`).join("");
  const pedRows = GEAR.pedals.map(p => `<div class="gr-row"><label class="gr-lab"><input type="checkbox" data-gped="${esc(p.id)}" ${GEAR.rig.pedals.includes(p.id) ? "checked" : ""}>
    <span class="gr-ic">${esc(p.icon || "🎛")}</span><span class="gr-t"><b>${esc(p.name)}</b><small>${esc(p.custom ? p.about : tx(p.about || ""))}</small></span></label>
    ${p.custom ? `<button class="gr-rm" data-grm="${esc(p.id)}" aria-label="${tx("Remove")}">${tx("Remove")}</button>` : ""}</div>`).join("");
  const add = GADD
    ? `<div class="gr-form"><input id="gr-name" maxlength="40" placeholder="${tx("Pedal name")}" aria-label="${tx("Pedal name")}">
        <input id="gr-about" maxlength="200" placeholder="${tx("What does it do? (delay, overdrive, looper…)")}" aria-label="${tx("What does it do? (delay, overdrive, looper…)")}">
        <div class="gr-btns"><button class="p" data-gradd="1">${tx("Add")}</button><button class="p ghost" data-grcancel="1">${tx("Cancel")}</button></div></div>`
    : `<button class="p ghost" data-gropen="1">${tx("＋ Add a pedal")}</button>`;
  $("#gearsheet").innerHTML = `<div class="gr">
    <div class="gr-head"><h3>${tx("My gear")}</h3><div class="sub">${tx("TonenAI only shows and builds setups for gear you own.")}</div>
      <button class="ls-x" data-grclose="1" aria-label="${tx("Close")}">×</button></div>
    <div class="gr-body">
      <h4>${tx("Amp")}</h4>${ampRows}
      ${amp && !amp.tested ? `<p class="gr-warn">${tx("Only the Katana MkII is tested. Check each patch by ear before you trust it on another amp.")}</p>` : ""}
      <h4>${tx("Pedals")}</h4>${pedRows}${add}
      <p class="gr-note">${tx("Your own pedals: the AI works out the settings for each song from the name and what it does.")}</p>
      <h4>${tx("Guitars")}</h4>
      <div class="gr-row"><span class="gr-t"><b>${GUITARS.length ? tx("{0} guitars", GUITARS.length) : tx("No guitars yet")}</b><small>${tx("The app recommends which one to play for each song.")}</small></span>
        <button class="p ghost" data-grguitars="1">${tx("Manage guitars")}</button></div>
    </div></div>`;
  if (GADD) { const n = $("#gr-name"); if (n) n.focus(); }
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
  if (b.dataset.grguitars) { gearClose(); return openGuitars(); }
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
