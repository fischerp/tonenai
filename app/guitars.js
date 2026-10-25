/* TonenAI guitar illustrations: original vector artwork.
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

  // Rounded polygon: each vertex [x, y, radius] becomes a smooth quadratic corner.
  function rpoly(vs) {
    const n = vs.length, pt = i => vs[(i + n) % n], d = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
    const near = (a, b, r) => { const l = d(a, b), k = Math.min(r, l / 2) / l; return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k]; };
    const q = v => Math.round(v * 100) / 100;
    let out = "";
    for (let i = 0; i < n; i++) {
      const p = pt(i), r = p[2] || 0, a = near(p, pt(i - 1), r), b = near(p, pt(i + 1), r);
      out += (i ? "L" : "M") + q(a[0]) + "," + q(a[1]) + (r ? "Q" + p[0] + "," + p[1] + " " + q(b[0]) + "," + q(b[1]) : "");
    }
    return out + "Z";
  }
  // Hand-drawn outlines for the models without a usable reference photo.
  const LES_PAUL = "M87.0,197.0C86.1,197.1 78.3,197.0 74.0,198.0C69.6,199.0 64.9,200.9 61.0,203.0C57.3,205.0 53.6,207.3 51.0,210.0C48.7,212.4 47.2,215.1 46.0,218.0C44.8,221.0 44.3,224.8 44.0,228.0C43.7,230.8 43.7,233.4 44.0,236.0C44.3,238.7 45.2,241.4 46.0,244.0C46.8,246.7 47.9,249.4 49.0,252.0C50.2,254.7 52.0,257.3 53.0,260.0C54.0,262.6 55.0,265.3 55.0,268.0C55.0,270.9 54.3,274.2 53.0,277.0C51.7,279.9 49.2,282.5 47.0,285.0C44.8,287.5 42.3,289.5 40.0,292.0C37.6,294.5 35.2,297.2 33.0,300.0C30.8,302.8 28.7,305.9 27.0,309.0C25.4,311.9 24.0,314.9 23.0,318.0C22.0,321.2 21.5,324.5 21.0,328.0C20.5,331.8 20.2,336.0 20.0,340.0C19.8,344.0 19.8,348.2 20.0,352.0C20.2,355.5 20.2,358.7 21.0,362.0C21.8,365.4 23.3,368.8 25.0,372.0C26.7,375.2 28.7,378.2 31.0,381.0C33.4,383.9 36.0,386.6 39.0,389.0C42.3,391.6 46.0,394.2 50.0,396.0C54.3,397.9 59.0,398.9 64.0,400.0C69.6,401.2 76.0,402.0 82.0,402.5C88.0,403.0 94.0,403.0 100.0,403.0C106.0,403.0 112.0,403.0 118.0,402.5C124.0,402.0 130.4,401.2 136.0,400.0C141.0,398.9 145.7,397.9 150.0,396.0C154.0,394.2 157.7,391.6 161.0,389.0C164.0,386.6 166.6,383.9 169.0,381.0C171.3,378.2 173.3,375.2 175.0,372.0C176.7,368.8 178.2,365.4 179.0,362.0C179.8,358.7 179.8,355.5 180.0,352.0C180.2,348.2 180.2,344.0 180.0,340.0C179.8,336.0 179.5,331.8 179.0,328.0C178.5,324.5 178.0,321.2 177.0,318.0C176.0,314.9 174.6,311.9 173.0,309.0C171.3,305.9 169.2,302.8 167.0,300.0C164.8,297.2 162.4,294.5 160.0,292.0C157.7,289.5 155.2,287.5 153.0,285.0C150.8,282.5 148.3,279.9 147.0,277.0C145.7,274.2 145.0,270.9 145.0,268.0C145.0,265.3 146.0,262.6 147.0,260.0C148.0,257.3 149.8,254.7 151.0,252.0C152.1,249.4 153.2,246.7 154.0,244.0C154.8,241.4 155.5,238.7 156.0,236.0C156.5,233.3 157.2,230.5 157.0,228.0C156.8,225.8 156.3,223.5 155.0,222.0C153.7,220.5 151.0,219.0 149.0,219.0C147.0,219.0 144.7,220.6 143.0,222.0C141.4,223.3 140.6,225.5 139.0,227.0C137.3,228.6 135.1,230.4 133.0,231.0C131.1,231.6 128.8,231.6 127.0,231.0C125.1,230.3 123.5,228.6 122.0,227.0C120.4,225.3 119.2,223.3 118.0,221.0C116.5,218.4 114.8,215.3 114.0,212.0C113.1,208.4 113.1,202.7 113.0,200.0C112.9,198.7 113.0,197.4 113.0,197.0Z";

  // Pickup spec: [type, cx, cy, cover]  ·  bridge: [type, cy, tail cy]  ·  knob: [x, y, colour]
  const S = {
    Stratocaster: { shape: "strat", fin: "sunburst", hs: "fender", fb: "maple", inlay: "dots", pgc: "#f6f2e6",
      pups: [["single", 100, 215], ["single", 100, 239], ["single", 100, 263]], bridge: ["strat", 285], knobs: [[126, 271, "cream"], [144, 278, "cream"], [158, 289, "cream"]], sw: [148, 263] },
    Telecaster: { shape: "tele", fin: "butter", hs: "fender", fb: "maple", inlay: "dots", pgc: "#1c1c20",
      pups: [["telecover", 100, 213]], bridge: ["tele", 274], knobs: [[143, 301, "chrome"], [143, 315, "chrome"]], sw: [143, 283], plate: [136, 272, 14, 52] },
    "Les Paul": { path: LES_PAUL, bottom: 403, fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "trapezoid",
      pups: [["hum", 100, 246, "cream"], ["hum", 100, 295, "cream"]], bridge: ["tom", 314, 334], knobs: [[155, 326, "gold"], [133, 339, "gold"], [154, 354, "gold"], [133, 368, "gold"]], sw: [64, 226] },
    SG: { shape: "sg", fin: "cherry", hs: "gibson", fb: "rosewood", inlay: "dots",
      pups: [["hum", 100, 213, "chrome"], ["hum", 100, 249, "chrome"]], bridge: ["tom", 264, 281], knobs: [[56, 286, "black"], [52, 302, "black"], [70, 310, "black"], [86, 314, "black"]], sw: [62, 222] },
    "ES-335 / semi-hollow": { shape: "es335", neckExtra: 50, fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "blocks",
      pups: [["hum", 100, 254, "chrome"], ["hum", 100, 302, "chrome"]], bridge: ["tom", 324, 342], knobs: [[160, 366, "gold"], [136, 380, "gold"], [166, 394, "gold"], [142, 404, "gold"]], sw: [128, 349],
      fholes: [[41, 300, 33, 340], [159, 300, 167, 340]] },
    "Jazzmaster / Jaguar": { shape: "jazz", fin: "sunburst", hs: "fender", fb: "rosewood", inlay: "dots",
      pups: [["wide", 100, 240], ["wide", 100, 285]], bridge: ["offset", 297, 342], knobs: [[158, 322, "chrome"], [166, 334, "chrome"]], sw: null, plate: [54, 222, 24, 40], ctrl: true },
    Explorer: { shape: "explorer", fin: "cherry", hs: "gibson", fb: "rosewood", inlay: "dots", pgc: "#f4f0e2", dx: -9,
      pups: [["hum", 97, 217, "chrome"], ["hum", 99, 263, "chrome"]], bridge: ["tom", 284, 301], knobs: [[147, 301, "black"], [138, 322, "black"], [129, 343, "black"]], sw: null },
    "PRS / Superstrat": { shape: "prs", fin: "cherryburst", hs: "prs", fb: "rosewood", bind: "#0006", inlay: "birds",
      pups: [["hum", 100, 215, "cream"], ["hum", 100, 254, "cream"]], bridge: ["hardtail", 279], knobs: [[126, 309, "gold"]], sw: [127, 336] },
    "P-90 guitar (Junior, Special…)": { path: LES_PAUL, bottom: 403, fin: "tv", hs: "gibson", fb: "rosewood", bind: "#f8f2d8", inlay: "dots",
      pups: [["p90", 100, 246], ["p90", 100, 295]], bridge: ["wrap", 316], knobs: [[150, 340, "black"], [132, 356, "black"]], sw: null },
    Dreadnought: { shape: "dread", acoustic: true, ac: { f: 0.31, r: 22, br: 0.62, pg: true, bridge: "pin" }, fin: "natural", hs: "martin", fb: "rosewood", inlay: "dots", bind: "#efe4c8" },
    "Concert / OM": { shape: "folk", acoustic: true, ac: { f: 0.33, r: 21, br: 0.62, pg: true, bridge: "pin" }, fin: "natural", hs: "martin", fb: "rosewood", inlay: "dots", bind: "#efe4c8" },
    Classical: { shape: "classical", acoustic: true, ac: { f: 0.30, r: 19, br: 0.70, pg: false, bridge: "tie", rose: "ornate" }, fin: "natural", hs: "classic", fb: "rosewood", inlay: "none", bind: "#6b4a2a", neckW: [11.6, 14.2], nylon: true },
    Other: { shape: "prs", fin: "grey", hs: "fender", fb: "rosewood", inlay: "dots",
      pups: [["hum", 100, 215, "chrome"], ["hum", 100, 254, "chrome"]], bridge: ["hardtail", 279], knobs: [[126, 309, "black"]], sw: null },
  };
  const FLYING_V_PATH = rpoly([[88, 204, 0], [5, 441, 8], [36, 452, 8], [100, 320, 6], [164, 452, 8], [195, 441, 8], [112, 204, 0]]);
  const FLYING_V = { path: FLYING_V_PATH, bottom: 452, fin: "honey", hs: "gibson", fb: "rosewood", inlay: "dots",
    pups: [["hum", 100, 218, "chrome"], ["hum", 100, 278, "chrome"]], bridge: ["tom", 298, 306], knobs: [[124, 317, "black"], [137, 346, "black"], [151, 376, "black"]], sw: null };

  S["Flying V"] = FLYING_V; S.Acoustic = S.Dreadnought;      // old saved guitars used the single "Acoustic" type
  const LEGACY_EXPLORER = "Explorer / Flying V";   // old saved guitars used one combined type

  // Real proportions: [scale length, nut -> body-join distance, headstock length] in canvas units (1 unit = 2.2 mm).
  const LAY = { Stratocaster: [291.6, 185, 80], Telecaster: [291.6, 185, 80], "Les Paul": [283, 172, 76], SG: [283, 188, 76], "ES-335 / semi-hollow": [283, 190, 76],
    "Jazzmaster / Jaguar": [291.6, 185, 80], Explorer: [283, 172, 76], "Flying V": [283, 172, 76], "PRS / Superstrat": [286, 175, 76],
    "P-90 guitar (Junior, Special…)": [283, 172, 76], Dreadnought: [290, 161, 76], "Concert / OM": [286, 161, 76], Classical: [292.5, 146, 80], Acoustic: [290, 161, 76], Other: [286, 175, 76] };
  Object.keys(S).forEach(k => { S[k].lay = LAY[k] || LAY.Other; }); FLYING_V.lay = LAY["Flying V"];

  let uid = 0;
  const f = n => Math.round(n * 100) / 100;
  const fretAt = (L, n) => L.nut + 2 + L.sl * (1 - Math.pow(2, -n / 12));
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

  function headstock(kind, hid) {
    const wood = "#4a2f1d", edge = "#1c1109";
    const tuner = (x, y, side, col = "#d6d8de", stroke = "#6c6f78") => {      // button outside the edge, post inside
      const bx = side < 0 ? x - 5.5 : x + 1.5;
      return `<circle cx="${x}" cy="${y}" r="1.5" fill="#c9ccd4" stroke="${stroke}" stroke-width=".4"/><rect x="${bx}" y="${y - 2.4}" width="4.6" height="4.8" rx="2" fill="${col}" stroke="${stroke}" stroke-width=".4"/>`;
    };
    const gloss = d => `<path d="${d}" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width="1.2" clip-path="url(#${hid})"/>`;
    if (kind === "fender") {
      const d = "M91.4,70 L91.4,54 C91.4,47 86,43 82,37 C77.5,30 77.5,17 84,10.5 C89,5.5 98,3.5 104,5 C110,6.5 113.5,11.5 111.5,19 C109.5,26 108.6,34 108.6,46 L108.6,70 Z";
      const posts = [[85, 13], [80, 20], [79.5, 28], [82, 35.5], [86.5, 41.5], [91, 48]];
      return `<defs><clipPath id="${hid}"><path d="${d}"/></clipPath></defs><path d="${d}" fill="${wood}" stroke="${edge}" stroke-width=".9" stroke-linejoin="round"/>${gloss(d)}
        <path d="M106,8 C110,10 110,16 108,22" fill="none" stroke="#fff" stroke-opacity=".12" stroke-width="1"/>${posts.map(([x, y]) => tuner(x, y, -1)).join("")}<rect x="96" y="58" width="8" height="2.2" rx="1" fill="#c9ccd4" opacity=".85"/>`;
    }
    if (kind === "prs") {
      const d = "M91,70 L90,54 C84,45 82,26 86,13 C89,5 95,2.5 100,2.5 C105,2.5 111,5 114,13 C118,26 116,45 110,54 L109,70 Z";
      const ys = [17, 29, 41];
      return `<defs><clipPath id="${hid}"><path d="${d}"/></clipPath></defs><path d="${d}" fill="${wood}" stroke="${edge}" stroke-width=".9" stroke-linejoin="round"/>${gloss(d)}
        ${ys.map(y => tuner(90.6, y, -1, "#e0cd85", "#7a6620") + tuner(109.4, y, 1, "#e0cd85", "#7a6620")).join("")}<path d="M96,22 q4,-6 8,0 q-4,2 -4,6 q0,-4 -4,-6 Z" fill="#efe6c8" opacity=".9"/>`;
    }
    if (kind === "martin") {
      const d = "M89,70 L89,60 L83,52 L83,17 Q83,8 92,8 L108,8 Q117,8 117,17 L117,52 L111,60 L111,70 Z", ys = [16, 29, 42];
      return `<defs><clipPath id="${hid}"><path d="${d}"/></clipPath></defs><path d="${d}" fill="#17110c" stroke="#000" stroke-width=".9" stroke-linejoin="round"/>${gloss(d)}
        ${ys.map(y => tuner(83.4, y, -1, "#d9dce2", "#6c6f78") + tuner(116.6, y, 1, "#d9dce2", "#6c6f78")).join("")}<path d="M100,20 l4,6 l-4,6 l-4,-6 Z" fill="#efe6c8" opacity=".9"/>`;
    }
    if (kind === "classic") {
      const d = "M88,70 L88,57 C85,53 83,48 83,40 L83,15 Q83,8 89,8 L111,8 Q117,8 117,15 L117,40 C117,48 115,53 112,57 L112,70 Z", ys = [15, 28, 41];
      return `<defs><clipPath id="${hid}"><path d="${d}"/></clipPath></defs><path d="${d}" fill="#7a4a24" stroke="#2a170a" stroke-width=".9" stroke-linejoin="round"/>${gloss(d)}
        <rect x="90.5" y="14" width="4" height="36" rx="2" fill="#241208"/><rect x="105.5" y="14" width="4" height="36" rx="2" fill="#241208"/>
        ${ys.map(y => `<rect x="77" y="${y - 2.4}" width="7" height="4.8" rx="2.2" fill="#eee7d4" stroke="#8a7a5a" stroke-width=".4"/><rect x="116" y="${y - 2.4}" width="7" height="4.8" rx="2.2" fill="#eee7d4" stroke="#8a7a5a" stroke-width=".4"/>`).join("")}`;
    }
    const d = "M88,70 L88.4,58 C85.5,52 80,48 78,38 C76,27 79,16 88,9.5 C94,5.5 106,5.5 112,9.5 C121,16 124,27 122,38 C120,48 114.5,52 111.6,58 L112,70 Z";   // gibson "open book"
    const ys = [15, 28, 41];
    return `<defs><clipPath id="${hid}"><path d="${d}"/></clipPath></defs><path d="${d}" fill="#15100c" stroke="#000" stroke-width=".9" stroke-linejoin="round"/>${gloss(d)}
      ${ys.map(y => tuner(86.6, y + 1, -1, "#e5d27c", "#8a7424") + tuner(113.4, y + 1, 1, "#e5d27c", "#8a7424")).join("")}
      <path d="M95,33 L96.2,25 L98.2,28 L100,22 L101.8,28 L103.8,25 L105,33 Z" fill="#efe6c8" opacity=".92"/><path d="M97,38 h6" stroke="#efe6c8" stroke-width="1" opacity=".7"/>`;
  }

  function inlays(style, fb, L) {
    if (style === "none") return "";
    const c = fb === "maple" ? "#2a2018" : "#efe6c8", out = [];
    const one = (y, dx) => style === "blocks" ? `<rect x="${96.5 + dx}" y="${f(y - 3)}" width="7" height="6" rx="1" fill="${c}"/>`
      : style === "trapezoid" ? `<path d="M${96.5} ${f(y - 3)} L103.5 ${f(y - 3)} L102.6 ${f(y + 3)} L97.4 ${f(y + 3)} Z" fill="${c}"/>`
        : style === "birds" ? `<path d="M96,${f(y)} q4,-5 8,0 q-4,2 -4,5 q0,-3 -4,-5 Z" fill="${c}"/>` : `<circle cx="${100 + dx}" cy="${f(y)}" r="1.9" fill="${c}"/>`;
    const mid = n => (fretAt(L, n - 1) + fretAt(L, n)) / 2, ok = n => mid(n) < L.join - 4;
    [3, 5, 7, 9, 15, 17, 19, 21].filter(ok).forEach(n => out.push(one(mid(n), 0)));
    if (ok(12)) { const y12 = mid(12); if (style === "dots") out.push(one(y12, -3.6), one(y12, 3.6)); else out.push(one(y12, 0)); }
    return out.join("");
  }

  window.guitarSVG = function (kind, name, finish) {
    let g = S[kind] || S.Other;
    if (kind === LEGACY_EXPLORER) g = /flying|\bv\b/i.test(name || "") ? FLYING_V : S.Explorer;
    const shp = g.shape ? SH[g.shape] : null;
    const body = g.path || (shp && shp.body) || ACOUSTIC;
    const bottom = g.bottom || (shp && shp.bottom) || 400;
    const lay = g.lay || LAY.Other, hsLen = lay[2];
    const L = { sl: lay[0], nut: 6 + hsLen }; L.join = L.nut + lay[1]; const dy = L.join - NECK_END;     // body artwork is drawn with its join at y=196
    const H = Math.ceil(bottom + dy + 12);
    const acTop = 196, acLen = bottom - acTop, acR = g.ac ? g.ac.r : 0;
    const acSoundY = g.ac ? acTop + g.ac.f * acLen : 0, acBridgeY = g.ac ? acTop + g.ac.br * acLen : 0;
    const id = "g" + (++uid), m = id + "m", fin = FIN[finish] || FIN[g.fin];
    const bodyFill = fin.burst
      ? `<radialGradient id="${id}b" cx=".5" cy=".6" r=".65"><stop offset="0" stop-color="${fin.burst[0]}"/><stop offset=".5" stop-color="${fin.burst[1]}"/><stop offset="1" stop-color="${fin.burst[2]}"/></radialGradient>`
      : `<linearGradient id="${id}b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${fin.solid[0]}"/><stop offset="1" stop-color="${fin.solid[1]}"/></linearGradient>`;
    const sheen = `<linearGradient id="${id}s" x1=".1" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".4"/><stop offset=".3" stop-color="#fff" stop-opacity=".06"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".34"/></linearGradient>`;
    const strings = Array.from({ length: 6 }, (_, i) => {
      const by = g.bridge ? g.bridge[1] + (g.bridge[0] === "strat" ? 2 : g.bridge[0] === "tele" ? 6 : g.bridge[0] === "hardtail" ? 4 : 0) : (g.acoustic ? acBridgeY : bottom - 80);
      return `<line x1="${f(100 + (i - 2.5) * 3.1)}" y1="${f(L.nut + 1 - dy)}" x2="${f(100 + (i - 2.5) * (g.acoustic ? 5.2 : 5.4))}" y2="${by}" stroke="${g.nylon ? (i < 3 ? "#f1efe6" : "#d9b86a") : "#e7e8ec"}" stroke-width="${f(.55 + (5 - i) * .08)}" stroke-opacity=".85"/>`;
    }).join("");
    const halfAt = y => (g.neckW ? g.neckW[0] : 9) + ((g.neckW ? g.neckW[1] : 12.6) - (g.neckW ? g.neckW[0] : 9)) * (y - L.nut) / (L.join - L.nut);
    const frets = Array.from({ length: 22 }, (_, i) => i + 1).filter(n => fretAt(L, n) < L.join - 2).map(n => { const y = fretAt(L, n), h = halfAt(y) - 1.4; return `<line x1="${f(100 - h)}" x2="${f(100 + h)}" y1="${f(y)}" y2="${f(y)}" stroke="#d9dbe0" stroke-width=".9"/>`; }).join("");
    const fbCol = g.fb === "maple" ? "#e9c98b" : "#3a2013";
    const [nutH, joinH] = g.neckW || [9, 12.6], span = (nEndOf() - L.nut) / (L.join - L.nut);
    function nEndOf() { return L.join + 8 + (g.neckExtra || 0); }
    const nEnd = nEndOf(), nh = nutH + (joinH - nutH) * span, fh2 = (nutH - 1.4) + ((joinH - 1.6) - (nutH - 1.4)) * span;
    const neckPoly = `M${100 - nutH},${L.nut} L${100 + nutH},${L.nut} L${f(100 + nh)},${nEnd} L${f(100 - nh)},${nEnd} Z`;
    const fbPoly = `M${100 - (nutH - 1.4)},${L.nut + 2} L${100 + (nutH - 1.4)},${L.nut + 2} L${f(100 + fh2)},${nEnd} L${f(100 - fh2)},${nEnd} Z`;
    const pgPath = g.pgc && shp && shp.pg ? `<g clip-path="url(#${id}c)"><path d="${shp.pg}" fill="${g.pgc}"/><path d="${shp.pg}" fill="none" stroke="#000" stroke-opacity=".35" stroke-width=".8"/><path d="${shp.pg}" fill="none" stroke="#fff" stroke-opacity=".25" stroke-width="1.6" transform="translate(-.5,-.5)" clip-path="url(#${id}c)"/></g>` : "";
    const plate = g.plate ? (g.ctrl
      ? (([x, y, w, h]) => `<g transform="rotate(-9 ${x + w / 2} ${y + h / 2})"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${w / 2.6}" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5"/>
          ${[0, 1].map(i => `<rect x="${x + 4 + i * 8.5}" y="${y + 5}" width="3" height="${h * .42}" rx="1.5" fill="#1b1b1f"/><rect x="${x + 3.4 + i * 8.5}" y="${y + 8 + i * 6}" width="4.2" height="5" rx="1.5" fill="#f2f2f4" stroke="#6c6f78" stroke-width=".3"/>`).join("")}
          <circle cx="${x + w / 2}" cy="${y + h - 7.5}" r="3.6" fill="#5a5d66"/><circle cx="${x + w / 2}" cy="${y + h - 7.5}" r="1.6" fill="#c9ccd4"/></g>`)(g.plate)
      : `<rect x="${g.plate[0]}" y="${g.plate[1]}" width="${g.plate[2]}" height="${g.plate[3]}" rx="3" fill="url(#${m})" stroke="#6c6f78" stroke-width=".5" opacity=".95"/>`) : "";
    const fh = (g.fholes || []).map(([x1, y1, x2, y2]) => `<path d="M${x1},${y1} q${(x2 - x1) / 2 + (x2 > x1 ? -3 : 3)},${(y2 - y1) / 2} ${x2 - x1},${y2 - y1}" fill="none" stroke="#0a0a0a" stroke-width="3.4" stroke-linecap="round"/>`).join("");
    const tear = (cx, cy, r) => `M${f(cx + r * .55)},${f(cy - r * .35)} C${f(cx + r * 1.7)},${f(cy - r * .55)} ${f(cx + r * 2.5)},${f(cy + r * .5)} ${f(cx + r * 2.35)},${f(cy + r * 1.9)} C${f(cx + r * 2.2)},${f(cy + r * 3.1)} ${f(cx + r * .9)},${f(cy + r * 3.3)} ${f(cx + r * .8)},${f(cy + r * 2.2)} C${f(cx + r * .72)},${f(cy + r * 1.3)} ${f(cx + r * .3)},${f(cy + r * .7)} ${f(cx + r * .55)},${f(cy - r * .35)} Z`;
    const ac = g.acoustic ? `${g.ac.pg ? `<path d="${tear(100, acSoundY, acR * 0.88)}" fill="#3a1a12"/><path d="${tear(100, acSoundY, acR * 0.88)}" fill="none" stroke="#8a4a2e" stroke-opacity=".7" stroke-width=".8"/>` : ""}
        <circle cx="100" cy="${f(acSoundY)}" r="${acR + 2.6}" fill="#efe4c8"/><circle cx="100" cy="${f(acSoundY)}" r="${acR + 1.4}" fill="#1a110a"/>${g.ac.rose === "ornate" ? `<circle cx="100" cy="${f(acSoundY)}" r="${acR + 6.5}" fill="none" stroke="#6b4a2a" stroke-width="4.2"/><circle cx="100" cy="${f(acSoundY)}" r="${acR + 6.5}" fill="none" stroke="#efe4c8" stroke-width="2.4" stroke-dasharray="2.2 2.2"/>` : `<circle cx="100" cy="${f(acSoundY)}" r="${acR + 4.2}" fill="none" stroke="#2a1b10" stroke-width="1.1"/>`}
        <circle cx="100" cy="${f(acSoundY)}" r="${acR}" fill="#040302"/><circle cx="100" cy="${f(acSoundY)}" r="${acR}" fill="none" stroke="#000" stroke-width="1"/>
        ${g.ac.bridge === "tie"
        ? `<rect x="62" y="${f(acBridgeY - 6)}" width="76" height="14" rx="5" fill="#3a2414"/><rect x="66" y="${f(acBridgeY - 3)}" width="68" height="3" rx="1.5" fill="#efe4c8"/>${[0, 1, 2, 3, 4, 5].map(i => `<ellipse cx="${72 + i * 11.2}" cy="${f(acBridgeY + 4)}" rx="3.2" ry="1.6" fill="#241608"/>`).join("")}`
        : `<path d="M54,${f(acBridgeY - 7)} Q100,${f(acBridgeY - 12)} 146,${f(acBridgeY - 7)} Q151,${f(acBridgeY - 1)} 146,${f(acBridgeY + 6)} Q100,${f(acBridgeY + 9)} 54,${f(acBridgeY + 6)} Q49,${f(acBridgeY - 1)} 54,${f(acBridgeY - 7)} Z" fill="#2b1810"/><rect x="62" y="${f(acBridgeY - 1.5)}" width="76" height="3" rx="1.5" fill="#efe4c8"/>${[0, 1, 2, 3, 4, 5].map(i => `<circle cx="${f(100 + (i - 2.5) * 7.6)}" cy="${f(acBridgeY - 5)}" r="1.5" fill="#efe4c8"/>`).join("")}`}` : "";
    const sw = g.sw ? `<g><rect x="${g.sw[0] - 6}" y="${g.sw[1] - 2}" width="12" height="4" rx="2" fill="#c9ccd4" stroke="#6c6f78" stroke-width=".4"/><rect x="${g.sw[0] - 1.5}" y="${g.sw[1] - 3.5}" width="3.4" height="7" rx="1.4" fill="#f2f2f4"/></g>` : "";
    const bind = g.bind ? `<path d="${body}" fill="none" stroke="${g.bind}" stroke-width="2.6" stroke-linejoin="round"/>` : "";
    return `<svg viewBox="0 0 200 ${H}" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>${bodyFill}${sheen}${metal(m)}<clipPath id="${id}c"><path d="${body}"/></clipPath><filter id="${id}f" x="-20%" y="-10%" width="140%" height="130%"><feGaussianBlur stdDeviation="3"/></filter>
        <linearGradient id="${id}e" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".6"/><stop offset=".45" stop-color="#fff" stop-opacity=".05"/><stop offset=".55" stop-color="#000" stop-opacity=".05"/><stop offset="1" stop-color="#000" stop-opacity=".5"/></linearGradient>
        <linearGradient id="${id}n" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".35"/><stop offset=".3" stop-color="#fff" stop-opacity=".1"/><stop offset="1" stop-color="#000" stop-opacity=".4"/></linearGradient></defs>
      <g transform="translate(${g.dx || 0},0)">
      <g transform="translate(0,${f(L.nut)}) scale(1,${f(hsLen / (NECK_TOP - 6))}) translate(0,${-NECK_TOP})">${headstock(g.hs, id + "h")}</g>
      <path d="${neckPoly}" fill="${g.fb === "maple" ? "#d8b176" : "#5a3a22"}"/><path d="${fbPoly}" fill="${fbCol}"/><path d="${fbPoly}" fill="url(#${id}n)"/>
      ${frets}${inlays(g.inlay, g.fb, L)}<rect x="${100 - 7.6}" y="${L.nut}" width="15.2" height="3" rx="1" fill="#f2ead0"/>
      <g transform="translate(0,${f(dy)})">
      <g transform="translate(5,7)" opacity=".38" filter="url(#${id}f)"><path d="${body}" fill="#000"/></g>
      <path d="${body}" fill="url(#${id}b)"/><g clip-path="url(#${id}c)"><path d="${body}" fill="url(#${id}s)"/></g>
      ${bind}<g clip-path="url(#${id}c)"><path d="${body}" fill="none" stroke="url(#${id}e)" stroke-width="5" stroke-linejoin="round"/></g>
      <path d="${body}" fill="none" stroke="#000" stroke-opacity=".6" stroke-width=".9" stroke-linejoin="round"/>
      ${pgPath}${plate}${fh}${ac}${g.acoustic ? "" : (g.pups || []).map(p => pickup(p, m)).join("")}
      ${strings}${g.bridge ? bridge(g.bridge, m) : ""}${(g.knobs || []).map(knob).join("")}${sw}</g></g></svg>`;
  };

  window.guitarPic = function (kind, name, finish) { return `<span class="gpic">${window.guitarSVG(kind, name, finish)}</span>`; };
})();
