#!/usr/bin/env python3
# Extracts the signing-certificate lineage (proof of rotation) from an APK's v3 signature block
# and writes it as an apksigner lineage file. The lineage is public data (certificates and the
# old key's signatures over them); no private key is involved.
# Usage: extract_lineage.py APK OUT.bin
# Created By Shane BlackbuRn
import struct, sys

V3_BLOCK = 0xF05368C0
POR_ATTR = 0x3BA06F8C
LINEAGE_MAGIC = 0x3EFF39D1   # apksig SigningCertificateLineage file prefix
LINEAGE_VERSION = 1

def lp(b, o):
    n = struct.unpack("<I", b[o:o + 4])[0]
    return b[o + 4:o + 4 + n], o + 4 + n

def seq(b):
    o, out = 0, []
    while o < len(b):
        x, o = lp(b, o); out.append(x)
    return out

def main(apk, out):
    d = open(apk, "rb").read()
    eocd = d.rfind(b"PK\x05\x06")
    cd = struct.unpack("<I", d[eocd + 16:eocd + 20])[0]
    if d[cd - 16:cd] != b"APK Sig Block 42":
        sys.exit("No APK signing block")
    size = struct.unpack("<Q", d[cd - 24:cd - 16])[0]
    p, end, v3 = cd - size - 8 + 8, cd - 24, None
    while p < end:
        n = struct.unpack("<Q", d[p:p + 8])[0]
        if struct.unpack("<I", d[p + 8:p + 12])[0] == V3_BLOCK:
            v3 = d[p + 12:p + 8 + n]
        p += 8 + n
    if v3 is None:
        sys.exit("No v3 signature block")
    signer = seq(lp(v3, 0)[0])[0]
    signed, _ = lp(signer, 0)
    _, o = lp(signed, 0)          # digests
    _, o = lp(signed, o)          # certificates
    attrs, _ = lp(signed, o + 8)  # skip min/max SDK
    for a in seq(attrs):
        if struct.unpack("<I", a[:4])[0] == POR_ATTR:
            open(out, "wb").write(struct.pack("<II", LINEAGE_MAGIC, LINEAGE_VERSION) + a[4:])
            print(f"Lineage written to {out} ({len(a) - 4} bytes of proof of rotation)")
            return
    sys.exit("The v3 signer has no proof-of-rotation (no lineage)")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
