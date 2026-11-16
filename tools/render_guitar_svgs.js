// Renders the desktop app's guitar artwork (app/guitars.js) to one SVG per guitar model for the Android app:
//   node tools/render_guitar_svgs.js
// Output: android/app/src/main/assets/guitars/<model>.svg (drawn in each model's usual finish; the app swaps the finish).
// The app's own scripts are not run on the phone, so no JavaScript engine or native library is needed there.
const fs = require("fs"), path = require("path");
global.window = global;
for (const f of ["guitar-shapes.js", "guitars.js"]) (0, eval)(fs.readFileSync(path.join(__dirname, "..", "app", f), "utf8"));
const KINDS = ["Stratocaster", "Telecaster", "Les Paul", "SG", "ES-335 / semi-hollow", "Jazzmaster / Jaguar", "Explorer", "Flying V",
  "PRS / Superstrat", "P-90 guitar (Junior, Special…)", "Dreadnought", "Concert / OM", "Classical", "Other"];
const out = path.join(__dirname, "..", "android", "app", "src", "main", "assets", "guitars");
fs.mkdirSync(out, { recursive: true });
const slug = k => k.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
for (const kind of KINDS) {
  // AndroidSVG cannot blur: leave out the soft drop shadow (a blurred black copy of the body) instead of drawing it hard.
  const svg = window.guitarSVG(kind === "Flying V" ? "Explorer / Flying V" : kind, kind === "Flying V" ? "Flying V" : "", "")
    .replace(/<g transform="translate\(5,7\)"[^>]*filter=[^>]*>[\s\S]*?<\/g>/, "");
  fs.writeFileSync(path.join(out, slug(kind) + ".svg"), svg);
}
console.log("wrote", KINDS.length, "files");
