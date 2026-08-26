package io.dpcaio.policy

fun main() {
    val planner = CredentialRecoveryPlanner()
    check(planner.state(CredentialRecoveryEvidence(false, false, false, false, true)) == CredentialRecoveryState.NOT_PROVISIONED)
    check(planner.state(CredentialRecoveryEvidence(true, false, true, false, true)) == CredentialRecoveryState.PROVISIONED_PENDING_CONFIRMATION)
    check(planner.state(CredentialRecoveryEvidence(true, true, true, false, true)) == CredentialRecoveryState.ACTIVE)
    check(planner.state(CredentialRecoveryEvidence(true, false, false, false, true)) == CredentialRecoveryState.LOST_BEFORE_ACTIVATION)
    check(planner.state(CredentialRecoveryEvidence(false, false, false, true, true)) == CredentialRecoveryState.REVOKED)
    check(planner.state(CredentialRecoveryEvidence(false, false, false, false, false)) == CredentialRecoveryState.UNSUPPORTED_SECURE_LOCK_SCREEN)
    println("CredentialRecoveryTest: PASS")
}
