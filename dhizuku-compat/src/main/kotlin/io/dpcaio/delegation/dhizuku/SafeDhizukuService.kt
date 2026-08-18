package io.dpcaio.delegation.dhizuku

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.rosan.dhizuku.aidl.IDhizuku
import com.rosan.dhizuku.aidl.IDhizukuRemoteProcess
import com.rosan.dhizuku.aidl.IDhizukuUserServiceConnection
import io.dpcaio.delegation.AuthorizationReason
import io.dpcaio.delegation.ClientIdentity
import io.dpcaio.delegation.DelegationAuthorizer
import io.dpcaio.policy.DelegationPolicyGateway

class SafeDhizukuService(
    private val initialCaller: ClientIdentity?,
    private val identityResolver: AndroidCallerIdentityResolver,
    private val authorizer: DelegationAuthorizer,
    private val delegationPolicy: DelegationPolicyGateway
) : IDhizuku.Stub() {

    override fun getVersionCode(): Int = 7
    override fun getVersionName(): String = "7-aio-safe"

    override fun isPermissionGranted(): Boolean = authorize("dhizuku.api") == AuthorizationReason.ALLOWED

    override fun remoteProcess(cmd: Array<out String>?, env: Array<out String>?, dir: String?): IDhizukuRemoteProcess? {
        throw SecurityException("REMOTE_PROCESS_DISABLED")
    }

    override fun bindUserService(connection: IDhizukuUserServiceConnection?, bundle: Bundle?) {
        throw SecurityException("USER_SERVICE_DISABLED")
    }

    override fun unbindUserService(bundle: Bundle?) {
        throw SecurityException("USER_SERVICE_DISABLED")
    }

    override fun unbindUserServiceByConnection(connection: IDhizukuUserServiceConnection?, bundle: Bundle?) {
        throw SecurityException("USER_SERVICE_DISABLED")
    }

    override fun getDelegatedScopes(packageName: String): Array<String> {
        enforce("delegated.scopes.read")
        val result = delegationPolicy.getDelegatedScopes(packageName)
        if (!result.isSuccess) throw SecurityException(result.message ?: "DELEGATED_SCOPE_READ_DENIED")
        return result.value.orEmpty().sorted().toTypedArray()
    }

    override fun setDelegatedScopes(packageName: String, scopes: Array<out String>?) {
        enforce("delegated.scopes.manage")
        val result = delegationPolicy.setDelegatedScopes(packageName, scopes.orEmpty().toSet())
        if (!result.isSuccess) throw SecurityException(result.message ?: "DELEGATED_SCOPE_WRITE_DENIED")
    }

    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == Binder.FIRST_CALL_TRANSACTION + 10) {
            throw SecurityException("RAW_BINDER_DISABLED")
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun enforce(scope: String) {
        val reason = authorize(scope)
        if (reason != AuthorizationReason.ALLOWED) {
            throw SecurityException("Dhizuku compatibility denied: $reason")
        }
    }

    private fun authorize(scope: String): AuthorizationReason {
        val pinned = initialCaller ?: return AuthorizationReason.CLIENT_NOT_FOUND
        val callingUid = Binder.getCallingUid()
        val current = identityResolver.resolve(callingUid).firstOrNull { it.packageName == pinned.packageName }
            ?: return AuthorizationReason.IDENTITY_MISMATCH
        if (current != pinned) return AuthorizationReason.IDENTITY_MISMATCH
        return authorizer.authorize(current, scope).reason
    }
}
