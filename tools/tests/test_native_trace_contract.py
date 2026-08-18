from pathlib import Path
root=Path(__file__).resolve().parents[2]
module=root/'native-diagnostics'
texts='\n'.join(p.read_text(errors='ignore') for p in module.rglob('*') if p.is_file())
for required in ['CMakeLists.txt','NativeTraceBridge','monotonicNanos','pageSize','traceMarker','clock_gettime','_SC_PAGESIZE','arm64-v8a','armeabi-v7a','x86_64','x86']:
    assert required in texts, required
print('test_native_trace_contract: PASS')
