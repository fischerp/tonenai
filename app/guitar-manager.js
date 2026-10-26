/* My guitars: list + add/edit form.
   UX rules: pick the type first and pre-fill everything else from it (brand, name, pickups, finish) while never
   overwriting what the user typed; one clear primary action; inline validation; instant live preview; undo instead of confirm. */
const PK = { "single-coil": "Single-coil", humbucker: "Humbucker", p90: "P-90", sss: "SSS", hh: "HH", hs: "HS", hss: "HSS", hsh: "HSH", other: "Other", acoustic: "Acoustic" };
const pkLabel = g => g.pickups === "other" ? (g.pickups_other || PK.other) : (PK[g.pickups] || g.pickups);
// What a layout means for matching a song: single-coil, humbucker, p90, mixed (both kinds) or other (unknown)
const pkClass = pk => ({ sss: "single-coil", hh: "humbucker", hs: "mixed", hss: "mixed", hsh: "mixed" })[pk] || pk;
const PK_LONG = { sss: "SSS: three single-coils (Strat style)", hh: "HH: two humbuckers", hs: "HS: humbucker + single-coil", hss: "HSS: humbucker + two single-coils", hsh: "HSH: two humbuckers + a single-coil", "single-coil": "Single-coil pickups", humbucker: "Humbucker pickups", p90: "P-90 pickups", other: "Something else: say what it is", acoustic: "Acoustic (no pickups)" };
const GTYPES = [
  { k: "Stratocaster", short: "Strat", w: "Stratocaster", pk: "single-coil", fin: "sunburst", brands: ["Fender", "Squier"] },
  { k: "Telecaster", short: "Tele", w: "Telecaster", pk: "single-coil", fin: "butter", brands: ["Fender", "Squier"] },
  { k: "Les Paul", short: "Les Paul", w: "Les Paul", pk: "humbucker", fin: "cherryburst", brands: ["Gibson", "Epiphone"] },
  { k: "SG", short: "SG", w: "SG", pk: "humbucker", fin: "cherry", brands: ["Gibson", "Epiphone"] },
  { k: "ES-335 / semi-hollow", short: "ES-335", w: "ES-335", pk: "humbucker", fin: "cherryburst", brands: ["Gibson", "Epiphone"] },
  { k: "Jazzmaster / Jaguar", short: "Jazzmaster", w: "Jazzmaster", pk: "single-coil", fin: "red", brands: ["Fender", "Squier"] },
  { k: "Explorer", short: "Explorer", w: "Explorer", pk: "humbucker", fin: "cherry", brands: ["Gibson", "Epiphone"] },
  { k: "Flying V", short: "Flying V", w: "Flying V", pk: "humbucker", fin: "honey", brands: ["Gibson", "Epiphone"] },
  { k: "PRS / Superstrat", short: "PRS", w: "Custom 24", pk: "hsh", fin: "cherryburst", brands: ["PRS", "Ibanez"] },
  { k: "Dreadnought", short: "Dreadnought", w: "Dreadnought", pk: "acoustic", fin: "natural", brands: ["Martin", "Taylor", "Yamaha"] },
  { k: "Concert / OM", short: "Concert/OM", w: "OM", pk: "acoustic", fin: "natural", brands: ["Martin", "Taylor", "Yamaha"] },
  { k: "Classical", short: "Classical", w: "Classical", pk: "acoustic", fin: "natural", brands: ["Yamaha", "Cordoba", "Alhambra"] },
  { k: "Other", short: "Other", w: "", pk: "humbucker", fin: "grey", brands: [] },
];
const ALL_BRANDS = ["Fender", "Gibson", "Epiphone", "PRS", "Ibanez", "ESP", "LTD", "Jackson", "Charvel", "Squier", "Gretsch", "Schecter", "Yamaha", "Rickenbacker",
  "Music Man", "G&L", "Danelectro", "Hagstrom", "Reverend", "Godin", "Sterling", "Cort", "Martin", "Taylor", "Takamine", "Cordoba", "Alhambra", "Guild", "Seagull", "Ovation"];
const LEGACY_P90 = "P-90 guitar (Junior, Special…)";
const normKind = (k, name) => k === "Explorer / Flying V" ? (/flying|\bv\b/i.test(name || "") ? "Flying V" : "Explorer")   // old combined type
  : k === LEGACY_P90 ? "Les Paul" : k === "Acoustic" ? "Dreadnought" : k;                                                                                       // P-90 is a pickup, not a body shape
const defFinish = g => g.finish || (g.kind === LEGACY_P90 ? "tv" : gtype(normKind(g.kind, g.name)).fin);
const isAcoustic = k => ["Dreadnought", "Concert / OM", "Classical", "Acoustic"].includes(k);
const gtype = k => GTYPES.find(t => t.k === k) || GTYPES[GTYPES.length - 1];
let GM = null;   // { view: "list" | "form", f: form state }

function openGuitars(editId) {
  const id = typeof editId === "string" ? editId : null;
  const g = id && GUITARS.find(x => x.id === id);
  if (g) GM = { view: "form", f: { id: g.id, kind: normKind(g.kind, g.name), brand: g.brand || "", name: g.name, nameTouched: true, brandTouched: true, pickups: g.pickups, pickupsOther: g.pickups_other || "", pkTouched: true,
    finish: defFinish(g), finTouched: true, notes: g.notes || "" } };
  else if (GUITARS.length) GM = { view: "list" };
  else GM = { view: "form", f: newGuitarState("Stratocaster"), first: true };
  gmRender();
  $("#gmodal").classList.add("open");
}
function newGuitarState(kind) {
  const t = gtype(kind);
  return { id: "", kind, brand: t.brands[0] || "", name: "", nameTouched: false, brandTouched: false, pickups: t.pk, pickupsOther: "", pkTouched: false, finish: t.fin, finTouched: false, notes: "" };
}
function gmClose() {
  $("#gmodal").classList.remove("open");
  if (typeof SHEET !== "undefined" && SHEET && $("#modal").classList.contains("open")) openSong(SHEET);   // refresh the song's guitar ranking
  if (typeof GEAR_BACK !== "undefined" && GEAR_BACK) { GEAR_BACK = false; openGear(); }   // opened from My gear: go back there
}
const fin = k => { const x = GUITAR_FINISHES.find(x => x.key === k) || GUITAR_FINISHES[0]; return { ...x, label: tx(x.label) }; };
const suggestName = f => [f.brand.trim(), gtype(f.kind).w].filter(Boolean).join(" ");

function gmRender() {
  const sheet = $("#gsheet");
  if (GM.view === "list") {
    const rows = GUITARS.map(g => `<div class="gm-row"><div class="gthumb">${guitarPic(g.kind, g.name, g.finish)}</div>
      <div class="m"><div class="n">${esc(g.name)}</div><div class="d">${[g.brand, pkLabel(g), fin(defFinish(g)).label].filter(Boolean).map(esc).join(" · ")}${g.notes ? " · " + esc(g.notes) : ""}</div></div>
      <div class="act"><button class="gm-link" data-gm="edit" data-id="${esc(g.id)}">${tx("Edit")}</button><button class="gm-link danger" data-gm="remove" data-id="${esc(g.id)}">${tx("Remove")}</button></div></div>`).join("");
    sheet.innerHTML = `<div class="gm"><div class="gm-head"><button class="gm-x" data-gm="close" aria-label="${tx("Close")}">×</button><h3>${tx("My guitars")}</h3><div class="sub">${tx("TonenAI recommends the best one for each song")}</div></div>
      <div class="gm-body"><div class="gm-list">${rows}</div></div>
      <div class="gm-foot"><span class="grow">${GUITARS.length === 1 ? tx("1 guitar") : tx("{0} guitars", GUITARS.length)}</span><button class="p main" data-gm="add">${tx("＋ Add guitar")}</button></div></div>`;
    return;
  }
  const f = GM.f, editing = !!f.id;
  sheet.innerHTML = `<form class="gm" id="gform" novalidate autocomplete="off">
    <div class="gm-head"><button type="button" class="gm-x" data-gm="${GUITARS.length ? "cancel" : "close"}" aria-label="${tx("Close")}">×</button>
      <h3>${editing ? tx("Edit guitar") : GM.first ? tx("Add your first guitar") : tx("Add a guitar")}</h3><div class="sub">${editing ? tx("Change anything; the picture updates as you go.") : tx("Pick the type and we'll fill in the rest. It takes about ten seconds.")}</div></div>
    <div class="gm-body"><div class="gm-form"><div>
      <div class="fld"><div class="lbl"><span id="l-type">${tx("1 · What type of guitar is it?")}</span></div>
        <div class="tiles" role="radiogroup" aria-labelledby="l-type">${GTYPES.map(t => `<button type="button" class="tile" role="radio" data-kind="${esc(t.k)}" aria-checked="false">${guitarPic(t.k, "", t.fin)}<span>${esc(tx(t.short))}</span></button>`).join("")}</div></div>
      <div class="fld"><div class="lbl"><label for="g-brand">${tx("2 · Brand")}</label><span class="opt">${tx("Pick one or type your own")}</span></div>
        <input class="in" id="g-brand" list="g-brands" placeholder="${tx("e.g. Gibson")}" value="${esc(f.brand)}"><datalist id="g-brands">${ALL_BRANDS.map(b => `<option value="${esc(b)}">`).join("")}</datalist>
        <div class="chips2" id="brand-chips"></div></div>
      <div class="fld" id="fld-name"><div class="lbl"><label for="g-name">${tx("3 · Name")}</label><span class="opt">${tx("What you call it")}</span></div>
        <input class="in" id="g-name" placeholder="${tx("e.g. Gibson Les Paul")}" maxlength="60" value="${esc(f.name)}" aria-describedby="name-hint name-err">
        <div class="err" id="name-err" role="alert">${tx("Give your guitar a name (at least 2 characters).")}</div><div class="hint" id="name-hint"></div></div>
      <div class="fld"><div class="lbl"><span id="l-pk">${tx("4 · Pickups")}</span><span class="opt" id="pk-note"></span></div>
        <div class="seg" role="radiogroup" aria-labelledby="l-pk">${Object.keys(PK).filter(k => k !== "acoustic" || isAcoustic(f.kind)).map(k => `<button type="button" role="radio" data-pk="${k}" aria-checked="false" title="${esc(tx(PK_LONG[k]))}">${esc(PK[k])}</button>`).join("")}</div>
        <div class="hint" id="pk-hint"></div>
        <input class="in" id="g-pkother" maxlength="40" placeholder="${tx("e.g. Filter'Tron, EMG 81/85")}" value="${esc(f.pickupsOther || "")}" style="display:none;margin-top:6px" aria-label="${tx("Which pickups?")}"></div>
      <div class="fld"><div class="lbl"><span id="l-fin">${tx("5 · Finish")}</span><span class="opt" id="fin-name"></span></div>
        <div class="swatches" role="radiogroup" aria-labelledby="l-fin">${GUITAR_FINISHES.map(x => `<button type="button" class="sw" role="radio" data-fin="${x.key}" aria-checked="false" aria-label="${esc(tx(x.label))}" title="${esc(tx(x.label))}" style="background:${x.css}"></button>`).join("")}</div></div>
      <div class="fld"><div class="lbl"><label for="g-notes">${tx("Notes")}</label><span class="opt">${tx("Optional")}</span></div>
        <textarea class="in" id="g-notes" rows="2" maxlength="200" placeholder="${tx("e.g. 57 Classic pickups, coil split on the tone knob")}">${esc(f.notes)}</textarea></div>
    </div>
    <aside class="gm-prev" aria-label="${tx("Preview")}"><div id="prev-pic"></div><div class="pn" id="prev-name"></div><div class="pm" id="prev-meta"></div></aside></div></div>
    <div class="gm-foot"><span class="grow"></span>${GUITARS.length ? '<button type="button" class="p" data-gm="cancel">' + tx("Cancel") + '</button>' : ""}<button type="submit" class="p main">${editing ? tx("Save changes") : tx("Save guitar")}</button></div></form>`;
  gmSync(true);
  const first = $("#gsheet .tile[aria-checked='true']"); if (first && !editing) first.focus({ preventScroll: true }); else $("#g-name").focus();
}

function gmSync(initial) {   // push state f into the DOM (idempotent; never touches a field the user is typing in)
  const f = GM.f, t = gtype(f.kind);
  document.querySelectorAll("#gsheet .tile").forEach(b => b.setAttribute("aria-checked", String(b.dataset.kind === f.kind)));
  document.querySelectorAll("#gsheet [data-pk]").forEach(b => b.setAttribute("aria-checked", String(b.dataset.pk === f.pickups)));
  document.querySelectorAll("#gsheet [data-fin]").forEach(b => b.setAttribute("aria-checked", String(b.dataset.fin === f.finish)));
  if (!f.nameTouched) $("#g-name").value = f.name = suggestName(f);
  if (document.activeElement !== $("#g-brand") || initial) $("#g-brand").value = f.brand;
  $("#brand-chips").innerHTML = t.brands.length ? `<span class="tl">${tx("Common for a {0}:", esc(tx(t.short)))}</span>` + t.brands.map(b => `<button type="button" class="pillbtn${b === f.brand ? " on" : ""}" data-brand="${esc(b)}">${esc(b)}</button>`).join("") : "";
  const sug = suggestName(f);
  $("#name-hint").innerHTML = f.name.trim() === sug || !sug ? (sug ? tx("Suggested from brand + type. Edit it any time.") : "") : tx("Suggested: <b>{0}</b> · <button type=\"button\" class=\"linkbtn\" data-gm=\"usesug\">Use it</button>", esc(sug));
  $("#g-pkother").style.display = f.pickups === "other" ? "" : "none";
  $("#pk-note").textContent = f.pickups === t.pk ? tx("Pre-filled for this type") : "";
  $("#pk-hint").innerHTML = `${esc(tx(PK_LONG[f.pickups] || ""))}${f.pickups !== t.pk ? ` · <button type="button" class="linkbtn" data-gm="usepk">${tx("Reset to typical ({0})", esc(PK[t.pk]))}</button>` : ""}`;
  $("#fin-name").textContent = fin(f.finish).label;
  $("#prev-pic").innerHTML = guitarPic(f.kind, f.name, f.finish);
  $("#prev-name").textContent = f.name.trim() || tx("Your guitar");
  $("#prev-meta").textContent = [f.brand.trim(), pkLabel({ pickups: f.pickups, pickups_other: f.pickupsOther }), fin(f.finish).label].filter(Boolean).join(" · ");
  // acoustic pickup option only for acoustics
  const seg = document.querySelector("#gsheet .seg"); const hasAc = !!seg.querySelector('[data-pk="acoustic"]');
  if (isAcoustic(f.kind) !== hasAc) { seg.innerHTML = Object.keys(PK).filter(k => k !== "acoustic" || isAcoustic(f.kind)).map(k => `<button type="button" role="radio" data-pk="${k}" aria-checked="${k === f.pickups}" title="${esc(tx(PK_LONG[k]))}">${esc(PK[k])}</button>`).join(""); }
}

function gmSetKind(k) {
  const f = GM.f, t = gtype(k); f.kind = k;
  if (!f.pkTouched) f.pickups = t.pk;
  if (!f.finTouched) f.finish = t.fin;
  if (!f.brandTouched) f.brand = t.brands[0] || "";
  gmSync();
}
function gmToastUndo(msg, fn) {
  const t = $("#toast"); t.innerHTML = `${esc(msg)}<button type="button" data-undo="1">${tx("Undo")}</button>`; t.className = "toast show";
  gmToastUndo.fn = fn; clearTimeout(toast.t); toast.t = setTimeout(() => { t.className = "toast"; gmToastUndo.fn = null; }, 6000);
}

async function gmSubmit() {
  const f = GM.f, name = $("#g-name").value.trim();
  const bad = name.length < 2; $("#fld-name").classList.toggle("bad", bad); $("#g-name").setAttribute("aria-invalid", String(bad));
  if (bad) { $("#g-name").focus(); return; }
  const r = await api().save_guitar({ id: f.id || undefined, brand: f.brand.trim(), name, kind: f.kind, pickups: f.pickups, pickups_other: f.pickups === "other" ? ($("#g-pkother").value.trim() || f.pickupsOther || "") : "", finish: f.finish, notes: $("#g-notes").value.trim() });
  if (!r.ok) return toast(r.error, true);
  GUITARS = r.guitars; toast(f.id ? tx("Changes saved") : tx("Guitar added")); GM = { view: "list" }; gmRender();
}

(function wire() {
  const sheet = document.getElementById("gsheet");
  sheet.addEventListener("input", e => { if (e.target.id === "g-pkother" && GM && GM.f) { GM.f.pickupsOther = e.target.value; gmSync(); } });
  sheet.addEventListener("click", async e => {
    const el = e.target.closest("[data-gm],[data-kind],[data-pk],[data-fin],[data-brand]"); if (!el) return;
    if (el.dataset.kind) { return gmSetKind(el.dataset.kind); }
    if (el.dataset.pk) { GM.f.pickups = el.dataset.pk; GM.f.pkTouched = true; return gmSync(); }
    if (el.dataset.fin) { GM.f.finish = el.dataset.fin; GM.f.finTouched = true; return gmSync(); }
    if (el.dataset.brand) { GM.f.brand = el.dataset.brand; GM.f.brandTouched = true; return gmSync(); }
    switch (el.dataset.gm) {
      case "close": return gmClose();
      case "add": GM = { view: "form", f: newGuitarState("Stratocaster") }; return gmRender();
      case "cancel": GM = { view: "list" }; return gmRender();
      case "edit": return openGuitars(el.dataset.id);
      case "usesug": GM.f.name = suggestName(GM.f); GM.f.nameTouched = false; return gmSync();
      case "usepk": GM.f.pickups = gtype(GM.f.kind).pk; GM.f.pkTouched = false; return gmSync();
      case "remove": {
        const g = GUITARS.find(x => x.id === el.dataset.id); if (!g) return;
        const r = await api().delete_guitar(g.id); GUITARS = r.guitars; gmRender();
        gmToastUndo(tx("Removed “{0}”", g.name), async () => { const rr = await api().save_guitar(g); if (rr.ok) { GUITARS = rr.guitars; if ($("#gmodal").classList.contains("open")) { GM = { view: "list" }; gmRender(); } } });
      }
    }
  });
  sheet.addEventListener("input", e => {
    if (!GM || GM.view !== "form") return;
    const f = GM.f;
    if (e.target.id === "g-brand") { f.brand = e.target.value; f.brandTouched = true; gmSync(); }
    else if (e.target.id === "g-name") { f.name = e.target.value; f.nameTouched = true; $("#fld-name").classList.remove("bad"); gmSync(); }
    else if (e.target.id === "g-notes") f.notes = e.target.value;
  });
  sheet.addEventListener("focusout", e => { if (e.target.id === "g-name" && GM && GM.view === "form" && !e.target.value.trim()) { GM.f.nameTouched = false; gmSync(); } });
  sheet.addEventListener("submit", e => { e.preventDefault(); gmSubmit(); });
  document.addEventListener("click", e => { const u = e.target.closest("[data-undo]"); if (u && gmToastUndo.fn) { const fn = gmToastUndo.fn; gmToastUndo.fn = null; $("#toast").className = "toast"; fn(); } });
})();
