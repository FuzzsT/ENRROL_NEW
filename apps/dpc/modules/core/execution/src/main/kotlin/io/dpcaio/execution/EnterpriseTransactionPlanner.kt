package io.dpcaio.execution

import java.security.MessageDigest

class EnterpriseTransactionPlanner {
    fun preview(operation: EnterpriseOperation): EnterprisePlan {
        val material = listOf(operation.id, operation.targetId, operation.preState.orEmpty(), operation.desiredState.orEmpty(), operation.protectionDecision.name).joinToString("\u001f")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return EnterprisePlan(operation, hash, "op:${operation.id}:$hash", EnterpriseTransactionState.PREVIEWED)
    }
}
