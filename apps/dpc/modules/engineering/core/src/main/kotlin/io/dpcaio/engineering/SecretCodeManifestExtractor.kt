package io.dpcaio.engineering

import java.nio.file.Path
import java.util.zip.ZipFile

object SecretCodeManifestExtractor {
    const val CURRENT_SECRET_CODE_ACTION = "android.telephony.action.SECRET_CODE"
    const val LEGACY_SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
    const val SECRET_CODE_SCHEME = "android_secret_code"

    fun extract(
        apk: Path,
        sourceKind: SecretCodeSourceKind,
        limits: SecretCodeExtractionLimits = SecretCodeExtractionLimits(),
    ): SecretCodeExtractionResult {
        return try {
            ZipFile(apk.toFile()).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                    ?: return SecretCodeExtractionResult.Failure(
                        SecretCodeExtractionError.MANIFEST_MISSING,
                        "AndroidManifest.xml is missing",
                    )
                val declaredSize = entry.size
                if (declaredSize > limits.maxManifestBytes) {
                    return SecretCodeExtractionResult.Failure(
                        SecretCodeExtractionError.LIMIT_EXCEEDED,
                        "manifest exceeds maxManifestBytes",
                    )
                }
                val bytes = zip.getInputStream(entry).use { input ->
                    val out = ArrayList<Byte>()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size + read > limits.maxManifestBytes) {
                            return SecretCodeExtractionResult.Failure(
                                SecretCodeExtractionError.LIMIT_EXCEEDED,
                                "manifest exceeds maxManifestBytes while reading",
                            )
                        }
                        for (i in 0 until read) out += buffer[i]
                    }
                    ByteArray(out.size) { out[it] }
                }

                when (val parsed = BinaryAndroidManifestReader.read(bytes, limits)) {
                    is BinaryManifestReadResult.Failure -> SecretCodeExtractionResult.Failure(parsed.error, parsed.message)
                    is BinaryManifestReadResult.Success -> extractDeclarations(
                        parsed.events,
                        apk.fileName.toString(),
                        sourceKind,
                        limits,
                    )
                }
            }
        } catch (error: Exception) {
            SecretCodeExtractionResult.Failure(
                SecretCodeExtractionError.APK_NOT_READABLE,
                "${error.javaClass.simpleName}: ${error.message ?: "unable to read APK"}",
            )
        }
    }

    private fun extractDeclarations(
        events: List<BinaryXmlEvent>,
        sourceApkName: String,
        sourceKind: SecretCodeSourceKind,
        limits: SecretCodeExtractionLimits,
    ): SecretCodeExtractionResult {
        data class ReceiverContext(
            val className: String,
            val enabled: Boolean?,
            val exported: Boolean?,
            val permission: String?,
        )
        data class FilterContext(
            val actions: MutableSet<String> = linkedSetOf(),
            var scheme: String? = null,
            var host: String? = null,
            var relevantDataSeen: Boolean = false,
        )

        var receiver: ReceiverContext? = null
        var filter: FilterContext? = null
        val declarations = linkedSetOf<SecretCodeDeclaration>()

        fun addCandidate() {
            val currentReceiver = receiver ?: return
            val currentFilter = filter ?: return
            if (currentFilter.actions.none(::isSecretCodeAction)) return
            if (!currentFilter.relevantDataSeen) return

            val rawHost = currentFilter.host?.trim()?.takeIf { it.isNotEmpty() }
            val literal = rawHost != null && !rawHost.contains('*')
            val declaration = SecretCodeDeclaration(
                rawHost = rawHost,
                normalizedCode = rawHost?.takeIf { literal },
                receiverClassName = currentReceiver.className,
                actions = currentFilter.actions.toSet(),
                scheme = currentFilter.scheme,
                enabled = currentReceiver.enabled,
                exported = currentReceiver.exported,
                requiredPermission = currentReceiver.permission,
                sourceKind = sourceKind,
                sourceApkName = sourceApkName,
                declarationKind = if (literal) SecretCodeDeclarationKind.LITERAL_HOST else SecretCodeDeclarationKind.PATTERN_ONLY,
            )
            if (declaration !in declarations && declarations.size >= limits.maxCandidates) {
                throw CandidateLimitExceeded
            }
            declarations += declaration
        }

        try {
            for (event in events) {
                when (event) {
                    is BinaryXmlEvent.StartElement -> when (event.name) {
                        "receiver" -> {
                            receiver = ReceiverContext(
                                className = event.attribute("name") ?: "",
                                enabled = event.attribute("enabled")?.toBooleanStrictOrNull(),
                                exported = event.attribute("exported")?.toBooleanStrictOrNull(),
                                permission = event.attribute("permission"),
                            )
                        }
                        "intent-filter" -> if (receiver != null) filter = FilterContext()
                        "action" -> filter?.let { current ->
                            event.attribute("name")?.let(current.actions::add)
                        }
                        "data" -> filter?.let { current ->
                            val scheme = event.attribute("scheme")
                            if (scheme == SECRET_CODE_SCHEME) {
                                current.scheme = scheme
                                current.host = event.attribute("host")
                                current.relevantDataSeen = true
                            }
                        }
                    }
                    is BinaryXmlEvent.EndElement -> when (event.name) {
                        "intent-filter" -> {
                            addCandidate()
                            filter = null
                        }
                        "receiver" -> {
                            filter = null
                            receiver = null
                        }
                    }
                }
            }
        } catch (_: CandidateLimitExceeded) {
            return SecretCodeExtractionResult.Failure(
                SecretCodeExtractionError.LIMIT_EXCEEDED,
                "candidate count exceeds maxCandidates",
            )
        }

        return SecretCodeExtractionResult.Success(declarations.toList())
    }

    private fun isSecretCodeAction(action: String): Boolean =
        action == CURRENT_SECRET_CODE_ACTION || action == LEGACY_SECRET_CODE_ACTION

    private fun BinaryXmlEvent.StartElement.attribute(name: String): String? =
        attributes.firstOrNull { it.name == name }?.value

    private object CandidateLimitExceeded : RuntimeException()
}
