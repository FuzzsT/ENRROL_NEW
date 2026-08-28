#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/parity"))
from extract_testdpc_direct_keys import direct_keys  # noqa: E402

CATALOG = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/TestDpcParityCatalog.kt"


def main() -> None:
    upstream_keys = direct_keys()
    assert len(upstream_keys) == 169, len(upstream_keys)
    assert len(set(upstream_keys)) == 169
    assert CATALOG.is_file(), CATALOG
    catalog_text = CATALOG.read_text("utf-8")
    catalog_keys = re.findall(r'testDpcKey\s*=\s*"([^"]+)"', catalog_text)
    assert len(catalog_keys) == 169, len(catalog_keys)
    assert set(catalog_keys) == set(upstream_keys), (
        sorted(set(upstream_keys) - set(catalog_keys)),
        sorted(set(catalog_keys) - set(upstream_keys)),
    )
    assert len(catalog_keys) == len(set(catalog_keys))
    print("TESTDPC_PARITY_CATALOG_169: PASS")


if __name__ == "__main__":
    main()
