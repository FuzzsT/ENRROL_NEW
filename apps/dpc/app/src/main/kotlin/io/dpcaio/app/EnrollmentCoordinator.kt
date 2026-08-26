package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PersistableBundle
import io.dpcaio.core.model.EnrollmentErrorCode
import io.dpcaio.core.model.EnrollmentSession
import io.dpcaio.core.model.EnrollmentStage
import org.json.JSONObject

sealed interface EnrollmentOutcome {
    data class Complete(val session: EnrollmentSession, val serverRegistrationPending: Boolean = false) : EnrollmentOutcome
    data class Retryable(val session: EnrollmentSession, val message: String) : EnrollmentOutcome
    data class Failed(val session: EnrollmentSession, val message: String) : EnrollmentOutcome
}

class EnrollmentCoordinator(private val context: Context) {
    private val sessionStore = EnrollmentSessionStore(context)
    private val secretStore = EnrollmentSecretStore(context)

    fun resumeOrCreate(intent: Intent): EnrollmentOutcome {
        val extras = adminExtras(intent)
        val parsed = EnrollmentConfigParser.parse(extras)
        var session = sessionStore.read()?.takeIf { existing ->
            val incoming = extras?.getString(KEY_SESSION_ID)
            incoming == null || incoming == existing.sessionId
        } ?: createSession(parsed.config)

        if (session.stage == EnrollmentStage.COMPLETE) return EnrollmentOutcome.Complete(session)
        val secrets = secretStore.get(session.secretRef) ?: EnrollmentSecrets(parsed.config.enrollmentToken, parsed.config.password)
        val token = secrets.enrollmentToken
        val endpoint = session.serverUri

        session = advance(session, EnrollmentStage.VALIDATING)
        if (token.isNullOrBlank() && endpoint.isNullOrBlank()) {
            session = advance(session, EnrollmentStage.LOCAL_PROVISIONED)
            session = advance(session, EnrollmentStage.COMPLETE)
            return EnrollmentOutcome.Complete(session)
        }
        if (token.isNullOrBlank() || endpoint.isNullOrBlank()) {
            return if (session.allowOffline) {
                session = advance(session, EnrollmentStage.LOCAL_PROVISIONED)
                session = advance(session, EnrollmentStage.SERVER_REGISTRATION_PENDING)
                EnrollmentOutcome.Complete(session, serverRegistrationPending = true)
            } else {
                fail(session, EnrollmentErrorCode.CONFIG_INVALID, "Online enrollment requires token and HTTPS endpoint")
            }
        }
        if (!hasInternet()) return retry(session, EnrollmentErrorCode.NETWORK_UNAVAILABLE, "Network unavailable")

        session = advance(session, EnrollmentStage.NETWORK_CHECK)
        val client = runCatching { EnrollmentServerClient(endpoint) }.getOrElse {
            return fail(session, EnrollmentErrorCode.CONFIG_INVALID, it.message ?: "Invalid endpoint")
        }

        return try {
            session = advance(session, EnrollmentStage.RESERVING)
            val reserve = client.reserve(token, session)
            if (reserve.statusCode !in 200..299) return classifyHttpFailure(session, reserve.statusCode, reserve.body)
            val reservationId = reserve.body.optString("reservationId").takeIf { it.isNotBlank() }
                ?: return fail(session, EnrollmentErrorCode.TOKEN_INVALID, "Reservation id missing")
            session = session.copy(reservationId = reservationId, updatedAt = System.currentTimeMillis())
            sessionStore.write(session)

            session = advance(session, EnrollmentStage.REGISTERING)
            val validate = client.validate(session, reservationId, deviceFacts(session))
            if (validate.statusCode !in 200..299) return classifyHttpFailure(session, validate.statusCode, validate.body)

            val bootstrap = client.bootstrap(session, reservationId)
            if (bootstrap.statusCode !in 200..299) return classifyHttpFailure(session, bootstrap.statusCode, bootstrap.body)
            session = advance(session, EnrollmentStage.BOOTSTRAP_VERIFY)

            val publicKey = BuildConfig.ENROLLMENT_SIGNING_PUBLIC_KEY
            if (publicKey.isBlank()) return fail(session, EnrollmentErrorCode.CONFIG_INVALID, "Enrollment signing public key is not configured")
            val trust = EnrollmentTrustVerifier(publicKey).verify(bootstrap.body, session.sessionId, reservationId)
            if (!trust.verified) return fail(session, EnrollmentErrorCode.SIGNATURE_INVALID, trust.errorCode ?: "Bootstrap signature invalid")

            val applier = EnrollmentBootstrapApplier(context)
            val policy = applier.parsePolicy(bootstrap.body.getJSONObject("payload"))
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val policyValidation = policy.validate(session.requestedMode, Build.VERSION.SDK_INT, version)
            if (!policyValidation.ok) return fail(session, EnrollmentErrorCode.POLICY_INCOMPATIBLE, policyValidation.errorCode ?: "Policy incompatible")

            session = advance(session, EnrollmentStage.APPLYING_PROFILE)
            val apply = applier.applyAndVerify(policy)
            if (!apply.verified) return retry(session, EnrollmentErrorCode.POLICY_APPLY_FAILED, apply.errorCode ?: "Policy apply failed")
            session = advance(session, EnrollmentStage.POLICY_READBACK)

            session = advance(session, EnrollmentStage.COMMITTING)
            val commit = client.commit(session, reservationId)
            if (commit.statusCode !in 200..299) return classifyHttpFailure(session, commit.statusCode, commit.body)

            secretStore.remove(session.secretRef)
            session = advance(session, EnrollmentStage.POST_PROVISION)
            session = advance(session, EnrollmentStage.COMPLETE)
            EnrollmentOutcome.Complete(session)
        } catch (e: javax.net.ssl.SSLException) {
            fail(session, EnrollmentErrorCode.TLS_ERROR, e.javaClass.simpleName)
        } catch (e: java.io.IOException) {
            retry(session, EnrollmentErrorCode.SERVER_UNREACHABLE, e.javaClass.simpleName)
        } catch (e: RuntimeException) {
            fail(session, EnrollmentErrorCode.CONFIG_INVALID, e.javaClass.simpleName)
        }
    }

    private fun createSession(config: io.dpcaio.core.model.NormalizedEnrollmentConfig): EnrollmentSession {
        val now = System.currentTimeMillis()
        val initial = EnrollmentSession.new(config, now)
        val secretRef = if (!config.enrollmentToken.isNullOrBlank() || !config.password.isNullOrBlank()) "session:${initial.sessionId}" else null
        if (secretRef != null) secretStore.put(secretRef, EnrollmentSecrets(config.enrollmentToken, config.password))
        return initial.copy(
            tokenFingerprint = EnrollmentSessionStore.tokenFingerprint(config.enrollmentToken),
            secretRef = secretRef,
        ).also(sessionStore::write)
    }

    private fun advance(session: EnrollmentSession, stage: EnrollmentStage): EnrollmentSession =
        session.advance(stage, System.currentTimeMillis()).also(sessionStore::write)

    private fun retry(session: EnrollmentSession, code: EnrollmentErrorCode, message: String): EnrollmentOutcome.Retryable {
        val failed = session.fail(code, System.currentTimeMillis()).also(sessionStore::write)
        return EnrollmentOutcome.Retryable(failed, message)
    }

    private fun fail(session: EnrollmentSession, code: EnrollmentErrorCode, message: String): EnrollmentOutcome.Failed {
        val terminalCode = if (code.retryable) EnrollmentErrorCode.CONFIG_INVALID else code
        val failed = session.fail(terminalCode, System.currentTimeMillis()).also(sessionStore::write)
        return EnrollmentOutcome.Failed(failed, message)
    }

    private fun classifyHttpFailure(session: EnrollmentSession, status: Int, body: JSONObject): EnrollmentOutcome {
        val message = body.optString("error", "HTTP_$status")
        return when {
            status >= 500 -> retry(session, EnrollmentErrorCode.RETRYABLE_SERVER_ERROR, message)
            status == 401 && message.contains("EXPIRED") -> fail(session, EnrollmentErrorCode.TOKEN_EXPIRED, message)
            status == 401 -> fail(session, EnrollmentErrorCode.TOKEN_INVALID, message)
            status == 409 -> fail(session, EnrollmentErrorCode.TOKEN_ALREADY_USED, message)
            else -> fail(session, EnrollmentErrorCode.CONFIG_INVALID, message)
        }
    }

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun deviceFacts(session: EnrollmentSession): JSONObject = JSONObject().apply {
        put("androidApi", Build.VERSION.SDK_INT)
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("provisioningMode", session.requestedMode)
        put("dpcVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown")
        session.organizationId?.let { put("organizationId", it) }
    }

    private fun adminExtras(intent: Intent): PersistableBundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, PersistableBundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }

    companion object {
        const val KEY_SESSION_ID = "io.dpcaio.extra.ENROLLMENT_SESSION_ID"
        const val KEY_TRIGGER = "io.dpcaio.extra.ENROLLMENT_TRIGGER"
        fun scheduleResume(context: Context, trigger: String) {
            EnrollmentResumeScheduler.schedule(context, trigger)
        }
    }
}
