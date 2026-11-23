"""Verify ELF and ZIP load alignment for every shipped 64-bit Android native library."""
import argparse
import struct
import zipfile
from pathlib import Path


def check(apk):
    errors = []
    checked = set()
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as stream:
        for entry in archive.infolist():
            parts = entry.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or parts[1] not in ("arm64-v8a", "x86_64") or not parts[2].endswith(".so"):
                continue
            data = archive.read(entry)
            if data[:6] != b"\x7fELF\x02\x01":
                raise ValueError(f"Invalid 64-bit ELF: {entry.filename}")
            offset = struct.unpack_from("<Q", data, 32)[0]
            size, count = struct.unpack_from("<HH", data, 54)
            loads = 0
            for i in range(count):
                kind, _, pos, address, _, _, _, alignment = struct.unpack_from("<II6Q", data, offset+i*size)
                if kind == 1:
                    loads += 1
                    if alignment < 16384 or (address-pos) % 16384:
                        errors.append(f"{entry.filename}: LOAD align={alignment}")
            if not loads:
                errors.append(f"{entry.filename}: missing LOAD segments")
            if entry.compress_type == zipfile.ZIP_STORED:
                stream.seek(entry.header_offset+26)
                name_bytes, extra_bytes = struct.unpack("<HH", stream.read(4))
                if (entry.header_offset+30+name_bytes+extra_bytes) % 16384:
                    errors.append(f"{entry.filename}: ZIP data not aligned")
            checked.add(parts[1])
            print(entry.filename, loads, "LOAD segments checked")
    if "arm64-v8a" not in checked:
        errors.append("Missing arm64 libraries")
    if errors:
        raise SystemExit("\n".join(errors))
    print("16 KB alignment passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    check(parser.parse_args().apk)
