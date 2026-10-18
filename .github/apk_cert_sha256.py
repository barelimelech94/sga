"""Print the SHA-256 fingerprint of an APK's v2 signing certificate (no Android SDK or JDK version needed)."""
import hashlib, struct, sys

def u32(b, o): return struct.unpack_from('<I', b, o)[0]
def u64(b, o): return struct.unpack_from('<Q', b, o)[0]
def lp(b, o):
    n = u32(b, o)
    return b[o + 4:o + 4 + n], o + 4 + n

d = open(sys.argv[1], 'rb').read()
cd = u32(d, d.rfind(b'PK\x05\x06') + 16)  # central directory offset, from the EOCD record
if d[cd - 16:cd] != b'APK Sig Block 42':
    sys.exit('no APK signing block')
o = cd - u64(d, cd - 24)  # first pair, just after the block's leading size field
while o < cd - 24:
    n, key = u64(d, o), u32(d, o + 8)
    if key == 0x7109871A:  # v2 signature scheme block
        signer, _ = lp(lp(d[o + 12:o + 8 + n], 0)[0], 0)
        signed_data, _ = lp(signer, 0)
        _, p = lp(signed_data, 0)  # skip digests
        certs, _ = lp(signed_data, p)
        print(hashlib.sha256(lp(certs, 0)[0]).hexdigest())
        sys.exit(0)
    o += 8 + n
sys.exit('no v2 signature')
