"""Minimal Katana MkII SysEx helper (Roland DT1/RQ1)."""
import time, mido

HDR = [0x41, 0x00, 0x00, 0x00, 0x00, 0x33]

def cks(b):
    return (128 - sum(b) % 128) % 128

def addr4(a):
    return [(a >> 21) & 0x7F, (a >> 14) & 0x7F, (a >> 7) & 0x7F, a & 0x7F]

def nib(a):  # app addresses are 7-bit-per-byte: 0x60000010 -> bytes 60 00 00 10
    return [(a >> 24) & 0xFF, (a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF]

def open_ports():
    return mido.open_input("KATANA"), mido.open_output("KATANA")

def rq1(inp, out, addr, size, timeout=2):
    for _ in inp.iter_pending():
        pass
    body = nib(addr) + nib(size)
    out.send(mido.Message("sysex", data=HDR + [0x11] + body + [cks(body)]))
    want, t = nib(addr), time.time()
    while time.time() - t < timeout:
        m = inp.poll()
        if m and m.type == "sysex" and m.data[6] == 0x12 and list(m.data[7:11]) == want:
            return list(m.data[11:-1])
        if not m:
            time.sleep(0.005)
    return []


def dt1(out, addr, values):
    body = nib(addr) + list(values)
    out.send(mido.Message("sysex", data=HDR + [0x12] + body + [cks(body)]))
    time.sleep(0.03)
