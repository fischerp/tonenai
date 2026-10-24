"""Developer tool: derive accurate body outlines for the guitar illustrations from reference photos.

Reference photos (Wikimedia Commons, CC BY-SA / public domain) are only analysed here, in a temp folder; what ships in the app
are original vector paths (app/guitar-shapes.js) with real proportions. See README for attribution.
"""
import cv2, numpy as np, os, sys, json

REF = "/tmp/mkprev/ref"
SIMPLE = {"sg": "white", "flyingv": "black", "explorer": "dark", "strat": "whitefill"}   # plain backgrounds
FLOOD = {"lp": 3, "es335": 3}
CLOSE_BODY = {}
GROW = {"es335": 2}                     # dark rims that blend into a dark backdrop: grow the mask a few pixels
FLIP = set()                            # (add names photographed left-handed)      # smooth, spot-lit backdrops: flood the background from the edges

def segment(path, name):
    im = cv2.imread(path, cv2.IMREAD_UNCHANGED)
    sc = 640 / max(im.shape[:2])
    if sc < 1:
        im = cv2.resize(im, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA)   # keeps GrabCut fast
    if im.ndim == 3 and im.shape[2] == 4:                       # alpha cut-out (tele, p90 png)
        a = im[:, :, 3]
        if a.min() < 250:
            return (a > 128).astype(np.uint8), cv2.cvtColor(im[:, :, :3], cv2.COLOR_BGR2RGB)
        im = im[:, :, :3]
    bgr = im
    h, w = bgr.shape[:2]
    if name in FLOOD:
        sm = cv2.GaussianBlur(bgr, (0, 0), 2.0)
        bgmask = np.zeros((h + 2, w + 2), np.uint8)
        t = FLOOD[name]
        for x in range(0, w, 12):
            for y in (0, h - 1):
                if not bgmask[y + 1, x + 1]:
                    cv2.floodFill(sm.copy(), bgmask, (x, y), 0, (t,) * 3, (t,) * 3, 4 | cv2.FLOODFILL_MASK_ONLY | (255 << 8))
        for y in range(0, h, 12):
            for x in (0, w - 1):
                if not bgmask[y + 1, x + 1]:
                    cv2.floodFill(sm.copy(), bgmask, (x, y), 0, (t,) * 3, (t,) * 3, 4 | cv2.FLOODFILL_MASK_ONLY | (255 << 8))
        m = (bgmask[1:-1, 1:-1] == 0).astype(np.uint8)
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (13, 13)))
    elif name in SIMPLE:
        g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        if SIMPLE[name] == "white": m = (g < 235).astype(np.uint8)
        elif SIMPLE[name] == "whitefill":            # background = near-white pixels connected to the border (inner white pickguard stays)
            cand = (bgr.min(axis=2) > 214).astype(np.uint8)
            n_, lab_, st_, _ = cv2.connectedComponentsWithStats(cand, connectivity=4)
            border = set(lab_[0, :]) | set(lab_[-1, :]) | set(lab_[:, 0]) | set(lab_[:, -1])
            bgm_ = np.isin(lab_, [l for l in border if l != 0])
            m = (~bgm_).astype(np.uint8)
        elif SIMPLE[name] == "notred":
            b_, g_, r_ = [c.astype(np.int32) for c in cv2.split(cv2.GaussianBlur(bgr, (0, 0), 2))]
            m = (~((r_ > 1.7 * g_ + 6) & (r_ > 28))).astype(np.uint8)
        elif SIMPLE[name] == "dark": m = (cv2.GaussianBlur(g, (0, 0), 3) < 105).astype(np.uint8)
        else: m = (g > 40).astype(np.uint8)
    else:
        m = np.zeros((h, w), np.uint8); m[:] = cv2.GC_PR_BGD
        mx, my = int(w * .04), int(h * .02)
        m[my:h - my, mx:w - mx] = cv2.GC_PR_FGD
        m[:my, :] = cv2.GC_BGD; m[h - my:, :] = cv2.GC_BGD; m[:, :mx] = cv2.GC_BGD; m[:, w - mx:] = cv2.GC_BGD
        bgm, fgm = np.zeros((1, 65)), np.zeros((1, 65))
        cv2.grabCut(bgr, m, None, bgm, fgm, 5, cv2.GC_INIT_WITH_MASK)
        m = np.where((m == cv2.GC_FGD) | (m == cv2.GC_PR_FGD), 1, 0).astype(np.uint8)
    if name in GROW: m = cv2.dilate(m, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (GROW[name] * 2 + 1,) * 2))
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, k); m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m)
    if n > 1:
        big = 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA]); m = (lab == big).astype(np.uint8)
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    filled = np.zeros_like(m); cv2.drawContours(filled, cnts, -1, 1, -1)
    return filled, cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)

if __name__ == "__main__":
    names = sys.argv[1:] or sorted({os.path.splitext(f)[0] for f in os.listdir(REF)})
    tiles = []
    for n in names:
        f = next(x for x in os.listdir(REF) if os.path.splitext(x)[0] == n)
        mask, rgb = segment(os.path.join(REF, f), n)
        np.save(f"/tmp/mkprev/mask_{n}.npy", mask)
        ov = rgb.copy(); ov[mask == 0] = (ov[mask == 0] * .25).astype(np.uint8)
        scale = 300 / ov.shape[0]; ov = cv2.resize(ov, None, fx=scale, fy=scale)
        cv2.putText(ov, n, (4, 14), cv2.FONT_HERSHEY_SIMPLEX, .45, (255, 255, 0), 1)
        tiles.append(ov)
    H = 300; W = sum(t.shape[1] for t in tiles) + 4 * len(tiles)
    rows, cur, curw = [], [], 0
    for t in tiles:
        if curw + t.shape[1] > 1300: rows.append(cur); cur, curw = [], 0
        cur.append(t); curw += t.shape[1] + 4
    rows.append(cur)
    sheet = np.zeros((H * len(rows), 1310, 3), np.uint8)
    for r, row in enumerate(rows):
        x = 0
        for t in row:
            sheet[r * H:r * H + t.shape[0], x:x + t.shape[1]] = t; x += t.shape[1] + 4
    cv2.imwrite("/tmp/mkprev/masks.jpg", cv2.cvtColor(sheet, cv2.COLOR_RGB2BGR))
    print("ok", names)


# ----------------------------------------------------------------------------- outline extraction
BODY_ONLY_MM = {"es335": 406}   # real body width (mm) when the neck can't be segmented
OUR_NECK_JOIN_W = 25.2        # canvas units: neck width where it enters the body (see app/guitars.js)
NECK_END = 196                # canvas y of the neck/body join

def runs(row):
    d = np.diff(np.concatenate([[0], row, [0]]))
    return list(zip(np.where(d == 1)[0], np.where(d == -1)[0]))

def analyse(name):
    f = next(x for x in os.listdir(REF) if os.path.splitext(x)[0] == name)
    mask, rgb = segment(os.path.join(REF, f), name)
    h, w = mask.shape
    ys = np.where(mask.any(1))[0]; top, bot = ys[0], ys[-1]
    # 1) orient with the neck pointing up: principal axis -> vertical, narrower end on top
    if name in FLIP:
        mask, rgb = mask[:, ::-1].copy(), rgb[:, ::-1].copy()
    h_orig = mask.shape[0]
    yy, xx = np.nonzero(mask); pts = np.stack([xx, yy], 1).astype(float); mu = pts.mean(0)
    ev, evec = np.linalg.eigh(np.cov((pts - mu).T)); ax = evec[:, 1]               # major axis (x, y)
    ang = np.degrees(np.arctan2(ax[0], ax[1]))                                    # tilt away from vertical
    if abs(ang) > 45: ang = ang - np.sign(ang) * 90 if abs(ang) < 135 else ang    # axis may be horizontal
    M = cv2.getRotationMatrix2D((float(mu[0]), float(mu[1])), -ang, 1.0)
    # enlarge canvas so rotation never crops
    hh, ww = mask.shape; diag = int(np.hypot(hh, ww)); M[0, 2] += (diag - ww) / 2; M[1, 2] += (diag - hh) / 2
    mask = cv2.warpAffine(mask, M, (diag, diag), flags=cv2.INTER_NEAREST); rgb = cv2.warpAffine(rgb, M, (diag, diag), flags=cv2.INTER_LINEAR)
    ys0, xs0 = np.where(mask)
    mask, rgb = mask[ys0.min():ys0.max() + 1, xs0.min() - 4 if xs0.min() > 4 else 0:xs0.max() + 5], rgb[ys0.min():ys0.max() + 1, xs0.min() - 4 if xs0.min() > 4 else 0:xs0.max() + 5]
    h, w = mask.shape
    wd = mask.sum(1); k = max(5, h // 6)
    if wd[:k].mean() > wd[-k:].mean():                                             # wider end must be the body: flip 180
        mask, rgb = mask[::-1, ::-1].copy(), rgb[::-1, ::-1].copy()
    ys = np.where(mask.any(1))[0]; top, bot = ys[0], ys[-1]
    if (bot - top) < 0.5 * h_orig:                    # dark neck merged into the dark backdrop: body-only mode (ES-335)
        widths = np.array([mask[y].sum() for y in range(h)])
        last = max(y for y in range(top, bot) if widths[y] > 0.30 * widths.max())
        body = mask.copy(); body[last + 1:] = 0
        BODY_W_MM, NECK_MM = BODY_ONLY_MM.get(name, 406), 56.0
        nW = widths.max() * NECK_MM / BODY_W_MM           # neck width implied by real proportions
        cx = float(np.mean(np.where(body[top + (last - top) // 3])[0]))
        return dict(name=name, mask=mask, body=body, rgb=rgb, r0=top, join=int(top + .10 * (last - top)), last=last, neckW=nW, neckMid=nW, cx=cx, ang=ang)
    # 2) neck width and the join row
    a0, a1 = int(top + .16 * (bot - top)), int(top + .30 * (bot - top))
    widths = np.array([mask[y].sum() for y in range(h)])
    neckW = float(np.median(widths[a0:a1])); cx = float(np.median([np.mean(np.where(mask[y])[0]) for y in range(a0, a1)]))
    r0 = next((y for y in range(a1, bot) if widths[y] > 1.55 * neckW), None)
    if r0 is None: raise SystemExit(f"{name}: no body found; neckW={neckW} max={widths.max()} rows {a0}-{a1} top {top} bot {bot}")
    join = r0
    if len(runs(mask[r0])) > 1:                      # horns beside the neck: join is where the gap closes
        join = next(y for y in range(r0, bot) if len(runs(mask[y])) == 1)
    # neck width at the join (use rows just above the join/horn tips)
    nW = float(np.median(widths[max(a1, r0 - 40):max(a1 + 1, r0 - 8)]))
    # 3) body mask: drop the neck column above the join, trim the stand below
    body = mask.copy(); body[:r0] = 0
    x0, x1 = int(cx - 0.56 * nW), int(cx + 0.56 * nW) + 1      # only the neck columns go; horns that touch the neck stay
    body[r0:join, x0:x1] = 0
    if name in CLOSE_BODY:                      # white pickguards that touch the edge leave bites in the mask: close them below the join
        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (CLOSE_BODY[name], CLOSE_BODY[name]))
        lower = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, k)
        lower = np.pad(lower, 0)
        body[join + 6:] = lower[join + 6:]
    maxw = widths[r0:bot].max()
    last = max(y for y in range(r0, bot) if widths[y] > 0.30 * maxw)
    body[last + 1:] = 0
    return dict(name=name, mask=mask, body=body, rgb=rgb, r0=r0, join=join, last=last, neckW=nW, neckMid=neckW, cx=cx, ang=ang)

def to_path(body, d, nsamp=120):
    cnts, _ = cv2.findContours(body, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    c = max(cnts, key=cv2.contourArea)[:, 0, :].astype(float)
    s = OUR_NECK_JOIN_W / d["neckW"]
    P = np.stack([100 + (c[:, 0] - d["cx"]) * s, NECK_END + (c[:, 1] - d["join"]) * s], 1)
    # light smoothing + uniform resampling along the outline
    k = 5; Pc = np.concatenate([P[-k:], P, P[:k]])
    ker = np.ones(2 * k + 1) / (2 * k + 1)
    Ps = np.stack([np.convolve(Pc[:, i], ker, "valid") for i in range(2)], 1)
    seg = np.r_[0, np.cumsum(np.hypot(*np.diff(Ps, axis=0).T))]
    t = np.linspace(0, seg[-1], nsamp, endpoint=False)
    Q = np.stack([np.interp(t, seg, Ps[:, i]) for i in range(2)], 1)
    # closed Catmull-Rom -> cubic beziers
    n = len(Q); out = [f"M{Q[0][0]:.1f},{Q[0][1]:.1f}"]
    for i in range(n):
        p0, p1, p2, p3 = Q[(i - 1) % n], Q[i], Q[(i + 1) % n], Q[(i + 2) % n]
        c1 = p1 + (p2 - p0) / 6; c2 = p2 - (p3 - p1) / 6
        out.append(f"C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}")
    return "".join(out) + "Z", s

def calib_image(d, s, path):
    """Photo warped into canvas units (3 px per unit) with a labelled grid and the body outline, for placing hardware."""
    M = np.array([[s * 3, 0, (100 - d["cx"] * s) * 3], [0, s * 3, (NECK_END - d["join"] * s) * 3]], dtype=np.float64)
    W, H = 200 * 3, 460 * 3
    im = cv2.warpAffine(cv2.cvtColor(d["rgb"], cv2.COLOR_RGB2BGR), M, (W, H))
    bm = cv2.warpAffine(d["body"], M, (W, H), flags=cv2.INTER_NEAREST)
    cnts, _ = cv2.findContours(bm, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    cv2.drawContours(im, cnts, -1, (0, 255, 255), 1)
    for u in range(0, 201, 20):
        cv2.line(im, (u * 3, 0), (u * 3, H), (80, 80, 255) if u != 100 else (0, 0, 255), 1); cv2.putText(im, str(u), (u * 3 + 2, 12), cv2.FONT_HERSHEY_SIMPLEX, .4, (0, 255, 0), 1)
    for v in range(0, 461, 20):
        cv2.line(im, (0, v * 3), (W, v * 3), (80, 80, 255), 1); cv2.putText(im, str(v), (2, v * 3 - 2), cv2.FONT_HERSHEY_SIMPLEX, .4, (0, 255, 0), 1)
    ys = np.where(bm.any(1))[0]; y1 = min(H, ys[-1] + 30)
    cv2.imwrite(path, im[max(0, (NECK_END - 60) * 3):y1])

if __name__ == "__main__" and os.environ.get("TRACE"):
    res = {}
    for n in ["strat", "tele", "lp", "sg", "es335", "jazz", "prs", "p90", "flyingv"]:
        d = analyse(n); path, s = to_path(d["body"], d)
        h = d["last"]; bottom = NECK_END + (d["last"] - d["join"]) * s
        res[n] = {"path": path, "bottom": round(bottom, 1), "scale": round(s, 4), "neckW": round(d["neckW"], 1)}
        calib_image(d, s, f"/tmp/mkprev/cal_{n}.png")
        print(n, "ang", round(d["ang"], 2), "r0", d["r0"], "join", d["join"], "last", d["last"], "neckW", round(d["neckW"], 1), "-> bottom", res[n]["bottom"], "len", len(path))
    json.dump(res, open("/tmp/mkprev/bodies.json", "w"))


# ----------------------------------------------------------------------------- pickguards + final export
def canvas_view(d, s, ppu=3):
    M = np.array([[s * ppu, 0, (100 - d["cx"] * s) * ppu], [0, s * ppu, (NECK_END - d["join"] * s) * ppu]], dtype=np.float64)
    W, H = 200 * ppu, 470 * ppu
    im = cv2.warpAffine(cv2.cvtColor(d["rgb"], cv2.COLOR_RGB2BGR), M, (W, H))
    bm = cv2.warpAffine(d["body"], M, (W, H), flags=cv2.INTER_NEAREST)
    return im, bm

PG_RULES = {   # name -> (colour test, region of interest in canvas units x0,y0,x1,y1)
    "strat": (lambda hsv: (hsv[..., 2] > 185) & (hsv[..., 1] < 55), (30, 190, 175, 330)),
    "tele": (lambda hsv: (hsv[..., 2] < 60), (45, 195, 150, 300)),
    "flyingv": (lambda hsv: (hsv[..., 2] > 170) & (hsv[..., 1] < 70), (60, 200, 200, 470)),
    "jazz": (lambda hsv: ((hsv[..., 0] < 12) | (hsv[..., 0] > 168)) & (hsv[..., 1] > 130) & (hsv[..., 2] > 40) & (hsv[..., 2] < 150), (70, 215, 150, 330)),
}

def pickguard_path(name, d, s, ppu=3):
    if name not in PG_RULES: return None
    im, bm = canvas_view(d, s, ppu)
    test, (x0, y0, x1, y1) = PG_RULES[name]
    hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)
    m = (test(hsv) & (bm > 0)).astype(np.uint8)
    roi = np.zeros_like(m); roi[y0 * ppu:y1 * ppu, x0 * ppu:x1 * ppu] = 1; m *= roi
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    m = cv2.erode(m, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))          # leave a visible rim of body finish around the guard
    n, lab, st, _ = cv2.connectedComponentsWithStats(m)
    if n < 2: return None
    m = (lab == 1 + np.argmax(st[1:, cv2.CC_STAT_AREA])).astype(np.uint8)
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    c = max(cnts, key=cv2.contourArea)[:, 0, :].astype(float) / ppu
    if len(c) < 20: return None
    return smooth_path(c, 70)

def smooth_path(c, nsamp):
    k = 3; Pc = np.concatenate([c[-k:], c, c[:k]]); ker = np.ones(2 * k + 1) / (2 * k + 1)
    Ps = np.stack([np.convolve(Pc[:, i], ker, "valid") for i in range(2)], 1)
    seg = np.r_[0, np.cumsum(np.hypot(*np.diff(Ps, axis=0).T))]; t = np.linspace(0, seg[-1], nsamp, endpoint=False)
    Q = np.stack([np.interp(t, seg, Ps[:, i]) for i in range(2)], 1); n = len(Q); out = [f"M{Q[0][0]:.1f},{Q[0][1]:.1f}"]
    for i in range(n):
        p0, p1, p2, p3 = Q[(i - 1) % n], Q[i], Q[(i + 1) % n], Q[(i + 2) % n]
        c1 = p1 + (p2 - p0) / 6; c2 = p2 - (p3 - p1) / 6
        out.append(f"C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}")
    return "".join(out) + "Z"

def export(out_js="app/guitar-shapes.js"):
    shapes = {}
    for n in ["strat", "tele", "lp", "sg", "es335", "jazz", "prs"]:
        d = analyse(n); path, s = to_path(d["body"], d)
        shapes[n] = {"body": path, "bottom": round(NECK_END + (d["last"] - d["join"]) * s, 1), "pg": pickguard_path(n, d, s)}
        print(n, shapes[n]["bottom"], "pg" if shapes[n]["pg"] else "-")
    js = ("/* Generated by tools/trace_guitars.py: original vector outlines (canvas units, 200 wide, neck join at y=196).\n"
          "   Derived by tracing silhouettes of public photographs (see README, Credits); no photo data is included. */\n"
          "window.GUITAR_SHAPES = " + json.dumps(shapes, separators=(",", ":")) + ";\n")
    open(out_js, "w").write(js); print("wrote", out_js, len(js) // 1024, "KB")

if __name__ == "__main__" and os.environ.get("EXPORT"):
    export()
