/* Guitar tabs: open the song on the tab site you already use (search link or an exact link you paste once). */
let TABCFG = { service: "", template: "", links: {} };
const TAB_SERVICES = {
  "ultimate-guitar": ["Ultimate Guitar", "https://www.ultimate-guitar.com/search.php?search_type=title&value={q}"],
  songsterr: ["Songsterr", "https://www.songsterr.com/?pattern={q}"],
  google: ["Google (tabs)", "https://www.google.com/search?q={q}+guitar+tab"],
  custom: ["Another site…", ""],
};
const tabQuery = s => encodeURIComponent((s.artist ? s.artist + " " : "") + s.title.replace(/\s*\(.*?\)/g, m => /live|remaster|acoustic|version/i.test(m) ? "" : m));
function tabUrl(s) {
  if (TABCFG.links[s.id]) return TABCFG.links[s.id];
  const tpl = TABCFG.service === "custom" ? TABCFG.template : (TAB_SERVICES[TABCFG.service] || [])[1];
  return tpl ? tpl.replace("{q}", tabQuery(s)) : "";
}
async function loadTabCfg() { try { TABCFG = await api().tabs_config(); TABCFG.links = TABCFG.links || {}; } catch (e) {} }
async function openTabs(id) {
  const s = SONGS.find(x => x.id === id); if (!s) return;
  const url = tabUrl(s);
  if (!url) return tabSettings(id);
  const r = await api().open_url(url); if (!r.ok) toast(r.error, true);
}
function tabSettings(id) {
  const s = SONGS.find(x => x.id === id); if (!s) return;
  const ov = document.createElement("div"); ov.className = "ls-ov"; ov.id = "tabov"; ov.style.position = "fixed"; ov.style.zIndex = 80;
  const cur = TABCFG.service || "";
  ov.innerHTML = `<div class="ls-card" style="max-width:520px"><h4>${tx("Guitar tabs")}</h4><div class="sub">${tx("Pick the site you use. “Tabs” will search it for each song. You can also paste an exact link for this song.")}</div>
    <div style="padding:0 20px 8px;display:grid;gap:8px">${Object.keys(TAB_SERVICES).map(k => `<label style="display:flex;gap:10px;align-items:center;padding:8px 10px;border-radius:8px;background:#222230;cursor:pointer"><input type="radio" name="tabsvc" value="${k}" ${cur === k ? "checked" : ""}> ${esc(tx(TAB_SERVICES[k][0]))}</label>`).join("")}
      <input class="q" id="tab-tpl" style="margin:0;display:${cur === "custom" ? "block" : "none"}" placeholder="${esc(tx("Search URL with {q}, e.g. https://example.com/search?q={q}"))}" value="${esc(TABCFG.template || "")}">
      <div class="sub" style="padding:6px 0 0">${tx("Exact link for “{0}” (optional)", esc(s.title))}</div>
      <input class="q" id="tab-link" style="margin:0" placeholder="https://…" value="${esc(TABCFG.links[s.id] || "")}"></div>
    <div class="acts"><button class="p" data-tabcancel="1">${tx("Cancel")}</button><button class="p main" data-tabsave="${esc(id)}">${tx("Save and open")}</button></div></div>`;
  document.body.appendChild(ov);
}
document.addEventListener("change", e => { if (e.target.name === "tabsvc") { const t = document.getElementById("tab-tpl"); if (t) t.style.display = e.target.value === "custom" ? "block" : "none"; } });
/* YouTube guitar lessons: a search link, so nothing is downloaded and no API key is needed. */
async function openLessons(id) {
  const s = SONGS.find(x => x.id === id); if (!s) return;
  const r = await api().open_url("https://www.youtube.com/results?search_query=" + tabQuery(s) + "+guitar+lesson");
  if (!r.ok) toast(r.error, true);
}
document.addEventListener("click", async e => {
  const l = e.target.closest("[data-lessons-open]"); if (l) return openLessons(l.dataset.lessonsOpen);
  const o = e.target.closest("[data-tabs-open]"); if (o) return openTabs(o.dataset.tabsOpen);
  const g = e.target.closest("[data-tabs-set]"); if (g) return tabSettings(g.dataset.tabsSet);
  if (e.target.closest("[data-tabcancel]")) { const ov = document.getElementById("tabov"); if (ov) ov.remove(); return; }
  const sv = e.target.closest("[data-tabsave]");
  if (sv) {
    const id = sv.dataset.tabsave, svc = (document.querySelector('input[name="tabsvc"]:checked') || {}).value || "";
    const link = (document.getElementById("tab-link").value || "").trim(), tpl = (document.getElementById("tab-tpl").value || "").trim();
    if (!svc && !link) return toast(tx("Pick a site or paste a link"), true);
    if (svc === "custom" && !tpl.includes("{q}") && !link) return toast(tx("The search URL must contain {q}"), true);
    const links = { ...TABCFG.links }; if (link) links[id] = link; else delete links[id];
    const r = await api().save_tabs_config({ service: svc || TABCFG.service, template: tpl, links }); if (r.ok) TABCFG = r.config;
    document.getElementById("tabov").remove(); openTabs(id);
  }
});
