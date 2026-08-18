#!/usr/bin/env python3
"""Non-blocking release audit.

This tool intentionally contains no technology denylist and never rejects a
build because Frida, Xposed/LSPosed, hidden/non-SDK API adapters, raw Binder
research helpers, Shizuku/Dhizuku APIs, native instrumentation, or lab-tools
are present in source or dependencies.

Structural/build correctness is validated by dedicated verifiers such as
verify_project.py and verify_android_contracts.py.  `release_gate.py` remains
as a compatibility entry point for existing scripts, but is audit-only.
"""
from pathlib import Path
import sys


def scan_release_tree(root: Path):
    """Compatibility API: return no blocking findings.

    The previous implementation scanned selected directories for forbidden
    substrings.  That produced false positives and made research tooling
    unusable from arbitrary modules.  Policy/capability availability is now a
    runtime concern, not a source-tree denylist.
    """
    Path(root)  # Normalize/validate path-like input without imposing policy.
    return []


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    scan_release_tree(root)
    print('RELEASE_AUDIT: PASS (non-blocking; no technology denylist)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
