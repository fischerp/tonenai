/* Tonai guitar illustrations: original vector artwork (no photos), rendered from a small spec per model.
   guitarSVG(kind, name) -> <svg> string. Coordinates: body/hardware are drawn in a 100x270 "body space"
   and scaled x2 into the 200x540 canvas; neck, headstock and strings are drawn directly in canvas space. */
(function () {
  const NECK_TOP = 70, NECK_END = 196, SCALE_LEN = 248, SY = 1.55, BODY_TOP = 126;
  const absY = y => NECK_END + (y - BODY_TOP) * SY;     // body-space y -> canvas y
  const hY = y => absY(y) / 2;                           // same, inside the x2 hardware group

  // finishes: [centre, mid, edge] for a sunburst, or [solid, solid-dark]
  const FIN = {
    sunburst: { burst: ["#f3c247", "#c8631a", "#3b0c05"] },
    cherryburst: { burst: ["#e8892a", "#a8200f", "#2d0504"] },
    honey: { burst: ["#f1b94a", "#d98a2a", "#8a4a12"] },
    butter: { solid: ["#f0cf7a", "#c79a3b"] },
    cherry: { solid: ["#c32a24", "#6a0d0b"] },
    black: { solid: ["#2a2a2e", "#08080a"] },
    red: { solid: ["#d23a2c", "#7a1710"] },
    tv: { solid: ["#f2e3a2", "#cdb15a"] },
    natural: { solid: ["#e9c98b", "#b9854a"] },
    teal: { solid: ["#83cbc0", "#2f7f86"] },
    green: { burst: ["#41a77f", "#1c6a4d", "#06251b"] },
    grey: { solid: ["#9a9aa8", "#4a4a56"] },
    white: { solid: ["#faf8f2", "#cfcab8"] },
    blue: { solid: ["#4f86e0", "#1b3a86"] },
  };
  window.GUITAR_FINISHES = [["sunburst", "Sunburst"], ["cherryburst", "Cherry sunburst"], ["honey", "Honey burst"], ["butter", "Butterscotch"], ["tv", "TV yellow"], ["natural", "Natural"],
    ["cherry", "Cherry"], ["red", "Red"], ["black", "Black"], ["white", "White"], ["blue", "Blue"], ["teal", "Seafoam"], ["green", "Green burst"], ["grey", "Grey"]]
    .map(([k, label]) => { const f = FIN[k], c = f.burst || f.solid; return { key: k, label, css: f.burst ? `radial-gradient(circle at 50% 60%, ${c[0]}, ${c[1]} 55%, ${c[2]})` : `linear-gradient(135deg, ${c[0]}, ${c[1]})` }; });

  const S = {
    Stratocaster: { fin: "sunburst", hs: "fender", fb: "maple", bind: null, inlay: "dots",
      body: "M39,132 C32,106 17,104 20,130 C22,146 33,152 29,166 C20,182 12,204 18,226 C26,252 44,262 58,262 C78,262 90,246 88,224 C86,204 76,188 70,172 C69,158 79,148 77,126 C75,104 65,104 61,130 Z",
      pg: { path: "M33,148 C37,160 30,176 29,198 C29,216 43,229 58,225 C71,221 73,203 69,186 C67,171 71,157 67,146 C58,151 42,151 33,148 Z", c: "#f6f2e6" },
      pups: [["single", 39, 163], ["single", 39, 177], ["single", 39, 191]], bridge: ["strat", 222], knobs: [[64, 236, "cream"], [72, 228, "cream"], [56, 244, "cream"]], sw: [72, 212] },
    Telecaster: { fin: "butter", hs: "fender", fb: "maple", bind: null, inlay: "dots",
      body: "M36,134 C30,134 26,142 28,152 C20,164 14,184 16,210 C18,242 36,262 54,262 C76,262 88,244 88,214 C88,192 80,176 76,158 C74,146 76,134 72,124 C70,118 64,118 62,128 L60,134 Z",
      pg: { path: "M36,146 C34,164 36,184 34,206 C34,226 52,233 62,227 C72,219 70,196 68,176 C66,160 70,150 66,142 Z", c: "#1c1c20" },
      pups: [["telecover", 38, 152]], bridge: ["tele", 210], knobs: [[68, 240, "chrome"], [58, 248, "chrome"]], sw: [72, 226] },
    "Les Paul": { fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "trapezoid",
      body: "M36,134 C30,132 24,134 22,146 C14,156 10,176 14,200 C16,236 34,262 54,262 C80,262 92,240 90,208 C88,184 82,168 80,152 C78,140 70,126 62,124 L60,134 Z",
      pups: [["hum", 38, 166, "cream"], ["hum", 38, 198, "cream"]], bridge: ["tom", 224], knobs: [[72, 226, "gold"], [78, 214, "gold"], [70, 240, "gold"], [62, 248, "gold"]], sw: [80, 168] },
    SG: { fin: "cherry", hs: "gibson", fb: "rosewood", bind: null, inlay: "dots",
      body: "M36,134 C30,126 26,100 22,98 C20,112 22,140 26,152 C22,176 24,214 36,238 C44,254 58,262 66,248 C70,240 64,226 70,210 C78,190 78,170 76,154 C80,140 80,116 78,100 C74,104 70,124 62,134 Z",
      pg: { path: "M36,150 C32,172 33,198 40,218 C50,230 66,218 68,198 C70,178 68,160 62,148 Z", c: "#1c1c20" },
      pups: [["hum", 38, 163, "chrome"], ["hum", 38, 193, "chrome"]], bridge: ["tom", 214], knobs: [[62, 230, "black"], [54, 238, "black"]], sw: [26, 150] },
    "ES-335 / semi-hollow": { fin: "cherryburst", hs: "gibson", fb: "rosewood", bind: "#f3e8c4", inlay: "blocks",
      body: "M34,134 C26,130 18,134 16,146 C10,158 8,180 14,202 C10,224 16,250 36,258 C56,266 82,262 88,242 C92,224 88,206 90,194 C92,172 88,152 80,146 C76,132 66,126 60,134 Z",
      pups: [["hum", 38, 164, "chrome"], ["hum", 38, 198, "chrome"]], bridge: ["tom", 228], knobs: [[72, 236, "gold"], [64, 244, "gold"], [78, 226, "gold"], [58, 250, "gold"]], sw: [24, 160], fholes: true },
    "Jazzmaster / Jaguar": { fin: "red", hs: "fender", fb: "rosewood", bind: null, inlay: "dots",
      body: "M38,130 C30,124 24,126 24,138 C24,152 18,160 14,176 C10,194 18,206 24,214 C20,232 26,250 42,258 C62,266 84,258 88,236 C90,214 80,198 82,180 C84,164 78,152 72,142 C72,128 66,124 60,130 Z",
      pg: { path: "M30,150 C28,164 20,178 18,192 C16,204 22,214 30,214 C34,232 44,242 60,240 C72,238 78,224 74,206 C72,190 78,176 74,160 C72,150 64,146 56,148 C48,150 38,146 30,150 Z", c: "#f6f2e6" },
      pups: [["wide", 36, 158], ["wide", 38, 186]], bridge: ["offset", 224], knobs: [[28, 196, "chrome"], [32, 204, "chrome"]], sw: [26, 186] },
    "Explorer / Flying V": { fin: "black", hs: "gibson", fb: "rosewood", bind: null, inlay: "dots",
      body: "M42,134 L26,120 L16,152 L36,176 L12,262 L30,262 L50,214 L70,262 L88,262 L64,176 L84,152 L74,120 L58,134 Z",
      pups: [["hum", 40, 148, "chrome"], ["hum", 40, 168, "chrome"]], bridge: ["tom", 196], knobs: [[62, 214, "gold"], [38, 214, "gold"]], sw: null },
    "PRS / Superstrat": { fin: "cherryburst", hs: "prs", fb: "rosewood", bind: "#0006", inlay: "birds",
      body: "M38,130 C32,114 23,110 21,126 C21,144 30,150 24,164 C14,182 14,214 22,234 C30,254 44,262 54,262 C72,262 84,250 86,226 C88,204 82,182 74,166 C70,152 79,144 79,126 C79,112 68,114 62,130 Z",
      pups: [["hum", 38, 164, "chrome"], ["hum", 38, 190, "chrome"]], bridge: ["hardtail", 222], knobs: [[66, 238, "black"], [56, 246, "black"]], sw: [76, 222] },
    "P-90 guitar (Junior, Special…)": { fin: "tv", hs: "gibson", fb: "rosewood", bind: "#f8f2d8", inlay: "dots",
      body: "M38,132 C30,128 24,130 24,142 C24,156 18,166 16,184 C14,214 20,240 36,254 C50,264 70,264 82,250 C92,236 92,206 88,184 C84,168 78,158 78,142 C78,130 70,128 62,132 Z",
      pups: [["p90", 37, 168], ["p90", 37, 198]], bridge: ["wrap", 228], knobs: [[68, 238, "black"], [60, 246, "black"]], sw: null },
    Acoustic: { fin: "natural", hs: "gibson", fb: "rosewood", bind: "#2a1b10", inlay: "dots", acoustic: true,
      body: "M40,120 C30,118 24,126 24,140 C24,156 34,160 34,168 C34,176 18,184 16,214 C14,246 36,262 56,262 C76,262 92,246 90,216 C88,188 70,178 70,168 C70,160 80,154 80,140 C80,126 70,118 60,120 Z" },
    Other: { fin: "grey", hs: "fender", fb: "rosewood", bind: null, inlay: "dots",
      body: "M38,132 C30,128 24,130 24,142 C24,156 18,166 16,184 C14,214 20,240 36,254 C50,264 70,264 82,250 C92,236 92,206 88,184 C84,168 78,158 78,142 C78,130 70,128 62,132 Z",
      pups: [["hum", 38, 168, "chrome"], ["hum", 38, 200, "chrome"]], bridge: ["hardtail", 228], knobs: [[68, 238, "black"], [60, 246, "black"]], sw: null },
  };
  const FLYING_V = { fin: "honey", hs: "gibson", fb: "rosewood", bind: null, inlay: "dots",
    body: "M40,134 L12,236 L26,256 L50,176 L74,256 L88,236 L60,134 Z", pups: [["hum", 40, 150, "chrome"], ["hum", 40, 168, "chrome"]], bridge: ["tom", 190], knobs: [[50, 214, "gold"]], sw: null };

  let uid = 0;
  const f = n => (Math.round(n * 100) / 100);
  const half = y => 9 + (y - NECK_TOP) / (NECK_END - NECK_TOP) * 3.6;          // neck half-width at y
  const fretY = n => NECK_TOP + 2 + SCALE_LEN * (1 - Math.pow(2, -n / 12));

  function metal(id, dir) { return `<linearGradient id="${id}" x1="0" y1="0" x2="${dir ? 1 : 0}" y2="${dir ? 0 : 1}"><stop offset="0" stop-color="#f4f4f6"/><stop offset=".45" stop-color="#b9bcc4"/><stop offset=".55" stop-color="#8d9099"/><stop offset="1" stop-color="#d9dbe0"/></linearGradient>`; }

  function pickup(p, ids) {
    const [type, x, y, col] = p;
    const cream = "#efe6c8", black = "#17171a";
    const poles = (cx, cy, n, gap, c) => Array.from({ length: n }, (_, i) => `<circle cx="${f(cx + (i - (n - 1) / 2) * gap)}" cy="${cy}" r=".75" fill="${c}"/>`).join("");
    if (type === "single") return `<g><rect x="${x - 1}" y="${y - .8}" width="26" height="7.6" rx="3" fill="#000" opacity=".35"/><rect x="${x}" y="${y}" width="24" height="5.6" rx="2.6" fill="#f2efe4"/>${poles(x + 12, y + 2.8, 6, 3.4, "#9a9ca4")}</g>`;
    if (type === "telecover") return `<g><rect x="${x - 1}" y="${y - 1}" width="28" height="11" rx="2" fill="#000" opacity=".3"/><rect x="${x}" y="${y}" width="26" height="9" rx="1.8" fill="url(#${ids.m})"/><rect x="${x}" y="${y}" width="26" height="9" rx="1.8" fill="none" stroke="#6c6f78" stroke-width=".3"/></g>`;
    if (type === "wide") return `<g><rect x="${x - 1}" y="${y - 1}" width="28" height="11.5" rx="3" fill="#000" opacity=".3"/><rect x="${x}" y="${y}" width="26" height="9.5" rx="3" fill="url(#${ids.m})"/><rect x="${x + 2}" y="${y + 2.4}" width="22" height="4.6" rx="2" fill="#e8e2cf"/>${poles(x + 13, y + 4.7, 6, 3.4, "#7c7e86")}</g>`;
    if (type === "p90") return `<g><rect x="${x - 1}" y="${y - 1}" width="28" height="12" rx="3" fill="#000" opacity=".35"/><rect x="${x}" y="${y}" width="26" height="10" rx="3" fill="#efe6c8"/>${poles(x + 13, y + 5, 6, 3.4, "#7c7e86")}<circle cx="${x + 2.2}" cy="${y + 5}" r=".9" fill="#8d9099"/><circle cx="${x + 23.8}" cy="${y + 5}" r=".9" fill="#8d9099"/></g>`;
    // humbucker
    const body = col === "cream" ? `<rect x="${x}" y="${y}" width="26" height="10.5" rx="1.6" fill="${cream}"/><rect x="${x + 1.5}" y="${y + 1.5}" width="23" height="3.2" rx=".8" fill="#d8cca6"/><rect x="${x + 1.5}" y="${y + 5.8}" width="23" height="3.2" rx=".8" fill="#d8cca6"/>${poles(x + 13, y + 3.1, 6, 3.4, "#7c7e86")}${poles(x + 13, y + 7.4, 6, 3.4, "#7c7e86")}`
      : col === "black" ? `<rect x="${x}" y="${y}" width="26" height="10.5" rx="1.6" fill="${black}"/>${poles(x + 13, y + 3.1, 6, 3.4, "#8d9099")}${poles(x + 13, y + 7.4, 6, 3.4, "#8d9099")}`
        : `<rect x="${x}" y="${y}" width="26" height="10.5" rx="1.8" fill="url(#${ids.m})"/><rect x="${x}" y="${y}" width="26" height="10.5" rx="1.8" fill="none" stroke="#5f626b" stroke-width=".3"/><line x1="${x + 1}" x2="${x + 25}" y1="${y + 5.25}" y2="${y + 5.25}" stroke="#7d808a" stroke-width=".35"/>`;
    return `<g><rect x="${x - 1}" y="${y - 1}" width="28" height="12.5" rx="2.4" fill="#000" opacity=".35"/>${body}</g>`;
  }

  function bridge(b, ids) {
    const [type, y] = b;
    const sad = (x0, n, gap, yy, c) => Array.from({ length: n }, (_, i) => `<rect x="${f(x0 + i * gap - 1)}" y="${yy}" width="2" height="3.2" rx=".6" fill="${c}"/>`).join("");
    if (type === "strat") return `<g><rect x="35" y="${y - 3}" width="30" height="13" rx="1.6" fill="url(#${ids.m})"/>${sad(40.5, 6, 3.8, y - .5, "#c9ccd4")}<rect x="38" y="${y + 6}" width="24" height="2" fill="#7d808a"/><rect x="47" y="${y + 8}" width="6" height="12" rx="2" fill="#9ea1aa"/></g>`;
    if (type === "tele") return `<g><path d="M33,${y} L67,${y} L66,${y + 18} L35,${y + 18} Z" fill="url(#${ids.m})" stroke="#6c6f78" stroke-width=".4"/>${sad(42, 3, 8, y + 5, "#c9ccd4")}</g>`;
    if (type === "tom") return `<g><rect x="36" y="${y}" width="28" height="2.4" rx="1" fill="url(#${ids.m})"/>${sad(39.6, 6, 4.2, y - .4, "#d0d3da")}<rect x="38" y="${y + 12}" width="24" height="3.6" rx="1.4" fill="url(#${ids.m})"/><circle cx="37" cy="${y}" r="1.2" fill="#8d9099"/><circle cx="63" cy="${y}" r="1.2" fill="#8d9099"/></g>`;
    if (type === "wrap") return `<g><rect x="36" y="${y}" width="28" height="3.2" rx="1.6" fill="url(#${ids.m})"/>${sad(39.6, 6, 4.2, y + 3.2, "#d0d3da")}</g>`;
    if (type === "hardtail") return `<g><rect x="35" y="${y}" width="30" height="6" rx="1.2" fill="url(#${ids.m})"/>${sad(40.5, 6, 3.8, y + 1.4, "#c9ccd4")}</g>`;
    if (type === "offset") return `<g><rect x="35" y="${y}" width="30" height="4" rx="1.6" fill="url(#${ids.m})"/>${sad(40.5, 6, 3.8, y + 1, "#d0d3da")}<rect x="30" y="${y + 20}" width="40" height="8" rx="2" fill="url(#${ids.m})"/><rect x="43" y="${y + 21.5}" width="14" height="5" rx="1" fill="#8d9099"/></g>`;
    return "";
  }

  function knob(k) {
    const [x, y, c] = k;
    const g = { cream: ["#f4efe0", "#c9c2a8"], gold: ["#f1d56a", "#9a7518"], black: ["#3a3a40", "#08080a"], chrome: ["#f4f4f6", "#8d9099"] }[c] || ["#3a3a40", "#08080a"];
    const gid = `kn${++uid}`;
    return `<g><defs><radialGradient id="${gid}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="${g[0]}"/><stop offset="1" stop-color="${g[1]}"/></radialGradient></defs><circle cx="${x}" cy="${y + .7}" r="3.6" fill="#000" opacity=".35"/><circle cx="${x}" cy="${y}" r="3.2" fill="url(#${gid})"/><circle cx="${x}" cy="${y}" r="1.1" fill="#000" opacity=".18"/></g>`;
  }

  function headstock(kind, fin) {
    const wood = "#4a2f1d", edge = "#24150b";
    const posts = (xs, ys) => xs.map((x, i) => `<circle cx="${x}" cy="${ys[i]}" r="1.7" fill="#c9ccd4" stroke="#6c6f78" stroke-width=".4"/>`).join("");
    if (kind === "fender") {
      const ys = [14, 22, 30, 38, 46, 54];
      return `<path d="M91.5,${NECK_TOP} L91.5,50 C86,40 84,22 90,9 C94,3 108,3 112,9 C118,22 114,40 108.5,50 L108.5,${NECK_TOP} Z" fill="${wood}" stroke="${edge}" stroke-width=".8"/>` +
        `<path d="M93,66 L93,50 C89,40 88,24 92,12" fill="none" stroke="#ffffff" stroke-opacity=".12" stroke-width="1.4"/>` + posts(Array(6).fill(100).map((_, i) => 94 + i * 0), ys).replace(/cx="94"/g, 'cx="96"') +
        ys.map(y => `<rect x="108" y="${y - 2.2}" width="6" height="4.4" rx="1.8" fill="#d6d8de" stroke="#6c6f78" stroke-width=".4"/>`).join("") +
        `<rect x="95" y="60" width="10" height="3" rx="1.2" fill="#c9ccd4" opacity=".8"/>`;
    }
    if (kind === "prs") {
      const ys = [18, 30, 42];
      return `<path d="M91,${NECK_TOP} L90,52 C84,42 82,24 86,12 C90,4 96,2 100,2 C104,2 110,4 114,12 C118,24 116,42 110,52 L109,${NECK_TOP} Z" fill="${wood}" stroke="${edge}" stroke-width=".8"/>` +
        ys.map(y => `<g><circle cx="92" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="80" y="${y - 2.3}" width="9" height="4.6" rx="2" fill="#d9c27a" stroke="#7a6620" stroke-width=".4"/></g>`).join("") +
        ys.map(y => `<g><circle cx="108" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="111" y="${y - 2.3}" width="9" height="4.6" rx="2" fill="#d9c27a" stroke="#7a6620" stroke-width=".4"/></g>`).join("");
    }
    const ys = [16, 30, 44];   // gibson 3+3
    return `<path d="M88,${NECK_TOP} L87,56 C80,46 79,24 83,12 C90,5 110,5 117,12 C121,24 120,46 113,56 L112,${NECK_TOP} Z" fill="#17120e" stroke="#000" stroke-width=".8"/>` +
      `<path d="M89,${NECK_TOP - 3} L88.5,56 C83,46 82,26 86,15" fill="none" stroke="#fff" stroke-opacity=".1" stroke-width="1.4"/>` +
      ys.map(y => `<g><circle cx="90" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="77" y="${y - 2.6}" width="11" height="5.2" rx="2.3" fill="#e5d27c" stroke="#8a7424" stroke-width=".4"/></g>`).join("") +
      ys.map(y => `<g><circle cx="110" cy="${y}" r="1.7" fill="#c9ccd4"/><rect x="112" y="${y - 2.6}" width="11" height="5.2" rx="2.3" fill="#e5d27c" stroke="#8a7424" stroke-width=".4"/></g>`).join("") +
      `<text x="100" y="${NECK_TOP - 8}" text-anchor="middle" font-size="5.5" font-weight="700" fill="#d8c88a" font-family="Georgia,serif" opacity=".8">${""}</text>`;
  }

  function inlays(style, fb) {
    const c = fb === "maple" ? "#2a2018" : "#efe6c8";
    const out = [];
    [3, 5, 7, 9].forEach(n => {
      const y = (fretY(n - 1) + fretY(n)) / 2;
      if (style === "blocks") out.push(`<rect x="96.5" y="${f(y - 3)}" width="7" height="6" rx="1" fill="${c}"/>`);
      else if (style === "trapezoid") out.push(`<path d="M${96.5} ${f(y - 3)} L103.5 ${f(y - 3)} L102.6 ${f(y + 3)} L97.4 ${f(y + 3)} Z" fill="${c}"/>`);
      else if (style === "birds") out.push(`<path d="M96,${f(y)} q4,-5 8,0 q-4,2 -4,5 q0,-3 -4,-5 Z" fill="${c}"/>`);
      else out.push(`<circle cx="100" cy="${f(y)}" r="1.9" fill="${c}"/>`);
    });
    const y12 = (fretY(11) + fretY(12)) / 2;
    if (style === "dots") out.push(`<circle cx="96.4" cy="${f(y12)}" r="1.9" fill="${c}"/><circle cx="103.6" cy="${f(y12)}" r="1.9" fill="${c}"/>`);
    else if (style === "blocks") out.push(`<rect x="96.5" y="${f(y12 - 3)}" width="7" height="6" rx="1" fill="${c}"/>`);
    else if (style === "trapezoid") out.push(`<path d="M96.5 ${f(y12 - 3)} L103.5 ${f(y12 - 3)} L102.6 ${f(y12 + 3)} L97.4 ${f(y12 + 3)} Z" fill="${c}"/>`);
    else out.push(`<path d="M96,${f(y12)} q4,-5 8,0 q-4,2 -4,5 q0,-3 -4,-5 Z" fill="${c}"/>`);
    return out.join("");
  }

  window.guitarSVG = function (kind, name, finish) {
    let g = S[kind] || S.Other;
    if (kind === "Explorer / Flying V" && /flying|\bv\b/i.test(name || "")) g = FLYING_V;
    const id = "g" + (++uid), ids = { m: id + "m" };
    const fin = FIN[finish] || FIN[g.fin];
    const bodyFill = fin.burst
      ? `<radialGradient id="${id}b" cx=".5" cy=".62" r=".62" gradientTransform="translate(0 0)"><stop offset="0" stop-color="${fin.burst[0]}"/><stop offset=".5" stop-color="${fin.burst[1]}"/><stop offset="1" stop-color="${fin.burst[2]}"/></radialGradient>`
      : `<linearGradient id="${id}b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${fin.solid[0]}"/><stop offset="1" stop-color="${fin.solid[1]}"/></linearGradient>`;
    const hw = g.acoustic ? "" : (g.pups || []).map(p => { const q = p.slice(); q[2] = hY(q[2]); return pickup(q, ids); }).join("");
    const strings = Array.from({ length: 6 }, (_, i) => {
      const bx = g.acoustic ? 100 + (i - 2.5) * 4.8 : 100 + (i - 2.5) * 4.6, by = g.bridge ? absY(g.bridge[1]) + (g.bridge[0] === "strat" ? 8 : g.bridge[0] === "tele" ? 14 : 3) : absY(226);
      return `<line x1="${f(100 + (i - 2.5) * 3.1)}" y1="${NECK_TOP + 1}" x2="${f(bx)}" y2="${f(by)}" stroke="#e7e8ec" stroke-width="${f(.55 + (5 - i) * .08)}" stroke-opacity=".78"/>`;
    }).join("");
    const frets = Array.from({ length: 12 }, (_, i) => { const y = fretY(i + 1), h = half(y) - 1.4; return `<line x1="${f(100 - h)}" x2="${f(100 + h)}" y1="${f(y)}" y2="${f(y)}" stroke="#d9dbe0" stroke-width=".9"/>`; }).join("");
    const fbCol = g.fb === "maple" ? ["#e9c98b", "#c79a55"] : ["#3a2013", "#1e0f08"];
    const neckPoly = `M${100 - 9},${NECK_TOP} L${100 + 9},${NECK_TOP} L${100 + 12.6},${NECK_END + 6} L${100 - 12.6},${NECK_END + 6} Z`;
    const fbPoly = `M${100 - 7.6},${NECK_TOP + 2} L${100 + 7.6},${NECK_TOP + 2} L${100 + 11},${NECK_END + 6} L${100 - 11},${NECK_END + 6} Z`;
    const knobs = (g.knobs || []).map(k => knob([k[0], hY(k[1]), k[2]])).join("");
    const sy = g.sw ? hY(g.sw[1]) : 0;
    const sw = g.sw ? `<rect x="${g.sw[0] - 4}" y="${f(sy - 1.2)}" width="8" height="2.4" rx="1.2" fill="#c9ccd4" stroke="#6c6f78" stroke-width=".3"/><rect x="${g.sw[0] - 1}" y="${f(sy - 2)}" width="2.6" height="4" rx="1" fill="#f2f2f4"/>` : "";
    const pg = g.pg ? `<g clip-path="url(#${id}c)"><path d="${g.pg.path}" fill="${g.pg.c}"/><path d="${g.pg.path}" fill="none" stroke="#000" stroke-opacity=".25" stroke-width=".6"/></g>` : "";
    const fholes = g.fholes ? `<path d="M26,178 q-5,18 3,34 M28,178 q-3,18 3,34" fill="none" stroke="#0a0a0a" stroke-width="2" stroke-linecap="round"/><path d="M74,178 q5,18 -3,34 M72,178 q3,18 -3,34" fill="none" stroke="#0a0a0a" stroke-width="2" stroke-linecap="round"/>` : "";
    const acBody = g.acoustic ? `<path d="M30,150 C26,164 38,176 52,172 C60,168 62,152 58,142 C48,148 36,142 30,150 Z" fill="#2a1b10" opacity=".55"/>` : "";
    const hs146 = hY(146), hb = hY(226);
    const ac = g.acoustic ? `<circle cx="52" cy="${f(hs146)}" r="12.4" fill="#2a1b10"/><circle cx="52" cy="${f(hs146)}" r="10.4" fill="#050403"/><circle cx="52" cy="${f(hs146)}" r="12.4" fill="none" stroke="#e8dcc0" stroke-width=".8" opacity=".7"/><rect x="36" y="${f(hb)}" width="32" height="8" rx="2.6" fill="#3a2414"/><rect x="39" y="${f(hb + 2)}" width="26" height="2" rx="1" fill="#e8dcc0"/>${[0, 1, 2, 3, 4, 5].map(i => `<circle cx="${40 + i * 4.8}" cy="${f(hb + 5.6)}" r=".9" fill="#e8dcc0"/>`).join("")}` : "";
    const bind = g.bind ? `<path d="${g.body}" fill="none" stroke="${g.bind}" stroke-width="2.4" stroke-linejoin="round"/>` : "";
    const sheen = `<linearGradient id="${id}s" x1=".1" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".42"/><stop offset=".28" stop-color="#fff" stop-opacity=".06"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".35"/></linearGradient>`;
    return `<svg viewBox="0 0 200 420" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>${bodyFill}${sheen}${metal(ids.m)}<clipPath id="${id}c"><path d="${g.body}"/></clipPath>
        <filter id="${id}f" x="-20%" y="-10%" width="140%" height="130%"><feGaussianBlur stdDeviation="3"/></filter>
        <linearGradient id="${id}n" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".35"/><stop offset=".3" stop-color="#fff" stop-opacity=".1"/><stop offset="1" stop-color="#000" stop-opacity=".4"/></linearGradient></defs>
      <g transform="translate(5,7) translate(0,${f(NECK_END - BODY_TOP * SY)}) scale(2,${SY})" opacity=".38" filter="url(#${id}f)"><path d="${g.body}" fill="#000"/></g>
      ${headstock(g.hs, fin)}
      <path d="${neckPoly}" fill="${g.fb === "maple" ? "#d8b176" : "#5a3a22"}"/><path d="${fbPoly}" fill="${fbCol[0]}"/><path d="${fbPoly}" fill="url(#${id}n)"/>
      ${frets}${inlays(g.inlay, g.fb)}<rect x="${100 - 7.6}" y="${NECK_TOP}" width="15.2" height="3" rx="1" fill="${g.fb === "maple" ? "#efe6c8" : "#f2ead0"}"/>
      <g transform="translate(0,${f(NECK_END - BODY_TOP * SY)}) scale(2,${SY})"><path d="${g.body}" fill="url(#${id}b)"/>
        <g clip-path="url(#${id}c)"><path d="${g.body}" fill="url(#${id}s)"/><path d="M0,0 H100 V140 H0Z" fill="#fff" opacity=".0"/></g>
        ${bind}<path d="${g.body}" fill="none" stroke="#000" stroke-opacity=".55" stroke-width=".7" stroke-linejoin="round"/>
        ${pg}${fholes}${acBody}</g>
      <g transform="scale(2)">${ac}${hw}</g>
      ${strings}
      <g transform="scale(2)">${g.bridge ? bridge([g.bridge[0], hY(g.bridge[1])], ids) : ""}${knobs}${sw}</g>
    </svg>`;
  };

  // Backwards-compatible wrapper used by the UI: a framed illustration tile.
  window.guitarPic = function (kind, name, finish) {
    return `<span class="gpic">${window.guitarSVG(kind, name, finish)}</span>`;
  };
})();
