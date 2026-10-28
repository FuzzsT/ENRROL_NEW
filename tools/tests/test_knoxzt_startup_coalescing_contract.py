from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTROLLER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxZtStartupController.kt'
text = CONTROLLER.read_text(encoding='utf-8')

for token in [
    'private val lock = Any()',
    'private var running = false',
    'private var rerunRequested = false',
    'if (running) {',
    'rerunRequested = true',
    'while (true) {',
    'rerunRequested = false',
    'running = false',
]:
    assert token in text, f'KnoxZT startup recovery must coalesce burst enqueue calls: missing {token}'

assert text.count('executor.execute {') == 1, 'KnoxZT startup controller should submit only the first active worker, not one task per event'

print('KNOXZT_STARTUP_COALESCING_CONTRACT: PASS')
