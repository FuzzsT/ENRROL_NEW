package io.dpcaio.appmanager.android

import android.content.Context
import android.util.Base64
import io.dpcaio.appmanager.PinGuardPolicy
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.PinGuardState
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinGuardVerifier(
    context: Context,
    private val store: AndroidPinGuardStore = AndroidPinGuardStore(context),
    private val policy: PinGuardPolicy = PinGuardPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun isConfigured(): Boolean = store.read() != null

    fun configure(pin: CharArray): PinGuardResult {
        if (pin.isEmpty()) {
            pin.fill('\u0000')
            return PinGuardResult.INVALID
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return try {
            val verifier = derive(pin, salt, DEFAULT_ITERATIONS)
            val saved = store.write(
                PinGuardRecord(
                    algorithm = ALGORITHM,
                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                    iterations = DEFAULT_ITERATIONS,
                    verifier = Base64.encodeToString(verifier, Base64.NO_WRAP),
                    failedAttempts = 0,
                    lockoutUntilMillis = 0L,
                )
            )
            verifier.fill(0)
            if (saved) PinGuardResult.SUCCESS else PinGuardResult.STORE_ERROR
        } catch (_: RuntimeException) {
            PinGuardResult.STORE_ERROR
        } finally {
            salt.fill(0)
            pin.fill('\u0000')
        }
    }

    fun verify(pin: CharArray): PinGuardResult {
        val record = store.read()
        if (record == null) {
            pin.fill('\u0000')
            return PinGuardResult.NOT_CONFIGURED
        }

        val now = clock()
        val precheck = policy.evaluate(
            PinGuardState(
                configured = true,
                failedAttempts = record.failedAttempts,
                lockoutUntilMillis = record.lockoutUntilMillis,
            ),
            attemptMatches = null,
            nowMillis = now,
        )
        if (record.lockoutUntilMillis > now) {
            pin.fill('\u0000')
            return precheck.result
        }

        val salt = try {
            Base64.decode(record.salt, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            pin.fill('\u0000')
            return PinGuardResult.STORE_ERROR
        }
        val expected = try {
            Base64.decode(record.verifier, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            salt.fill(0)
            pin.fill('\u0000')
            return PinGuardResult.STORE_ERROR
        }

        return try {
            val actual = derive(pin, salt, record.iterations)
            val matches = MessageDigest.isEqual(expected, actual)
            actual.fill(0)
            val decision = policy.evaluate(
                PinGuardState(
                    configured = true,
                    failedAttempts = record.failedAttempts,
                    lockoutUntilMillis = record.lockoutUntilMillis,
                ),
                attemptMatches = matches,
                nowMillis = now,
            )
            val saved = store.write(
                record.copy(
                    failedAttempts = decision.failedAttempts,
                    lockoutUntilMillis = decision.lockoutUntilMillis,
                )
            )
            if (saved) decision.result else PinGuardResult.STORE_ERROR
        } catch (_: RuntimeException) {
            PinGuardResult.STORE_ERROR
        } finally {
            expected.fill(0)
            salt.fill(0)
            pin.fill('\u0000')
        }
    }

    fun clear(): PinGuardResult = if (store.clear()) PinGuardResult.SUCCESS else PinGuardResult.STORE_ERROR

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0)
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val DEFAULT_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
    }
}
