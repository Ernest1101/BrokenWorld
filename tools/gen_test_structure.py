"""Writes the 1x1x1 empty structure that the GameTests run in:
src/main/resources/data/brokenworld/structures/empty.nbt. Run: python tools/gen_test_structure.py"""
import gzip
import os
import struct

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_int(n, v):
    return bytes([TAG_INT]) + name(n) + struct.pack(">i", v)


def tag_string(n, v):
    return bytes([TAG_STRING]) + name(n) + name(v)


def tag_list(n, elem_type, payloads):
    return bytes([TAG_LIST]) + name(n) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def compound_payload(*tags):
    return b"".join(tags) + bytes([TAG_END])


root = compound_payload(
    tag_int("DataVersion", 3465),  # 1.20.1
    tag_list("size", TAG_INT, [struct.pack(">i", 1)] * 3),
    tag_list("palette", TAG_COMPOUND, [compound_payload(tag_string("Name", "minecraft:air"))]),
    tag_list("blocks", TAG_COMPOUND, [compound_payload(
        tag_list("pos", TAG_INT, [struct.pack(">i", 0)] * 3),
        tag_int("state", 0))]),
    tag_list("entities", TAG_END, []),
)
data = bytes([TAG_COMPOUND]) + name("") + root

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "data", "brokenworld", "structures", "empty.nbt")
os.makedirs(os.path.dirname(out), exist_ok=True)
with gzip.open(out, "wb") as f:
    f.write(data)
print("wrote", out)
