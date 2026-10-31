package io.dpcaio.engineering

private fun declaration(
    code: String? = "1234",
    enabled: Boolean? = true,
    permission: String? = null,
    sourceKind: SecretCodeSourceKind = SecretCodeSourceKind.BASE_APK,
    sourceApkName: String = "base.apk",
    actions: Set<String> = setOf(SecretCodeManifestExtractor.CURRENT_SECRET_CODE_ACTION),
    kind: SecretCodeDeclarationKind = if (code == null) SecretCodeDeclarationKind.PATTERN_ONLY else SecretCodeDeclarationKind.LITERAL_HOST,
): SecretCodeDeclaration = SecretCodeDeclaration(
    rawHost = code ?: "*",
    normalizedCode = code,
    receiverClassName = ".SecretReceiver",
    actions = actions,
    scheme = SecretCodeManifestExtractor.SECRET_CODE_SCHEME,
    enabled = enabled,
    exported = true,
    requiredPermission = permission,
    sourceKind = sourceKind,
    sourceApkName = sourceApkName,
    declarationKind = kind,
)

private fun live(
    enabled: Boolean = true,
    permission: String? = null,
    action: String = SecretCodeManifestExtractor.CURRENT_SECRET_CODE_ACTION,
): SecretCodeLiveReceiver = SecretCodeLiveReceiver(
    packageName = "com.example.secret",
    className = ".SecretReceiver",
    action = action,
    enabled = enabled,
    exported = true,
    requiredPermission = permission,
)

fun main() {
    val verified = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(declaration()),
        liveReceivers = listOf(live()),
    ).single()
    check(verified.status == SecretCodeInventoryStatus.VERIFIED)

    val protected = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(declaration(permission = "com.example.PRIVILEGED")),
        liveReceivers = listOf(live(permission = "com.example.PRIVILEGED")),
    ).single()
    check(protected.status == SecretCodeInventoryStatus.PERMISSION_GATED)

    val disabled = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(declaration(enabled = false)),
        liveReceivers = listOf(live(enabled = false)),
    ).single()
    check(disabled.status == SecretCodeInventoryStatus.DISABLED)

    val pattern = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(declaration(code = null)),
        liveReceivers = emptyList(),
    ).single()
    check(pattern.status == SecretCodeInventoryStatus.PATTERN_ONLY)

    val notVisible = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(declaration()),
        liveReceivers = emptyList(),
    ).single()
    check(notVisible.status == SecretCodeInventoryStatus.PERMISSION_GATED)

    val duplicateBase = declaration()
    val duplicateSplit = declaration(
        sourceKind = SecretCodeSourceKind.SPLIT_APK,
        sourceApkName = "split_config.apk",
    )
    val deduped = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(duplicateBase, duplicateSplit),
        liveReceivers = listOf(live()),
    )
    check(deduped.size == 1)
    check(deduped.single().sourceKinds == setOf(SecretCodeSourceKind.BASE_APK, SecretCodeSourceKind.SPLIT_APK))
    check(deduped.single().sourceApkNames == setOf("base.apk", "split_config.apk"))

    val legacySplit = declaration(
        sourceKind = SecretCodeSourceKind.SPLIT_APK,
        sourceApkName = "split_legacy.apk",
        actions = setOf(SecretCodeManifestExtractor.LEGACY_SECRET_CODE_ACTION),
    )
    val crossAction = SecretCodeInventoryReducer.reduce(
        packageName = "com.example.secret",
        declarations = listOf(duplicateBase, legacySplit),
        liveReceivers = listOf(
            live(),
            live(action = SecretCodeManifestExtractor.LEGACY_SECRET_CODE_ACTION),
        ),
    )
    check(crossAction.size == 1)
    check(
        crossAction.single().actions == setOf(
            SecretCodeManifestExtractor.CURRENT_SECRET_CODE_ACTION,
            SecretCodeManifestExtractor.LEGACY_SECRET_CODE_ACTION,
        )
    )

    println("SecretCodeInventoryReducerTest: PASS")
}
