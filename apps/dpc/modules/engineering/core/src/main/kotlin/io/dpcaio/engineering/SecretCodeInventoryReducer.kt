package io.dpcaio.engineering

object SecretCodeInventoryReducer {
    fun reduce(
        packageName: String,
        declarations: List<SecretCodeDeclaration>,
        liveReceivers: List<SecretCodeLiveReceiver>,
    ): List<SecretCodeInventoryRecord> {
        return declarations
            .groupBy { declaration ->
                DedupKey(
                    receiverClassName = canonicalClassName(packageName, declaration.receiverClassName),
                    codeKey = declaration.normalizedCode ?: declaration.rawHost,
                    scheme = declaration.scheme,
                    declarationKind = declaration.declarationKind,
                )
            }
            .map { (key, grouped) ->
                val combinedActions = grouped.flatMapTo(linkedSetOf()) { it.actions }
                val matchingLive = liveReceivers.filter { live ->
                    live.packageName == packageName &&
                        canonicalClassName(packageName, live.className) == key.receiverClassName &&
                        live.action in combinedActions
                }
                val manifestPermission = grouped.firstNotNullOfOrNull { it.requiredPermission?.takeIf(String::isNotBlank) }
                val livePermission = matchingLive.firstNotNullOfOrNull { it.requiredPermission?.takeIf(String::isNotBlank) }
                val permission = manifestPermission ?: livePermission
                val enabled = when {
                    grouped.any { it.enabled == false } -> false
                    matchingLive.any { !it.enabled } -> false
                    matchingLive.isNotEmpty() -> true
                    else -> grouped.firstNotNullOfOrNull { it.enabled }
                }
                val exported = when {
                    grouped.any { it.exported == false } -> false
                    matchingLive.any { !it.exported } -> false
                    matchingLive.isNotEmpty() -> true
                    else -> grouped.firstNotNullOfOrNull { it.exported }
                }
                val status = when {
                    grouped.any { it.declarationKind == SecretCodeDeclarationKind.PATTERN_ONLY } ->
                        SecretCodeInventoryStatus.PATTERN_ONLY
                    enabled == false -> SecretCodeInventoryStatus.DISABLED
                    permission != null || exported == false -> SecretCodeInventoryStatus.PERMISSION_GATED
                    matchingLive.isNotEmpty() -> SecretCodeInventoryStatus.VERIFIED
                    else -> SecretCodeInventoryStatus.PERMISSION_GATED
                }
                val representative = grouped.first()
                SecretCodeInventoryRecord(
                    packageName = packageName,
                    code = representative.normalizedCode,
                    rawHost = representative.rawHost,
                    receiverClassName = key.receiverClassName,
                    actions = combinedActions,
                    scheme = representative.scheme,
                    status = status,
                    enabled = enabled,
                    exported = exported,
                    requiredPermission = permission,
                    sourceKinds = grouped.mapTo(linkedSetOf()) { it.sourceKind },
                    sourceApkNames = grouped.mapTo(linkedSetOf()) { it.sourceApkName },
                )
            }
            .sortedWith(
                compareBy<SecretCodeInventoryRecord> { it.packageName }
                    .thenBy { it.code ?: it.rawHost ?: "" }
                    .thenBy { it.receiverClassName }
            )
    }

    private fun canonicalClassName(packageName: String, className: String): String = when {
        className.startsWith('.') -> packageName + className
        '.' !in className -> "$packageName.$className"
        else -> className
    }

    private data class DedupKey(
        val receiverClassName: String,
        val codeKey: String?,
        val scheme: String?,
        val declarationKind: SecretCodeDeclarationKind,
    )
}
