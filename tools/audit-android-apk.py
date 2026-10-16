#!/usr/bin/env python3
"""Read-only structural audit for the Javity Android APK; never a runtime/device test."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any

EXPECTED_ABIS = {"arm64-v8a", "x86_64"}
# Names verified from libGDX 1.14.2 Android native classifier output and the actual Yoga/QuickJS AARs.
REQUIRED_NATIVE_NAMES = {"libgdx.so", "libgdx-freetype.so", "libyoga.so", "libquickjs.so"}
NATIVE_PROVENANCE = {
    "libgdx.so": "libGDX 1.14.2 gdx-platform Android native classifier (local x86_64 classifier jar confirms basename)",
    "libgdx-freetype.so": "libGDX 1.14.2 gdx-freetype-platform Android native classifier",
    "libyoga.so": "com.facebook.yoga:yoga:3.2.1 (variant-aware Android AAR), jni/<abi>/libyoga.so",
    "libquickjs.so": "io.github.dokar3:quickjs-kt-android:1.0.15, jni/<abi>/libquickjs.so",
}
ABI_ELF = {
    "arm64-v8a": (2, 183, "ELF64 AArch64"),
    "x86_64": (2, 62, "ELF64 x86-64"),
}
MIN_LOAD_ALIGNMENT = 16 * 1024
FORBIDDEN_DEX_PATTERNS = [
    ("LWJGL", re.compile(rb"(?:org[/.]lwjgl|lwjgl[-.])", re.I)),
    ("GraalVM", re.compile(rb"(?:org[/.]graalvm|graaljs|graal[-.]js)", re.I)),
    ("Truffle", re.compile(rb"(?:com[/.]oracle[/.]truffle|org[/.]graalvm[/.]truffle|truffle[-.]api)", re.I)),
    ("MapLibre/Mapbox desktop artifact", re.compile(rb"(?:org[/.]maplibre|com[/.]mapbox|maplibre-native-ffi)", re.I)),
]
FORBIDDEN_ZIP_NAMES = re.compile(
    r"(?:^|[/_.-])(?:lwjgl|graalvm|graaljs|truffle|maplibre-native-ffi)(?:[/_.-]|$)", re.I
)


def elf_load_segments(data: bytes) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF shared library")
    elf_class, elf_data = data[4], data[5]
    if elf_class not in (1, 2) or elf_data not in (1, 2):
        raise ValueError("unsupported ELF class or byte order")
    endian = "<" if elf_data == 1 else ">"
    machine = struct.unpack_from(endian + "H", data, 18)[0]
    if elf_class == 1:
        phoff = struct.unpack_from(endian + "I", data, 28)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 42)
        fmt, minimum_size = endian + "IIIIIIII", 32
    else:
        phoff = struct.unpack_from(endian + "Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54)
        fmt, minimum_size = endian + "IIQQQQQQ", 56
    if phentsize < minimum_size or phoff + phentsize * phnum > len(data):
        raise ValueError("invalid ELF program-header table")
    loads = []
    for index in range(phnum):
        row = struct.unpack_from(fmt, data, phoff + index * phentsize)
        if elf_class == 1:
            p_type, p_offset, p_vaddr, _paddr, _filesz, _memsz, _flags, p_align = row
        else:
            p_type, _flags, p_offset, p_vaddr, _paddr, _filesz, _memsz, p_align = row
        if p_type == 1:  # PT_LOAD
            loads.append({"index": index, "p_offset": p_offset, "p_vaddr": p_vaddr, "p_align": p_align,
                          "vaddr_offset_congruent": (p_vaddr % max(1, p_align)) == (p_offset % max(1, p_align))})
    if not loads:
        raise ValueError("ELF has no PT_LOAD segments")
    return {"class": elf_class, "machine": machine, "byte_order": "little" if elf_data == 1 else "big"}, loads


def zip_entry_data_offset(apk: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    fp = apk.fp
    fp.seek(info.header_offset)
    header = fp.read(30)
    if len(header) != 30:
        raise ValueError("truncated local ZIP header")
    fields = struct.unpack("<IHHHHHIIIHH", header)
    signature, name_length, extra_length = fields[0], fields[-2], fields[-1]
    if signature != 0x04034B50:
        raise ValueError("invalid local ZIP header signature")
    return info.header_offset + 30 + name_length + extra_length


def audit(apk_path: Path, zipalign_path: str | None) -> dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    report: dict[str, Any] = {
        "apk": str(apk_path.resolve()),
        "structural_audit_passed": False,
        "runtime_proof": False,
        "scope": "APK ZIP/ELF/Dex structure only; no install, launch, ABI startup, rendering, or device behavior is proven.",
        "expected_abis": sorted(EXPECTED_ABIS),
        "required_native_library_provenance": NATIVE_PROVENANCE,
        "abi_directories": [],
        "native_libraries": [],
        "forbidden_artifacts": [],
        "zipalign_check": {"status": "not_run"},
        "errors": errors,
        "warnings": warnings,
    }
    if not apk_path.is_file():
        errors.append(f"APK does not exist: {apk_path}")
        return report
    try:
        zf = zipfile.ZipFile(apk_path)
    except (OSError, zipfile.BadZipFile) as exc:
        errors.append(f"cannot read APK ZIP: {exc}")
        return report

    report["apk_sha256"] = hashlib.sha256(apk_path.read_bytes()).hexdigest()
    with zf:
        names = zf.namelist()
        notice_manifest = "assets/android/third-party/manifest.json"
        report["notice_files_verified"] = 0
        if notice_manifest not in names:
            errors.append("Android third-party license manifest is missing")
        else:
            try:
                notice_files = json.loads(zf.read(notice_manifest))["files"]
                for notice in notice_files:
                    entry = "assets/" + notice["path"]
                    if entry not in names:
                        errors.append("Missing license/notice: " + entry)
                    elif hashlib.sha256(zf.read(entry)).hexdigest() != notice["sha256"]:
                        errors.append("License/notice hash mismatch: " + entry)
                    else:
                        report["notice_files_verified"] += 1
            except (ValueError, KeyError, TypeError) as failure:
                errors.append("Invalid license manifest: " + str(failure))
        native_entries = [info for info in zf.infolist()
                          if info.filename.startswith("lib/") and info.filename.lower().endswith(".so")]
        abi_dirs = sorted({info.filename.split("/")[1] for info in native_entries if len(info.filename.split("/")) >= 3})
        report["abi_directories"] = abi_dirs
        if set(abi_dirs) != EXPECTED_ABIS:
            errors.append(f"native ABI directory set is {abi_dirs}; expected exactly {sorted(EXPECTED_ABIS)}")

        libs_by_abi: dict[str, set[str]] = {abi: set() for abi in abi_dirs}
        for info in native_entries:
            parts = info.filename.split("/")
            if len(parts) != 3:
                errors.append(f"native library entry must be exactly lib/<abi>/<name>.so: {info.filename}")
                continue
            _, abi, name = parts
            libs_by_abi.setdefault(abi, set()).add(name)
            record: dict[str, Any] = {
                "entry": info.filename,
                "compressed": info.compress_type != zipfile.ZIP_STORED,
                "compression_method": info.compress_type,
            }
            if info.compress_type != zipfile.ZIP_STORED:
                errors.append(f"native entry must be ZIP_STORED: {info.filename}")
            try:
                offset = zip_entry_data_offset(zf, info)
                record["data_offset"] = offset
                record["data_offset_mod_16384"] = offset % MIN_LOAD_ALIGNMENT
                if offset % MIN_LOAD_ALIGNMENT:
                    errors.append(f"native entry data offset is not 16KiB aligned: {info.filename} offset={offset}")
            except (ValueError, OSError, struct.error) as exc:
                record["zip_header_error"] = str(exc)
                errors.append(f"cannot verify native ZIP data offset for {info.filename}: {exc}")
            abi_expected = ABI_ELF.get(abi)
            try:
                data = zf.read(info)
                elf, loads = elf_load_segments(data)
                record["elf"] = elf
                record["pt_load_segments"] = loads
                if abi_expected:
                    expected_class, expected_machine, architecture = abi_expected
                    record["expected_architecture"] = architecture
                    if (elf["class"], elf["machine"]) != (expected_class, expected_machine):
                        errors.append(f"{info.filename} has ELF class/machine {elf['class']}/{elf['machine']}; expected {architecture}")
                for segment in loads:
                    if segment["p_align"] < MIN_LOAD_ALIGNMENT:
                        errors.append(f"{info.filename} PT_LOAD[{segment['index']}] p_align={segment['p_align']} is below 16KiB")
                    if not segment["vaddr_offset_congruent"]:
                        errors.append(f"{info.filename} PT_LOAD[{segment['index']}] p_vaddr/p_offset violate p_align congruence")
            except (ValueError, struct.error) as exc:
                record["elf_error"] = str(exc)
                errors.append(f"cannot verify ELF structure for {info.filename}: {exc}")
            report["native_libraries"].append(record)

        for abi in sorted(EXPECTED_ABIS):
            actual = libs_by_abi.get(abi, set())
            missing = sorted(REQUIRED_NATIVE_NAMES - actual)
            if missing:
                errors.append(f"{abi} is missing required native libraries: {missing}")
            extra = sorted(actual - REQUIRED_NATIVE_NAMES)
            if extra:
                errors.append(f"{abi} has unexpected native libraries outside the Android app graph: {extra}")

        forbidden = sorted(name for name in names if FORBIDDEN_ZIP_NAMES.search(name))
        report["forbidden_artifacts"].extend({"zip_entry": name} for name in forbidden)
        if forbidden:
            errors.append(f"forbidden desktop/runtime artifacts appear as APK ZIP entries: {forbidden}")
        dex_infos = [info for info in zf.infolist() if re.fullmatch(r"classes(?:\d+)?\.dex", Path(info.filename).name)]
        dex_hits: set[str] = set()
        for info in dex_infos:
            content = zf.read(info)
            for label, pattern in FORBIDDEN_DEX_PATTERNS:
                if pattern.search(content):
                    dex_hits.add(label)
        if dex_hits:
            report["forbidden_artifacts"].extend({"dex_reference": label} for label in sorted(dex_hits))
            errors.append(f"forbidden desktop/runtime DEX references found: {sorted(dex_hits)}")
        if not dex_infos:
            warnings.append("No classes*.dex entry found; this file may be an atypical or incomplete APK")

    executable = locate_zipalign(zipalign_path)
    if executable:
        proc = subprocess.run([executable, "-c", "-P", "16", "4", str(apk_path)],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        report["zipalign_check"] = {
            "status": "exit_zero" if proc.returncode == 0 else "nonzero",
            "executable": executable,
            "exit_code": proc.returncode,
            "output": proc.stdout.strip(),
            "command": [executable, "-c", "-P", "16", "4", str(apk_path)],
        }
        if proc.returncode:
            errors.append(f"Android SDK zipalign -P 16 check failed (exit {proc.returncode})")
    else:
        report["zipalign_check"] = {"status": "not_available", "note": "Python independently checked every native entry's 16KiB data offset."}

    report["structural_audit_passed"] = not errors
    return report


def locate_zipalign(explicit: str | None) -> str | None:
    if explicit:
        return shutil.which(explicit) or (explicit if Path(explicit).is_file() else None)
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if sdk:
        candidates = sorted((Path(sdk) / "build-tools").glob("*/zipalign"), reverse=True)
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    return shutil.which("zipalign")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="built APK to inspect (does not install or run it)")
    parser.add_argument("--zipalign", help="optional Android SDK zipalign executable; auto-detected from ANDROID_SDK_ROOT/HOME")
    parser.add_argument("--output", type=Path, help="also write the JSON report to this path")
    args = parser.parse_args()
    report = audit(args.apk, args.zipalign)
    encoded = json.dumps(report, indent=2, sort_keys=True)
    print(encoded)
    if args.output:
        args.output.write_text(encoded + "\n", encoding="utf-8")
    return 0 if report["structural_audit_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
