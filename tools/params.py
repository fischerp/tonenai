"""Parameter resolver for the Katana MkII, parsed from the installed BOSS TONE STUDIO bundle.

Addresses in the bundle's address map are "7-bit byte" numbers (0x146 means bytes 01 46),
so base + offset is plain addition as long as no byte exceeds 0x7F.
"""
import json, os, re

RES = ("/Applications/BOSS/KATANA MkII/BOSS TONE STUDIO for KATANA MkII.app"
       "/Contents/Resources/html/")
HERE = os.path.dirname(os.path.abspath(__file__))
TEMP = 0x60000000

# (block name, base offset inside a patch, prop array name) -- from the app's Patch tree
BLOCKS = [
    ("PatchName", 0x000, "prm_prop_patch_name"), ("Patch_0", 0x010, "prm_prop_patch_0"),
    ("Eq(2)", 0x060, "prm_prop_patch_eq2"), ("Fx(1)", 0x100, "prm_prop_patch_fx"),
    ("Fx(2)", 0x300, "prm_prop_patch_fx"), ("Delay(1)", 0x500, "prm_prop_patch_delay"),
    ("Delay(2)", 0x520, "prm_prop_patch_delay"), ("Patch_1", 0x540, "prm_prop_patch_1"),
    ("Patch_2", 0x620, "prm_prop_patch_2"), ("Status", 0x650, "prm_prop_patch_status"),
    ("KnobAsgn", 0x700, "prm_prop_patch_assign"), ("ExpPedalAsgn", 0x800, "prm_prop_patch_assign"),
    ("ExpPedalAsgnMinMax", 0x830, "prm_prop_patch_assign_minmax"),
    ("GafcExp1Asgn", 0x900, "prm_prop_patch_assign"), ("GafcExp1AsgnMinMax", 0x930, "prm_prop_patch_assign_minmax"),
    ("GafcExp2Asgn", 0xA00, "prm_prop_patch_assign"), ("GafcExp2AsgnMinMax", 0xA30, "prm_prop_patch_assign_minmax"),
    ("GafcExp3Asgn", 0xB00, "prm_prop_patch_assign"), ("GafcExp3AsgnMinMax", 0xB30, "prm_prop_patch_assign_minmax"),
    ("GafcExExp1Asgn", 0xC00, "prm_prop_patch_assign"), ("GafcExExp1AsgnMinMax", 0xC30, "prm_prop_patch_assign_minmax"),
    ("GafcExExp2Asgn", 0xD00, "prm_prop_patch_assign"), ("GafcExExp2AsgnMinMax", 0xD30, "prm_prop_patch_assign_minmax"),
    ("GafcExExp3Asgn", 0xE00, "prm_prop_patch_assign"), ("GafcExExp3AsgnMinMax", 0xE30, "prm_prop_patch_assign_minmax"),
    ("CtrlAsgn", 0xF00, "prm_prop_patch_ctrl_assign"), ("FsAsgn", 0xF08, "prm_prop_patch_fs_assign"),
    ("Patch_Mk2V2", 0xF10, "prm_prop_mk2_v2"),
    ("Contour(1)", 0xF30, "prm_prop_contour"), ("Contour(2)", 0xF38, "prm_prop_contour"),
    ("Contour(3)", 0xF40, "prm_prop_contour"),
]
# blocks that make up a liveset patch (UserPatch%...), per the app's librarian_setting.js
TSL_BLOCKS = ["PatchName", "Patch_0", "Fx(1)", "Fx(2)", "Delay(1)", "Delay(2)", "Patch_1", "Patch_2",
              "Status", "KnobAsgn", "ExpPedalAsgn", "ExpPedalAsgnMinMax", "GafcExp1Asgn",
              "GafcExp1AsgnMinMax", "GafcExp2Asgn", "GafcExp2AsgnMinMax", "FsAsgn", "Patch_Mk2V2",
              "Contour(1)", "Contour(2)", "Contour(3)", "GafcExp3Asgn", "GafcExp3AsgnMinMax",
              "GafcExExp1Asgn", "GafcExExp1AsgnMinMax", "GafcExExp2Asgn", "GafcExExp2AsgnMinMax",
              "GafcExExp3Asgn", "GafcExExp3AsgnMinMax", "CtrlAsgn", "Eq(2)"]


def to_linear(a7):
    return ((a7 >> 24 & 0x7F) << 21) | ((a7 >> 16 & 0x7F) << 14) | ((a7 >> 8 & 0x7F) << 7) | (a7 & 0x7F)


def to_7bit(n):
    return ((n >> 21 & 0x7F) << 24) | ((n >> 14 & 0x7F) << 16) | ((n >> 7 & 0x7F) << 8) | (n & 0x7F)


_ENTRY = re.compile(r"addr:0x([0-9a-fA-F]+),\s*size:\s*(\([^)]*\)|[A-Za-z0-9_]+)\s*,\s*ofs:\s*(-?\d+)\s*,"
                    r"\s*init:\s*('[^']*'|-?\d+)\s*,.*?name:'([^']*)'\s*\},\s*//\s*(\S*)")


def _parse_arrays():
    src = open(RES + "js/config/address_map.js", encoding="utf8").read()
    arrays = {}
    for m in re.finditer(r"var (prm_prop_\w+)\s*=\s*\[(.*?)\n\s*\];", src, re.S):
        ents = []
        for e in _ENTRY.finditer(m.group(2)):
            addr, size, ofs, init, name, prm = e.groups()
            size = size.strip()
            nbytes = {"INTEGER1x7": 1, "INTEGER2x7": 2}.get(size)
            if nbytes is None:
                pm = re.match(r"\(PADDING \| (0x[0-9a-f]+)\)", size)
                nbytes = int(pm.group(1), 16) if pm else int(size)
            ents.append(dict(addr=int(addr, 16), n=nbytes, size=size, ofs=int(ofs),
                             init=init.strip("'") if init.startswith("'") else int(init),
                             name=name, prm=prm, pad=size.startswith("(PADDING")))
        arrays[m.group(1)] = ents
    return arrays


ARRAYS = _parse_arrays()
BLOCK_BASE = {n: b for n, b, _ in BLOCKS}
BLOCK_ARR = {n: a for n, _, a in BLOCKS}


def block_len(block):
    """Number of 7-bit-address-space bytes spanned by a block (end of last entry)."""
    last = ARRAYS[BLOCK_ARR[block]][-1]
    return to_linear(last["addr"]) + last["n"]


def find(block, prm):
    for e in ARRAYS[BLOCK_ARR[block]]:
        if e["prm"] == prm:
            return e
    raise KeyError(f"{block}.{prm}")


def encode(e, value):
    if e["size"] == "INTEGER2x7":
        return [(value >> 7) & 0x7F, value & 0x7F]
    return [value + e["ofs"]]


def decode(e, data):
    if e["size"] == "INTEGER2x7":
        return (data[0] << 7 | data[1])
    return data[0] - e["ofs"]


SLOT_KEYS = ["PANEL", "A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]          # amp memory: index 0 is the Panel, then bank A and B, 4 channels each
SLOT_LABELS = ["PANEL", "A: CH-1", "A: CH-2", "A: CH-3", "A: CH-4", "B: CH-1", "B: CH-2", "B: CH-3", "B: CH-4"]


def slot_base(index):
    """Address of user patch `index` (0 = Panel, 1-4 = A1-A4, 5-8 = B1-B4); same layout as the temporary patch."""
    return 0x10000000 + (index << 16)


def abs_addr(block, offset7, base=TEMP):
    """Absolute address of byte `offset7` (7-bit form) inside a block of the patch stored at `base`."""
    return base + to_7bit(to_linear(BLOCK_BASE[block]) + to_linear(offset7))


def default_block(block):
    """Factory-init bytes for a block (linear)."""
    ents = ARRAYS[BLOCK_ARR[block]]
    out = [0] * block_len(block)
    for e in ents:
        i = to_linear(e["addr"])
        if e["pad"]:
            continue
        if isinstance(e["init"], str):
            out[i:i + e["n"]] = [ord(c) for c in e["init"].ljust(e["n"])[:e["n"]]]
        else:
            out[i:i + e["n"]] = encode(e, e["init"])
    return out


def option_values():
    """Name -> stored value tables for the main selectors (from the app's list_order)."""
    items = json.load(open(RES + "export/item.json"))
    cat = json.load(open(os.path.join(HERE, "catalog.json")))
    tables = {}
    for key, tname in [("booster-type-select-box", "booster"), ("modfx-mod-type-select-box", "mod"),
                       ("delay-delay1-type-select-box", "delay"), ("reverb-type-select-box", "reverb")]:
        order = items[key]["list_order"]
        tables[tname] = {n.upper(): order[i] for i, n in enumerate(cat[key]["options"])}
    tables["amp"] = {"ACOUSTIC": 0, "CLEAN": 1, "CRUNCH": 2, "LEAD": 3, "BROWN": 4}
    return tables


TABLES = option_values()
