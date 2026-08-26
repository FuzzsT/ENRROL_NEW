#!/usr/bin/env python3
"""Audit Gradle project source completeness without treating legitimate NO-SOURCE tasks as failures."""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

SOURCE_SUFFIXES = {".kt", ".java", ".aidl", ".c", ".cc", ".cpp", ".cxx", ".h", ".hpp"}
NATIVE_SUFFIXES = {".c", ".cc", ".cpp", ".cxx", ".h", ".hpp"}


def parse_settings(settings: Path):
    text = settings.read_text(encoding="utf-8")
    match = re.search(r"\binclude\s*\((.*?)\)\s*", text, re.S)
    include_ids = re.findall(r'"(:[^"]+)"', match.group(1)) if match else []
    mappings = dict(
        re.findall(
            r'project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)',
            text,
        )
    )
    return include_ids, mappings


def count_files(base: Path, predicate) -> int:
    if not base.exists():
        return 0
    return sum(1 for p in base.rglob("*") if p.is_file() and predicate(p))


def audit(root: Path) -> dict:
    settings = root / "settings.gradle.kts"
    blockers: list[str] = []
    if not settings.is_file():
        return {
            "schemaVersion": 1,
            "state": "SOURCE_INCOMPLETE",
            "moduleCount": 0,
            "duplicateIncludes": [],
            "blockers": ["MISSING_SETTINGS_GRADLE_KTS"],
            "modules": [],
        }

    include_ids, mappings = parse_settings(settings)
    duplicates = sorted(k for k, n in Counter(include_ids).items() if n > 1)
    if duplicates:
        blockers.extend(f"DUPLICATE_INCLUDE:{x}" for x in duplicates)

    modules = []
    for project_id in dict.fromkeys(include_ids):
        rel_dir = mappings.get(project_id, project_id.lstrip(":").replace(":", "/"))
        project_dir = root / rel_dir
        build_file = project_dir / "build.gradle.kts"
        main = project_dir / "src" / "main"
        build_text = build_file.read_text(encoding="utf-8", errors="replace") if build_file.is_file() else ""
        android = bool(re.search(r"com\.android\.(application|library)|libs\.plugins\.android\.(application|library)", build_text))

        kotlin = count_files(main, lambda p: p.suffix == ".kt")
        java = count_files(main, lambda p: p.suffix == ".java")
        aidl = count_files(main, lambda p: p.suffix == ".aidl")
        native = count_files(main, lambda p: p.suffix.lower() in NATIVE_SUFFIXES)
        resources = count_files(main / "resources", lambda p: True)
        res = count_files(main / "res", lambda p: True)
        manifest = (main / "AndroidManifest.xml").is_file()
        source_files = kotlin + java + aidl + native + resources + res + int(manifest)

        if android:
            if native:
                classification = "android-native"
            elif kotlin and java:
                classification = "android-mixed"
            elif kotlin:
                classification = "android-kotlin"
            elif java:
                classification = "android-java"
            elif aidl or res or manifest:
                classification = "android-resource-or-aidl"
            else:
                classification = "android-empty"
        else:
            if kotlin and java:
                classification = "mixed-jvm"
            elif kotlin:
                classification = "kotlin-only"
            elif java:
                classification = "java-only"
            elif resources:
                classification = "resource-only"
            else:
                classification = "empty"

        expected_no_source = []
        if java == 0:
            expected_no_source.append("compileJava")
            if android:
                expected_no_source.append("compile<Variant>JavaWithJavac")
        if not android and resources == 0:
            expected_no_source.append("processResources")
        if android and native == 0:
            expected_no_source.append("merge<Variant>NativeLibs")

        module_blockers = []
        if not project_dir.is_dir():
            module_blockers.append("MISSING_PROJECT_DIR")
        if not build_file.is_file():
            module_blockers.append("MISSING_BUILD_FILE")
        if source_files == 0:
            module_blockers.append("EMPTY_MAIN_SOURCE")
        blockers.extend(f"{project_id}:{x}" for x in module_blockers)

        modules.append(
            {
                "project": project_id,
                "projectDir": rel_dir,
                "classification": classification,
                "hasBuildFile": build_file.is_file(),
                "hasMainSource": source_files > 0,
                "sourceCounts": {
                    "kotlin": kotlin,
                    "java": java,
                    "aidl": aidl,
                    "native": native,
                    "resources": resources,
                    "res": res,
                    "manifest": int(manifest),
                },
                "expectedNoSourceTasks": expected_no_source,
                "blockers": module_blockers,
            }
        )

    mapped_not_included = sorted(set(mappings) - set(include_ids))
    for project_id in mapped_not_included:
        blockers.append(f"MAPPED_NOT_INCLUDED:{project_id}")

    return {
        "schemaVersion": 1,
        "state": "SOURCE_COMPLETE" if not blockers else "SOURCE_INCOMPLETE",
        "moduleCount": len(modules),
        "duplicateIncludes": duplicates,
        "mappedNotIncluded": mapped_not_included,
        "blockers": blockers,
        "modules": modules,
        "note": "Gradle NO-SOURCE is expected when a task has no inputs for that source type; it is not equivalent to an empty project module.",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--json-out")
    args = parser.parse_args()

    report = audit(Path(args.root).resolve())
    payload = json.dumps(report, indent=2, sort_keys=True)
    if args.json_out:
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(payload + "\n", encoding="utf-8")
    if args.json or not args.json_out:
        print(payload)
    return 0 if report["state"] == "SOURCE_COMPLETE" else 3


if __name__ == "__main__":
    raise SystemExit(main())
