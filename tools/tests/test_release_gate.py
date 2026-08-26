#!/usr/bin/env python3
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from release_gate import scan_release_tree


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def main():
    with TemporaryDirectory() as td:
        root = Path(td)
        source = root / 'apps/dpc/app/src/main/Research.kt'
        source.parent.mkdir(parents=True)
        source.write_text(
            '\n'.join([
                'import de.robv.android.xposed.XposedBridge',
                'import org.lsposed.SomeApi',
                'val a = "frida-gadget"',
                'val b = "HiddenApiBypass"',
                'val c = "DISABLE_HIDDEN_API_CHECKS"',
                'val d = "setHiddenApiExemptions"',
                'val e = "Shizuku.newProcess("',
                'val f = "DhizukuProcess.get()"',
                'val g = "signature spoof"',
                'val h = "TracerPid spoof"',
            ]),
            encoding='utf-8',
        )
        gradle = root / 'apps/dpc/app/build.gradle.kts'
        gradle.parent.mkdir(parents=True, exist_ok=True)
        gradle.write_text('dependencies { implementation(project(":lab-tools")) }', encoding='utf-8')

        findings = scan_release_tree(root)
        assert_true(findings == [], 'release gate must not block or flag technologies/tools by denylist')

        completed = subprocess.run(
            [sys.executable, str(ROOT / 'tools/release_gate.py'), str(root)],
            text=True,
            capture_output=True,
            check=False,
        )
        assert_true(completed.returncode == 0, 'release gate must always be non-blocking')
        assert_true('FAIL' not in completed.stdout, 'release gate must not emit FAIL for technology/tool usage')

    print('test_release_gate: PASS')


if __name__ == '__main__':
    main()
