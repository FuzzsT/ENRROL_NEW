# DPC-AIO Companion plugin validation

The plugin package is rooted at `plugins/chatgpt-companion/`. Plugin validation is independent from Android APK compilation.

## Fast checks

```bash
python3 tools/plugin/test_plugin_contract.py
python3 tools/plugin/test_plugin_scripts.py
```

## Full plugin gate

```bash
rm -rf /tmp/dpc-aio-plugin-release
python3 tools/plugin/run_plugin_checks.py --output-dir /tmp/dpc-aio-plugin-release
```

The orchestrator runs the structural/script tests, validates `plugins/chatgpt-companion/` with the vendored Plugin Autopilot-compatible validator and every exclusion in `tools/plugin/public_exclusions.txt`, packages the plugin twice, compares bytes and SHA-256, inspects the ZIP root, rejects forbidden archive members, and writes `validation-report.json`.

A successful plugin gate means the **skills-only plugin package** passed its packaging checks. It does not prove `:app-dpc:assembleEnterpriseDebug` or another Android variant compiled. APK build success requires a separate Gradle assemble command to exit 0.

Repository-native host checks remain separate:

```bash
python3 tools/verify_project.py
python3 tools/verify_android_contracts.py
python3 tools/release_gate.py
```
