"""Build Katana MkII patches from a high-level spec; apply live over USB MIDI or export a .tsl liveset."""
import json, sys, time
import params as P

AMP_BASE = 28  # preamp type = 28 + panel amp type (Acoustic..Brown), verified on the amp


def _set(blocks, block, prm, value):
    e = P.find(block, prm)
    i = P.to_linear(e["addr"])
    blocks[block][i:i + e["n"]] = P.encode(e, int(value))


def _fx_param(blocks, block, effect, name, value):
    """Set e.g. effect='CHORUS', name='RATE' -> PRM_FX1_CHORUS_RATE."""
    _set(blocks, block, f"PRM_FX1_{effect}_{name}", value)


def _opt(table, name):
    key = name.upper()
    if key not in P.TABLES[table]:
        raise ValueError(f"unknown {table} type {name!r}; choose from {sorted(P.TABLES[table])}")
    return P.TABLES[table][key]


def build(spec):
    """Return {block: [bytes]} for a spec (defaults + overrides)."""
    b = {n: P.default_block(n) for n in P.TSL_BLOCKS}
    name = spec.get("name", "KATANA Mk2")[:16].ljust(16)
    b["PatchName"][:16] = [ord(c) for c in name]

    a = spec.get("amp", {})
    t = _opt("amp", a.get("type", "CRUNCH"))
    _set(b, "Patch_0", "PRM_PREAMP_A_TYPE", AMP_BASE + t)
    for key, prm in [("gain", "PRM_PREAMP_A_GAIN"), ("bass", "PRM_PREAMP_A_BASS"), ("mid", "PRM_PREAMP_A_MIDDLE"),
                     ("treble", "PRM_PREAMP_A_TREBLE"), ("presence", "PRM_PREAMP_A_PRESENCE"),
                     ("volume", "PRM_PREAMP_A_LEVEL"), ("bright", "PRM_PREAMP_A_BRIGHT")]:
        if key in a:
            _set(b, "Patch_0", prm, a[key])
    # panel knobs mirror the patch so the amp's front panel agrees
    st = {"PRM_KNOB_POS_TYPE": t, "PRM_LED_STATE_VARI": a.get("variation", 0)}
    for key, prm in [("gain", "PRM_KNOB_POS_GAIN"), ("volume", "PRM_KNOB_POS_VOLUME"), ("bass", "PRM_KNOB_POS_BASS"),
                     ("mid", "PRM_KNOB_POS_MIDDLE"), ("treble", "PRM_KNOB_POS_TREBLE"),
                     ("presence", "PRM_KNOB_POS_PRESENCE")]:
        if key in a:
            st[prm] = a[key]
    for prm, v in st.items():
        _set(b, "Status", prm, v)

    bo = spec.get("booster")
    if bo:
        v = _opt("booster", bo["type"])
        _set(b, "Patch_0", "PRM_ODDS_SW", int(bo.get("on", True)))
        _set(b, "Patch_0", "PRM_ODDS_TYPE", v)
        for key, prm in [("drive", "PRM_ODDS_DRIVE"), ("bottom", "PRM_ODDS_BOTTOM"), ("tone", "PRM_ODDS_TONE"),
                         ("level", "PRM_ODDS_EFFECT_LEVEL")]:
            if key in bo:
                _set(b, "Patch_0", prm, bo[key])
        _set(b, "Patch_2", "PRM_FXBOX_ASGN_BOOSTER_G", v)

    for key, blk, asgn in [("mod", "Fx(1)", "PRM_FXBOX_ASGN_MOD_G"), ("fx", "Fx(2)", "PRM_FXBOX_ASGN_FX_G")]:
        fx = spec.get(key)
        if fx:
            v = _opt("mod", fx["type"])
            _set(b, blk, "PRM_FX1_SW", int(fx.get("on", True)))
            _set(b, blk, "PRM_FX1_FXTYPE", v)
            short = {"UNI-V": "UNIV", "AUTO WAH": "AWAH", "T.WAH": "TWAH", "SLOW GEAR": "SLOWGEAR",
                     "RING MOD": "RINGMOD", "PITCH SHIFTER": "PSHIFT", "HEAVY OCTAVE": "HEAVYOCT"}
            eff = short.get(fx["type"].upper(), fx["type"].upper().replace(" ", ""))
            for pname, pv in fx.get("params", {}).items():
                _fx_param(b, blk, eff, pname.upper(), pv)
            _set(b, "Patch_2", asgn, v)

    d = spec.get("delay")
    if d:
        v = _opt("delay", d.get("type", "DIGITAL"))
        _set(b, "Delay(1)", "PRM_DLY_SW", int(d.get("on", True)))
        _set(b, "Delay(1)", "PRM_DLY_TYPE", v)
        for key, prm in [("time", "PRM_DLY_COMMON_DLY_TIME"), ("feedback", "PRM_DLY_COMMON_FEEDBACK"),
                         ("level", "PRM_DLY_COMMON_EFFECT_LEVEL"), ("high_cut", "PRM_DLY_COMMON_HICUT")]:
            if key in d:
                _set(b, "Delay(1)", prm, d[key])
        _set(b, "Patch_2", "PRM_FXBOX_ASGN_DELAY_G", v)

    r = spec.get("reverb")
    if r:
        v = _opt("reverb", r.get("type", "PLATE"))
        _set(b, "Patch_1", "PRM_REVERB_SW", int(r.get("on", True)))
        _set(b, "Patch_1", "PRM_REVERB_TYPE", v)
        for key, prm in [("time", "PRM_REVERB_TIME"), ("pre_delay", "PRM_REVERB_PREDELAY"),
                         ("low_cut", "PRM_REVERB_LOWCUT"), ("high_cut", "PRM_REVERB_HICUT"),
                         ("level", "PRM_REVERB_EFFECT_LEVEL")]:
            if key in r:
                _set(b, "Patch_1", prm, r[key])
        _set(b, "Patch_2", "PRM_FXBOX_ASGN_REVERB_G", v)

    ns = spec.get("noise_suppressor")
    if ns:
        _set(b, "Patch_1", "PRM_NS1_SW", int(ns.get("on", True)))
        if "threshold" in ns:
            _set(b, "Patch_1", "PRM_NS1_THRESHOLD", ns["threshold"])

    for item in spec.get("raw", []):  # [block, PRM_NAME, value] escape hatch
        _set(b, item[0], item[1], item[2])
    return b


# ---------------------------------------------------------------- live MIDI

def _chunks(block, step=48):
    n = P.block_len(block)
    for off in range(0, n, step):
        yield P.to_7bit(off), min(step, n - off)


def apply_live(blocks, inp, out):
    import katana as k
    k.dt1(out, 0x7F000001, [1])
    time.sleep(0.2)
    for blk in ["PatchName", "Patch_0", "Fx(1)", "Fx(2)", "Delay(1)", "Delay(2)", "Patch_1", "Patch_2", "Eq(2)",
                "Patch_Mk2V2", "Status"]:
        data = blocks[blk]
        for off7, ln in _chunks(blk):
            lin = P.to_linear(off7)
            k.dt1(out, P.abs_addr(blk, off7), data[lin:lin + ln])
    time.sleep(0.3)


def snapshot(inp, out, blocks=None):
    import katana as k
    k.dt1(out, 0x7F000001, [1])
    time.sleep(0.2)
    res = {}
    for blk in blocks or P.TSL_BLOCKS:
        buf = []
        for off7, ln in _chunks(blk):
            got = k.rq1(inp, out, P.abs_addr(blk, off7), ln)
            if len(got) != ln:
                raise IOError(f"short read {blk}@{off7:#x}: {len(got)}/{ln}")
            buf += got
        res[blk] = buf
    return res


# ---------------------------------------------------------------- .tsl export

def to_tsl(patches, liveset_name):
    data = []
    for spec_or_blocks in patches:
        blocks = spec_or_blocks if "Patch_0" in spec_or_blocks else build(spec_or_blocks)
        data.append({"memo": spec_or_blocks.get("memo", "") if "Patch_0" not in spec_or_blocks else "",
                     "paramSet": {f"UserPatch%{n}": [f"{x:02X}" for x in blocks[n]] for n in P.TSL_BLOCKS}})
    return {"name": liveset_name, "formatRev": "0002", "device": "KATANA MkII", "data": [data]}


def main():
    import argparse, katana as k
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["apply", "export", "backup", "show"])
    ap.add_argument("spec", nargs="?")
    ap.add_argument("-o", "--out")
    a = ap.parse_args()
    if a.cmd == "apply":
        specs = json.load(open(a.spec))
        spec = specs[0] if isinstance(specs, list) else specs
        i, o = k.open_ports()
        apply_live(build(spec), i, o)
        print("applied:", spec["name"])
    elif a.cmd == "export":
        specs = json.load(open(a.spec))
        specs = specs if isinstance(specs, list) else [specs]
        name = a.out.rsplit("/", 1)[-1].rsplit(".", 1)[0] if a.out else "mk2ai"
        json.dump(to_tsl(specs, name), open(a.out, "w"), indent=1)
        print("wrote", a.out, len(specs), "patches")
    elif a.cmd == "backup":
        i, o = k.open_ports()
        snap = snapshot(i, o)
        json.dump(to_tsl([snap], "backup"), open(a.out, "w"), indent=1)
        print("backup written", a.out)
    elif a.cmd == "show":
        i, o = k.open_ports()
        s = snapshot(i, o, ["PatchName", "Patch_0", "Status"])
        print(bytes(s["PatchName"]).decode(), s["Patch_0"][:30], s["Status"])


if __name__ == "__main__":
    sys.path.insert(0, __file__.rsplit("/", 1)[0])
    main()
