package io.dpcaio.selfupdate

class SelfUpdatePlanner {
    fun evaluate(
        installedPackage: String,
        installedVersionCode: Long,
        selectedChannel: UpdateChannel,
        manifest: SelfUpdateManifest,
    ): SelfUpdateDecision {
        if (manifest.schemaVersion != 1) {
            return reject("UNSUPPORTED_SCHEMA")
        }
        if (manifest.channel != selectedChannel) {
            return reject("CHANNEL_MISMATCH")
        }
        if (manifest.packageName != installedPackage) {
            return reject("PACKAGE_MISMATCH")
        }
        if (manifest.versionCode <= installedVersionCode) {
            return SelfUpdateDecision(
                accepted = false,
                state = SelfUpdateState.UP_TO_DATE,
                reason = "NO_NEWER_VERSION",
            )
        }
        if (!manifest.apkUrl.startsWith("https://")) {
            return reject("APK_URL_NOT_HTTPS")
        }
        if (!SHA256.matches(manifest.apkSha256)) {
            return reject("INVALID_APK_SHA256")
        }
        return SelfUpdateDecision(
            accepted = true,
            state = SelfUpdateState.UPDATE_AVAILABLE,
            reason = "UPDATE_AVAILABLE",
        )
    }

    private fun reject(reason: String): SelfUpdateDecision = SelfUpdateDecision(
        accepted = false,
        state = SelfUpdateState.ERROR,
        reason = reason,
    )

    private companion object {
        val SHA256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
