/* Tonai guitar illustrations: original vector artwork.
   Body outlines (and some pickguards) come from guitar-shapes.js, vector data traced from public photographs with
   tools/trace_guitars.py, so the silhouettes and proportions follow the real models. Everything else (finish, neck, hardware)
   is drawn here. Canvas: 200 wide; nut at y=70, neck/body join at y=196; height depends on the model. */
(function () {
  const NECK_TOP = 70, NECK_END = 196, SCALE_LEN = 248;
  const SH = window.GUITAR_SHAPES || {};

  const FIN = {
    sunburst: { burst: ["#f3c247", "#c8631a", "#3b0c05"] }, cherryburst: { burst: ["#e8892a", "#a8200f", "#2d0504"] }, honey: { burst: ["#f1b94a", "#d98a2a", "#8a4a12"] },
    green: { burst: ["#41a77f", "#1c6a4d", "#06251b"] }, butter: { solid: ["#f0cf7a", "#c79a3b"] }, cherry: { solid: ["#c32a24", "#6a0d0b"] }, black: { solid: ["#2a2a2e", "#08080a"] },
    red: { solid: ["#d23a2c", "#7a1710"] }, tv: { solid: ["#f2e3a2", "#cdb15a"] }, natural: { solid: ["#e9c98b", "#b9854a"] }, teal: { solid: ["#83cbc0", "#2f7f86"] },
    grey: { solid: ["#9a9aa8", "#4a4a56"] }, white: { solid: ["#faf8f2", "#cfcab8"] }, blue: { solid: ["#4f86e0", "#1b3a86"] },
  };
  window.GUITAR_FINISHES = [["sunburst", "Sunburst"], ["cherryburst", "Cherry sunburst"], ["honey", "Honey burst"], ["butter", "Butterscotch"], ["tv", "TV yellow"], ["natural", "Natural"],
    ["cherry", "Cherry"], ["red", "Red"], ["black", "Black"], ["white", "White"], ["blue", "Blue"], ["teal", "Seafoam"], ["green", "Green burst"], ["grey", "Grey"]]
    .map(([k, label]) => { const f = FIN[k], c = f.burst || f.solid; return { key: k, label, css: f.burst ? `radial-gradient(circle at 50% 60%, ${c[0]}, ${c[1]} 55%, ${c[2]})` : `linear-gradient(135deg, ${c[0]}, ${c[1]})` }; });

  // Hand-drawn outlines for the two models without a usable reference photo.
  const EXPLORER = "M87.4,200 L34.4,313 Q32,321 41,320 L183,268 Q191,265 187,257 L171.5,205 L198.5,181 Q201,178 196,177 L112.6,198 Z";
  const ACOUSTIC = "M100,196 C140,196 163.5,210 163.5,244 C163.5,272 157,282 157,298 C157,316 189,330 189,372 C189,408 150,425 100,425 C50,425 11,408 11,372 C11,330 43,316 43,298 C43,282 36.5,272 36.5,244 C36.5,210 60,196 100,196 Z";

  // Pickup spec: [type, cx, cy, cover]  ·  bridge: [type, cy, tail cy]  ·  knob: [x, y, colour]
  const S = {
    Stratocaster: { shape: "strat", fin: "sunburst", hs: "fender", fb: "maple", inlay: "dots", pgc: "#f6f2e6",
      pups: [["single", 100, 215], ["single", 100, 239], ["single", 100, 263]], bridge: ["strat", 285], knobs: [[126, 271, "cream"], [144, 278, "cream"], [158, 289, "cream"]], sw: [148, 263] },
    Telecaster: { shape: "tele", fin: "butter", hs: "fender", fb: "maple", inlay: "dots", pgc: "#1c1c20",
      pups: [["telecover", 100, 213]], bridge: ["tele", 274], knobs: [[143, 301, "chrome"], [143, 315, "chrome"]], sw: [143, 283], plate: [136, 272, 14, 52] },
    "Les Paul": { shape: "lp", fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "trapezoid",
      pups: [["hum", 100, 246, "cream"], ["hum", 100, 295, "cream"]], bridge: ["tom", 314, 334], knobs: [[155, 326, "gold"], [133, 339, "gold"], [154, 354, "gold"], [133, 368, "gold"]], sw: [64, 226] },
    SG: { shape: "sg", fin: "cherry", hs: "gibson", fb: "rosewood", inlay: "dots",
      pups: [["hum", 100, 213, "chrome"], ["hum", 100, 249, "chrome"]], bridge: ["tom", 264, 281], knobs: [[56, 286, "black"], [52, 302, "black"], [70, 310, "black"], [86, 314, "black"]], sw: [62, 222] },
    "ES-335 / semi-hollow": { shape: "es335", fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "blocks",
      pups: [["hum", 100, 254, "chrome"], ["hum", 100, 302, "chrome"]], bridge: ["tom", 324, 342], knobs: [[164, 354, "gold"], [139, 372, "gold"], [170, 384, "gold"], [144, 398, "gold"]], sw: [131, 349],
      fholes: [[36, 318, 29, 352], [152, 352, 160, 388]] },
    "Jazzmaster / Jaguar": { shape: "jazz", fin: "sunburst", hs: "fender", fb: "rosewood", inlay: "dots",
      pups: [["wide", 100, 240], ["wide", 100, 285]], bridge: ["offset", 297, 342], knobs: [[158, 322, "chrome"], [166, 334, "chrome"]], sw: null, plate: [48, 214, 28, 44] },
    "Explorer / Flying V": { path: EXPLORER, bottom: 322, fin: "black", hs: "gibson", fb: "rosewood", inlay: "dots",
      pups: [["hum", 100, 216, "chrome"], ["hum", 100, 240, "chrome"]], bridge: ["tom", 262, 276], knobs: [[110, 288, "gold"], [124, 280, "gold"]], sw: null },
    "PRS / Superstrat": { shape: "prs", fin: "cherryburst", hs: "prs", fb: "rosewood", bind: "#0006", inlay: "birds",
      pups: [["hum", 100, 215, "cream"], ["hum", 100, 254, "cream"]], bridge: ["hardtail", 279], knobs: [[126, 309, "gold"]], sw: [127, 336] },
    "P-90 guitar (Junior, Special…)": { shape: "lp", fin: "tv", hs: "gibson", fb: "rosewood", bind: "#f8f2d8", inlay: "dots",
      pups: [["p90", 100, 246], ["p90", 100, 295]], bridge: ["wrap", 316], knobs: [[150, 340, "black"], [132, 356, "black"]], sw: null },
    Acoustic: { path: ACOUSTIC, bottom: 425, acoustic: true, fin: "natural", hs: "gibson", fb: "rosewood", bind: "#2a1b10", inlay: "dots" },
    Other: { shape: "prs", fin: "grey", hs: "fender", fb: "rosewood", inlay: "dots",
      pups: [["hum", 100, 215, "chrome"], ["hum", 100, 254, "chrome"]], bridge: ["hardtail", 279], knobs: [[126, 309, "black"]], sw: null },
  };
  const FLYING_V_PATH = "M88,204 L8,436 Q6,446 16,448 L34,450 Q42,450 45,442 L100,318 L155,442 Q158,450 166,450 L184,448 Q194,446 192,436 L112,204 Z";
  const FLYING_V = { path: FLYING_V_PATH, bottom: 452, fin: "honey", hs: "gibson", fb: "rosewood", inlay: "dots",
    pups: [["hum", 100, 218, "chrome"], ["hum", 100, 278, "chrome"]], bridge: ["tom", 298, 306], knobs: [[124, 317, "black"], [137, 346, "black"], [151, 376, "black"]], sw: null };

  let uid = 0;
  const f = n => Math.round(n * 100) / 100;
  const half = y => 9 + (y - NECK_TOP) / (NECK_END - NECK_TOP) * 3.6;
  const fretY = n => NECK_TOP + 2 + SCALE_LEN * (1 - Math.pow(2, -n / 12));
  const metal = id => `<linearGradient id="${id}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#f4f4f6"/><stop offset=".45" stop-color="#b9bcc4"/><stop offset=".55" stop-color="#8d9099"/><stop offset="1" stop-color="#d9dbe0"/></linearGradient>`;
  const poles = (cx, cy, n, gap, c, r = 1) => Array.from({ length: n }, (_, i) => `<circle cx="${f(cx + (i - (n - 1) / 2) * gap)}" cy="${f(cy)}" r="${r}" fill="${c}"/>`).join("");

  function pickup([type, cx, cy, col], m) {
    const sh = (w, h, r) => `<rect x="${f(cx - w / 2 - 1.5)}" y="${f(cy - h / 2 - 1)}" width="${w + 3}" height="${h + 3}" rx="${r + 1}" fill="#000" opacity=".38"/>`;
    if (type === "single") return `<g>${sh(38, 8, 3.5)}<rect x="${cx - 19}" y="${cy - 4}" width="38" height="8" rx="3.6" fill="#f2efe4"/>${poles(cx, cy, 6, 5.6, "#8f929a", 1.15)}</g>`;
    if (type === "telecover") return `<g>${sh(34, 13, 2)}<rect x="${cx - 17}" y="${cy - 6.5}" width="34" height="13" rx="2" fill="url(#${m})"/><rect x="${cx - 17}" y="${cy - 6.5}" width="34" height="13" rx="2" fill="none" stroke="#6c6f78" stroke-width=".5"/></g>`;
    if (type === "wide") return `<g>${sh(36, 11, 3)}<rect x="${cx - 18}" y="${cy - 5.5}" width="36" height="11" rx="3" fill="url(#${m})"/><rect x="${cx - 15}" y="${cy - 3}" width="30" height="6" rx="2" fill="#e8e2cf"/>${poles(cx, cy, 6, 5, "#7c7e86", 1.1)}</g>`;
    if (type === "p90") return `<g>${sh(36, 15, 3)}<rect x="${cx - 18}" y="${cy - 7.5}" width="36" height="15" rx="3.4" fill="#efe6c8"/>${poles(cx, cy, 6, 5.2, "#7c7e86", 1.3)}<circle cx="${cx - 15}" cy="${cy}" r="1.3" fill="#8d9099"/><circle cx="${cx + 15}" cy="${cy}" r="1.3" fill="#8d9099"/></g>`;
    const w = 36, h = 19;      // humbucker
    const inner = col === "cream"
      ? `<rect x="${cx - w / 2}" y="${cy - h / 2}" width="${w}" height="${h}" rx="2" fill="#efe6c8"/><rect x="${cx - 16}" y="${cy - 8}" width="32" height="6" rx="1.2" fill="#d8cca6"/><rect x="${cx - 16}" y="${cy + 2}" width="32" height="6" rx="1.2" fill="#d8cca6"/>${poles(cx, cy - 5, 6, 5.4, "#7c7e86", 1.2)}${poles(cx, cy + 5, 6, 5.4, "#7c7e86", 1.2)}`
      : col === "black" ? `<rect x="${cx - w / 2}" y="${cy - h / 2}" width="${w}" height="${h}" rx="2" fill="#17171a"/>${poles(cx, cy - 5, 6, 5.4, "#8d9099", 1.2)}${poles(cx, cy + 5, 6, 5.4, "#8d9099", 1.2)}`
        : `<rect x="${cx - w / 2}" y="${cy - h / 2}" width="${w}" height="${h}" rx="2.2" fill="url(#${m})"/><rect x="${cx - w / 2}" y="${cy - h / 2}" width="${w}" height="${h}" rx="2.2" fill="none" stroke="#5f626b" stroke-width=".5"/><line x1="${cx - w / 2 + 1}" x2="${cx + w / 2 - 1}" y1="${cy}" y2="${cy}" stroke="#7d808a" stroke-width=".5"/>`;
    return `<g>${sh(w, h, 2.4)}${inner}</g>`;
  }

  function bridge([type, y, y2], m) {
    const sad = (cx, n, gap, yy, c, w = 2.8, h = 4) => Array.from({ length: n }, (_, i) => `<rect x="${f(cx + (i - (n - 1) / 2) * gap - w / 2)}" y="${f(yy)}" width="${w}" height="${h}" rx=".8" fill="${c}"/>`).join("");
    if (type === "strat") return `<g><path d="M82,${y - 9} h36 l-2,19 h-32 z" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5"/>${sad(100, 6, 5.6, y - 5, "#d0d3da")}<rect x="86" y="${y + 2}" width="28" height="2.4" fill="#7d808a"/><rect x="96" y="${y + 6}" width="8" height="10" rx="2" fill="#9ea1aa"/></g>`;
    if (type === "tele") return `<g><path d="M83,${y - 16} h34 l-1,32 h-32 z" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5"/>${sad(100, 3, 11, y - 8, "#d9dbe0", 5, 9)}</g>`;
    if (type === "tom") return `<g><rect x="80" y="${y - 2}" width="40" height="4.2" rx="1.6" fill="url(#${m})"/>${sad(100, 6, 6.6, y - 3, "#d0d3da", 3, 6)}<circle cx="78" cy="${y}" r="1.8" fill="#8d9099"/><circle cx="122" cy="${y}" r="1.8" fill="#8d9099"/>${y2 ? `<rect x="82" y="${y2 - 3}" width="36" height="6" rx="2.4" fill="url(#${m})" stroke="#6c6f78" stroke-width=".4"/>` : ""}</g>`;
    if (type === "wrap") return `<g><rect x="80" y="${y - 2}" width="40" height="5" rx="2.4" fill="url(#${m})"/>${sad(100, 6, 6.6, y - 3, "#d0d3da", 3, 6)}</g>`;
    if (type === "hardtail") return `<g><rect x="82" y="${y - 7}" width="36" height="14" rx="2" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5"/>${sad(100, 6, 5.6, y - 4, "#d0d3da", 2.8, 7)}</g>`;
    if (type === "offset") return `<g><rect x="82" y="${y - 3}" width="36" height="5" rx="2" fill="url(#${m})"/>${sad(100, 6, 5.8, y - 3, "#d9dbe0", 2.8, 6)}<ellipse cx="100" cy="${y2}" rx="22" ry="16" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5"/><rect x="90" y="${y2 + 2}" width="20" height="8" rx="1.5" fill="#8d9099"/></g>`;
    return "";
  }

  function knob([x, y, c]) {
    const g = { cream: ["#f4efe0", "#c9c2a8"], gold: ["#f1d56a", "#9a7518"], black: ["#3a3a40", "#08080a"], chrome: ["#f4f4f6", "#8d9099"] }[c] || ["#3a3a40", "#08080a"];
    const gid = `kn${++uid}`;
    return `<g><defs><radialGradient id="${gid}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="${g[0]}"/><stop offset="1" stop-color="${g[1]}"/></radialGradient></defs><circle cx="${x}" cy="${y + 1}" r="6.4" fill="#000" opacity=".35"/><circle cx="${x}" cy="${y}" r="6" fill="url(#${gid})"/><circle cx="${x}" cy="${y}" r="2" fill="#000" opacity=".16"/></g>`;
  }

  function headstock(kind) {
    const wood = "#4a2f1d", edge = "#24150b";
    if (kind === "fender") {
      const ys = [14, 22, 30, 38, 46, 54];
      return `<path d="M91.5,${NECK_TOP} L91.5,50 C86,40 84,22 90,9 C94,3 108,3 112,9 C118,22 114,40 108.5,50 L108.5,${NECK_TOP} Z" fill="${wood}" stroke="${edge}" stroke-width=".8"/>
        <path d="M93,66 L93,50 C89,40 88,24 92,12" fill="none" stroke="#fff" stroke-opacity=".12" stroke-width="1.4"/>
        ${ys.map(y => `<circle cx="96" cy="${y}" r="1.7" fill="#c9ccd4" stroke="#6c6f78" stroke-width=".4"/><rect x="108" y="${y - 2.2}" width="6" height="4.4" rx="1.8" fill="#d6d8de" stroke="#6c6f78" stroke-width=".4"/>`).join("")}`;
    }
    if (kind === "prs") {
      const ys = [18, 30, 42];
      return `<path d="M91,${NECK_TOP} L90,52 C84,42 82,24 86,12 C90,4 96,2 100,2 C104,2 110,4 114,12 C118,24 116,42 110,52 L109,${NECK_TOP} Z" fill="${wood}" stroke="${edge}" stroke-width=".8"/>
        ${ys.map(y => `<circle cx="92" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="80" y="${y - 2.3}" width="9" height="4.6" rx="2" fill="#d9c27a" stroke="#7a6620" stroke-width=".4"/><circle cx="108" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="111" y="${y - 2.3}" width="9" height="4.6" rx="2" fill="#d9c27a" stroke="#7a6620" stroke-width=".4"/>`).join("")}`;
    }
    const ys = [16, 30, 44];
    return `<path d="M88,${NECK_TOP} L87,56 C80,46 79,24 83,12 C90,5 110,5 117,12 C121,24 120,46 113,56 L112,${NECK_TOP} Z" fill="#17120e" stroke="#000" stroke-width=".8"/>
      <path d="M89,${NECK_TOP - 3} L88.5,56 C83,46 82,26 86,15" fill="none" stroke="#fff" stroke-opacity=".1" stroke-width="1.4"/>
      ${ys.map(y => `<circle cx="90" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="77" y="${y - 2.6}" width="11" height="5.2" rx="2.3" fill="#e5d27c" stroke="#8a7424" stroke-width=".4"/><circle cx="110" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="112" y="${y - 2.6}" width="11" height="5.2" rx="2.3" fill="#e5d27c" stroke="#8a7424" stroke-width=".4"/>`).join("")}`;
  }

  function inlays(style, fb) {
    const c = fb === "maple" ? "#2a2018" : "#efe6c8", out = [];
    const one = (y, dx) => style === "blocks" ? `<rect x="${96.5 + dx}" y="${f(y - 3)}" width="7" height="6" rx="1" fill="${c}"/>`
      : style === "trapezoid" ? `<path d="M${96.5} ${f(y - 3)} L103.5 ${f(y - 3)} L102.6 ${f(y + 3)} L97.4 ${f(y + 3)} Z" fill="${c}"/>`
        : style === "birds" ? `<path d="M96,${f(y)} q4,-5 8,0 q-4,2 -4,5 q0,-3 -4,-5 Z" fill="${c}"/>` : `<circle cx="${100 + dx}" cy="${f(y)}" r="1.9" fill="${c}"/>`;
    [3, 5, 7, 9].forEach(n => out.push(one((fretY(n - 1) + fretY(n)) / 2, 0)));
    const y12 = (fretY(11) + fretY(12)) / 2;
    if (style === "dots") out.push(one(y12, -3.6), one(y12, 3.6)); else out.push(one(y12, 0));
    return out.join("");
  }

  window.guitarSVG = function (kind, name, finish) {
    let g = S[kind] || S.Other;
    if (kind === "Explorer / Flying V" && /flying|\bv\b/i.test(name || "")) g = FLYING_V;
    const shp = g.shape ? SH[g.shape] : null;
    const body = g.path || (shp && shp.body) || ACOUSTIC;
    const bottom = g.bottom || (shp && shp.bottom) || 400;
    const H = Math.ceil(bottom + 12);
    const id = "g" + (++uid), m = id + "m", fin = FIN[finish] || FIN[g.fin];
    const bodyFill = fin.burst
      ? `<radialGradient id="${id}b" cx=".5" cy=".6" r=".65"><stop offset="0" stop-color="${fin.burst[0]}"/><stop offset=".5" stop-color="${fin.burst[1]}"/><stop offset="1" stop-color="${fin.burst[2]}"/></radialGradient>`
      : `<linearGradient id="${id}b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${fin.solid[0]}"/><stop offset="1" stop-color="${fin.solid[1]}"/></linearGradient>`;
    const sheen = `<linearGradient id="${id}s" x1=".1" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".4"/><stop offset=".3" stop-color="#fff" stop-opacity=".06"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".34"/></linearGradient>`;
    const strings = Array.from({ length: 6 }, (_, i) => {
      const by = g.bridge ? g.bridge[1] + (g.bridge[0] === "strat" ? 2 : g.bridge[0] === "tele" ? 6 : g.bridge[0] === "hardtail" ? 4 : 0) : (g.acoustic ? 361 : bottom - 80);
      return `<line x1="${f(100 + (i - 2.5) * 3.1)}" y1="${NECK_TOP + 1}" x2="${f(100 + (i - 2.5) * (g.acoustic ? 5.2 : 5.4))}" y2="${by}" stroke="#e7e8ec" stroke-width="${f(.55 + (5 - i) * .08)}" stroke-opacity=".8"/>`;
    }).join("");
    const frets = Array.from({ length: 12 }, (_, i) => { const y = fretY(i + 1), h = half(y) - 1.4; return `<line x1="${f(100 - h)}" x2="${f(100 + h)}" y1="${f(y)}" y2="${f(y)}" stroke="#d9dbe0" stroke-width=".9"/>`; }).join("");
    const fbCol = g.fb === "maple" ? "#e9c98b" : "#3a2013";
    const neckPoly = `M91,${NECK_TOP} L109,${NECK_TOP} L${100 + 12.6},${NECK_END + 8} L${100 - 12.6},${NECK_END + 8} Z`;
    const fbPoly = `M${100 - 7.6},${NECK_TOP + 2} L${100 + 7.6},${NECK_TOP + 2} L${100 + 11},${NECK_END + 8} L${100 - 11},${NECK_END + 8} Z`;
    const pgPath = g.pgc && shp && shp.pg ? `<g clip-path="url(#${id}c)"><path d="${shp.pg}" fill="${g.pgc}"/><path d="${shp.pg}" fill="none" stroke="#000" stroke-opacity=".28" stroke-width=".7"/></g>` : "";
    const plate = g.plate ? `<rect x="${g.plate[0]}" y="${g.plate[1]}" width="${g.plate[2]}" height="${g.plate[3]}" rx="3" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5" opacity=".95"/>` : "";
    const fh = (g.fholes || []).map(([x1, y1, x2, y2]) => `<path d="M${x1},${y1} q${(x2 - x1) / 2 + (x2 > x1 ? -3 : 3)},${(y2 - y1) / 2} ${x2 - x1},${y2 - y1}" fill="none" stroke="#0a0a0a" stroke-width="3.4" stroke-linecap="round"/>`).join("");
    const ac = g.acoustic ? `<circle cx="100" cy="250" r="23" fill="#2a1b10"/><circle cx="100" cy="250" r="20" fill="#050403"/><circle cx="100" cy="250" r="25.5" fill="none" stroke="#e8dcc0" stroke-width="1.2" opacity=".7"/>
        <path d="M116,262 C146,262 150,300 130,314 C112,326 98,300 104,282 C108,272 110,264 116,262 Z" fill="#2a1b10" opacity=".55"/>
        <rect x="76" y="355" width="48" height="12" rx="3.6" fill="#3a2414"/><rect x="82" y="358" width="36" height="3" rx="1.5" fill="#e8dcc0"/>${[0, 1, 2, 3, 4, 5].map(i => `<circle cx="${84 + i * 6.4}" cy="363.5" r="1.3" fill="#e8dcc0"/>`).join("")}` : "";
    const sw = g.sw ? `<g><rect x="${g.sw[0] - 6}" y="${g.sw[1] - 2}" width="12" height="4" rx="2" fill="#c9ccd4" stroke="#6c6f78" stroke-width=".4"/><rect x="${g.sw[0] - 1.5}" y="${g.sw[1] - 3.5}" width="3.4" height="7" rx="1.4" fill="#f2f2f4"/></g>` : "";
    const bind = g.bind ? `<path d="${body}" fill="none" stroke="${g.bind}" stroke-width="2.6" stroke-linejoin="round"/>` : "";
    return `<svg viewBox="0 0 200 ${H}" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>${bodyFill}${sheen}${metal(m)}<clipPath id="${id}c"><path d="${body}"/></clipPath><filter id="${id}f" x="-20%" y="-10%" width="140%" height="130%"><feGaussianBlur stdDeviation="3"/></filter>
        <linearGradient id="${id}n" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".35"/><stop offset=".3" stop-color="#fff" stop-opacity=".1"/><stop offset="1" stop-color="#000" stop-opacity=".4"/></linearGradient></defs>
      <g transform="translate(5,7)" opacity=".38" filter="url(#${id}f)"><path d="${body}" fill="#000"/></g>
      ${headstock(g.hs)}
      <path d="${neckPoly}" fill="${g.fb === "maple" ? "#d8b176" : "#5a3a22"}"/><path d="${fbPoly}" fill="${fbCol}"/><path d="${fbPoly}" fill="url(#${id}n)"/>
      ${frets}${inlays(g.inlay, g.fb)}<rect x="${100 - 7.6}" y="${NECK_TOP}" width="15.2" height="3" rx="1" fill="#f2ead0"/>
      <path d="${body}" fill="url(#${id}b)"/><g clip-path="url(#${id}c)"><path d="${body}" fill="url(#${id}s)"/></g>
      ${bind}<path d="${body}" fill="none" stroke="#000" stroke-opacity=".55" stroke-width=".8" stroke-linejoin="round"/>
      ${pgPath}${plate}${fh}${ac}${g.acoustic ? "" : (g.pups || []).map(p => pickup(p, m)).join("")}
      ${strings}${g.bridge ? bridge(g.bridge, m) : ""}${(g.knobs || []).map(knob).join("")}${sw}</svg>`;
  };

  window.guitarPic = function (kind, name, finish) { return `<span class="gpic">${window.guitarSVG(kind, name, finish)}</span>`; };
})();
