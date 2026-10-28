from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COORDINATOR = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentCoordinator.kt'
text = COORDINATOR.read_text(encoding='utf-8')

for token in [
    'private val enrollmentRunLock = Any()',
    'fun resumeOrCreate(intent: Intent): EnrollmentOutcome = synchronized(enrollmentRunLock)',
    'private fun resumeOrCreateLocked(intent: Intent): EnrollmentOutcome',
]:
    assert token in text, f'EnrollmentCoordinator must serialize process-wide resume/create runs: missing {token}'

public_start = text.index('fun resumeOrCreate(intent: Intent): EnrollmentOutcome')
locked_start = text.index('private fun resumeOrCreateLocked(intent: Intent): EnrollmentOutcome')
assert public_start < locked_start, 'public resumeOrCreate must delegate into the serialized locked implementation'

print('ENROLLMENT_COORDINATOR_SERIALIZATION_CONTRACT: PASS')
