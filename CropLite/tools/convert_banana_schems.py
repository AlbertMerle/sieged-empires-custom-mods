#!/usr/bin/env python3
"""Convert WorldEdit Sponge Schematic v3 (.schem) to vanilla structure .nbt.

Vanilla StructureTemplate expects LIST-of-INT for `size` and each block `pos`.
INT_ARRAY for those fields loads as empty → size (0,0,0) → trees never place.
"""
from __future__ import annotations

import gzip
import io
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEM_DIR = ROOT / "NewCrops" / "banana"
OUT_DIR = ROOT / "src" / "main" / "resources" / "data" / "croplite" / "structure"
PIVOTS_FILE = ROOT / "tools" / "banana_tree_pivots.txt"

MAP = {
	"bananatree1.schem": "banana_tree_1.nbt",
	"bananatree2.schem": "banana_tree_2.nbt",
	"bananatree3.schem": "banana_tree_3.nbt",
	"bananatree4.schem": "banana_tree_4.nbt",
	"bananatreelarge.schem": "banana_tree_large.nbt",
}

BLOCK_REMAP = {
	"minecraft:jungle_log": "croplite:banana_stalk",
	"minecraft:jungle_wood": "croplite:banana_stalk",
	"minecraft:stripped_jungle_log": "croplite:banana_stalk",
	"minecraft:jungle_leaves": "croplite:banana_leaves",
}


class NbtWriter:
	def __init__(self) -> None:
		self.buf = io.BytesIO()

	def u8(self, v: int) -> None:
		self.buf.write(struct.pack(">B", v))

	def i16(self, v: int) -> None:
		self.buf.write(struct.pack(">h", v))

	def i32(self, v: int) -> None:
		self.buf.write(struct.pack(">i", v))

	def string(self, s: str) -> None:
		b = s.encode("utf-8")
		self.i16(len(b))
		self.buf.write(b)

	def tag_header(self, tag: int, name: str) -> None:
		self.u8(tag)
		self.string(name)

	def end(self) -> None:
		self.u8(0)

	def compound(self, name: str, fn) -> None:
		self.tag_header(10, name)
		fn()
		self.end()

	def list_int(self, name: str, values: list[int]) -> None:
		self.tag_header(9, name)
		self.u8(3)
		self.i32(len(values))
		for v in values:
			self.i32(v)

	def list_compound(self, name: str, writers: list) -> None:
		self.tag_header(9, name)
		self.u8(10)
		self.i32(len(writers))
		for w in writers:
			w()
			self.end()

	def string_tag(self, name: str, value: str) -> None:
		self.tag_header(8, name)
		self.string(value)

	def int_tag(self, name: str, value: int) -> None:
		self.tag_header(3, name)
		self.i32(value)

	def bytes(self) -> bytes:
		return self.buf.getvalue()


def read_nbt(data: bytes):
	# Use a 1-element list so nested closures share the cursor reliably.
	cur = [0]

	def u8():
		b = data[cur[0]]
		cur[0] += 1
		return b

	def i16():
		v = struct.unpack_from(">h", data, cur[0])[0]
		cur[0] += 2
		return v

	def i32():
		v = struct.unpack_from(">i", data, cur[0])[0]
		cur[0] += 4
		return v

	def i64():
		v = struct.unpack_from(">q", data, cur[0])[0]
		cur[0] += 8
		return v

	def f32():
		v = struct.unpack_from(">f", data, cur[0])[0]
		cur[0] += 4
		return v

	def f64():
		v = struct.unpack_from(">d", data, cur[0])[0]
		cur[0] += 8
		return v

	def read_string():
		n = i16()
		s = data[cur[0] : cur[0] + n].decode("utf-8")
		cur[0] += n
		return s

	def read_payload(tag: int):
		if tag == 0:
			return None
		if tag == 1:
			v = struct.unpack_from(">b", data, cur[0])[0]
			cur[0] += 1
			return v
		if tag == 2:
			return i16()
		if tag == 3:
			return i32()
		if tag == 4:
			return i64()
		if tag == 5:
			return f32()
		if tag == 6:
			return f64()
		if tag == 7:
			n = i32()
			b = data[cur[0] : cur[0] + n]
			cur[0] += n
			return b
		if tag == 8:
			return read_string()
		if tag == 9:
			et = u8()
			n = i32()
			return [read_payload(et) for _ in range(n)]
		if tag == 10:
			d = {}
			while True:
				t = u8()
				if t == 0:
					break
				# Key must be read before value: on Python 3.14+, `d[k()]=v()`
				# evaluates the value expression first and desyncs the cursor.
				key = read_string()
				d[key] = read_payload(t)
			return d
		if tag == 11:
			n = i32()
			return [i32() for _ in range(n)]
		if tag == 12:
			n = i32()
			return [i64() for _ in range(n)]
		raise ValueError(tag)

	t = u8()
	name = read_string()
	return name, read_payload(t)


def decode_varints(data: bytes) -> list[int]:
	idxs: list[int] = []
	i = 0
	while i < len(data):
		num = 0
		shift = 0
		while True:
			b = data[i]
			i += 1
			num |= (b & 0x7F) << shift
			if not (b & 0x80):
				break
			shift += 7
		idxs.append(num)
	return idxs


def parse_block_state(s: str) -> tuple[str, dict[str, str]]:
	if "[" not in s:
		return s, {}
	name, rest = s.split("[", 1)
	rest = rest.rstrip("]")
	props: dict[str, str] = {}
	if rest:
		for part in rest.split(","):
			k, v = part.split("=", 1)
			props[k] = v
	return name, props


def remap_state(state_str: str) -> tuple[str, dict[str, str]]:
	name, props = parse_block_state(state_str)
	name = BLOCK_REMAP.get(name, name)
	if name == "croplite:banana_leaves":
		props = {
			"distance": props.get("distance", "7"),
			"persistent": "false",
			"waterlogged": props.get("waterlogged", "false"),
		}
	elif name == "croplite:banana_stalk":
		props = {"axis": props.get("axis", "y")}
	return name, props


def convert_one(schem_path: Path, nbt_name: str) -> tuple[int, int, int]:
	raw = gzip.decompress(schem_path.read_bytes())
	_, root = read_nbt(raw)
	sch = root["Schematic"]
	w, h, length = sch["Width"], sch["Height"], sch["Length"]
	blocks = sch["Blocks"]
	palette_in = blocks["Palette"]
	idxs = decode_varints(blocks["Data"])
	if len(idxs) != w * h * length:
		raise SystemExit(f"{schem_path.name}: block count {len(idxs)} != {w*h*length}")

	inv = {v: k for k, v in palette_in.items()}
	palette: list[tuple[str, dict[str, str]]] = []
	palette_index: dict[tuple, int] = {}
	block_entries: list[tuple[int, int, int, int]] = []
	trunk_bases: list[tuple[int, int, int]] = []

	for i, idx in enumerate(idxs):
		state_str = inv[idx]
		if state_str.split("[")[0] == "minecraft:air":
			continue
		x = i % w
		z = (i // w) % length
		y = i // (w * length)
		name, props = remap_state(state_str)
		key = (name, tuple(sorted(props.items())))
		if key not in palette_index:
			palette_index[key] = len(palette)
			palette.append((name, props))
		block_entries.append((x, y, z, palette_index[key]))
		if name == "croplite:banana_stalk" and props.get("axis", "y") == "y":
			trunk_bases.append((x, y, z))

	if not trunk_bases:
		raise SystemExit(f"No banana stalk trunk in {schem_path.name}")

	min_y = min(p[1] for p in trunk_bases)
	bases = [p for p in trunk_bases if p[1] == min_y]
	cx, cz = (w - 1) / 2, (length - 1) / 2
	bases.sort(key=lambda p: (p[0] - cx) ** 2 + (p[2] - cz) ** 2)
	pivot = bases[0]

	writer = NbtWriter()

	def root_body() -> None:
		writer.list_int("size", [w, h, length])

		def palette_entry(name: str, props: dict[str, str]):
			def entry() -> None:
				writer.string_tag("Name", name)
				if props:
					def prop_body() -> None:
						for k, v in props.items():
							writer.string_tag(k, v)

					writer.compound("Properties", prop_body)

			return entry

		writer.list_compound("palette", [palette_entry(n, p) for n, p in palette])

		def block_entry(x: int, y: int, z: int, state: int):
			def entry() -> None:
				writer.list_int("pos", [x, y, z])
				writer.int_tag("state", state)

			return entry

		writer.list_compound("blocks", [block_entry(x, y, z, s) for x, y, z, s in block_entries])
		writer.list_compound("entities", [])
		writer.int_tag("DataVersion", int(sch.get("DataVersion", 4440)))

	writer.compound("", root_body)
	out_path = OUT_DIR / nbt_name
	out_path.write_bytes(gzip.compress(writer.bytes(), mtime=0))
	print(
		f"{schem_path.name} -> {nbt_name}: "
		f"size={w}x{h}x{length} blocks={len(block_entries)} "
		f"palette={len(palette)} pivot={pivot}"
	)
	return pivot


def verify_list_tags(nbt_path: Path) -> None:
	raw = gzip.decompress(nbt_path.read_bytes())
	pos = 0

	def u8():
		nonlocal pos
		b = raw[pos]
		pos += 1
		return b

	def i16():
		nonlocal pos
		v = struct.unpack_from(">h", raw, pos)[0]
		pos += 2
		return v

	def i32():
		nonlocal pos
		v = struct.unpack_from(">i", raw, pos)[0]
		pos += 4
		return v

	def skip(tag: int) -> None:
		nonlocal pos
		if tag == 1:
			pos += 1
		elif tag == 2:
			pos += 2
		elif tag == 3:
			pos += 4
		elif tag == 4:
			pos += 8
		elif tag == 5:
			pos += 4
		elif tag == 6:
			pos += 8
		elif tag == 7:
			n = i32()
			pos += n
		elif tag == 8:
			n = i16()
			pos += n
		elif tag == 9:
			et = u8()
			n = i32()
			for _ in range(n):
				skip(et)
		elif tag == 10:
			while True:
				t = u8()
				if t == 0:
					break
				ln = i16()
				pos += ln
				skip(t)
		elif tag == 11:
			n = i32()
			pos += 4 * n
		elif tag == 12:
			n = i32()
			pos += 8 * n
		else:
			raise ValueError(tag)

	t = u8()
	assert t == 10
	ln = i16()
	pos += ln
	saw_size = False
	saw_pos_list = False
	while True:
		t = u8()
		if t == 0:
			break
		ln = i16()
		name = raw[pos : pos + ln].decode()
		pos += ln
		if name == "size":
			if t != 9:
				raise SystemExit(f"{nbt_path.name}: size must be LIST, got tag {t}")
			et = u8()
			n = i32()
			vals = [i32() for _ in range(n)]
			if et != 3 or n != 3:
				raise SystemExit(f"{nbt_path.name}: bad size list {et=} {n=} {vals}")
			saw_size = True
			print(f"  OK size LIST {vals}")
		elif name == "blocks":
			if t != 9:
				raise SystemExit(f"{nbt_path.name}: blocks must be LIST")
			et = u8()
			n = i32()
			# Inspect first compound's pos tag
			tt = u8()
			ln = i16()
			field = raw[pos : pos + ln].decode()
			pos += ln
			if field != "pos" or tt != 9:
				raise SystemExit(f"{nbt_path.name}: first block pos must be LIST, got {tt} {field}")
			pet = u8()
			pn = i32()
			pvals = [i32() for _ in range(pn)]
			if pet != 3:
				raise SystemExit(f"{nbt_path.name}: pos list element type {pet}")
			saw_pos_list = True
			print(f"  OK block pos LIST {pvals} (and {n} blocks)")
			# Don't bother finishing full parse
			return
		elif t == 11:
			raise SystemExit(f"{nbt_path.name}: unexpected INT_ARRAY field {name!r}")
		else:
			skip(t)
	if not saw_size or not saw_pos_list:
		raise SystemExit(f"{nbt_path.name}: incomplete verification")


def main() -> None:
	OUT_DIR.mkdir(parents=True, exist_ok=True)
	pivots: dict[str, tuple[int, int, int]] = {}
	for schem_name, nbt_name in MAP.items():
		pivot = convert_one(SCHEM_DIR / schem_name, nbt_name)
		pivots[nbt_name.removesuffix(".nbt")] = pivot

	lines = [f"{name}={p[0]},{p[1]},{p[2]}" for name, p in pivots.items()]
	PIVOTS_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
	print(f"Wrote {PIVOTS_FILE}")

	print("\nVerifying:")
	for nbt_name in MAP.values():
		print(nbt_name)
		verify_list_tags(OUT_DIR / nbt_name)


if __name__ == "__main__":
	main()
