package io.dpcaio.delegation.dhizuku

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.rosan.dhizuku.aidl.IDhizukuClient
import io.dpcaio.delegation.DelegationAuthorizer
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class SafeDhizukuProvider : ContentProvider() {
    companion object {
        const val METHOD_CLIENT = "client"
        const val EXTRA_CLIENT = "client"
        const val PARAM_DHIZUKU_BINDER = "dhizuku_binder"
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_CLIENT || extras == null) return null
        val clientBinder = extras.getBinder(EXTRA_CLIENT) ?: return null
        IDhizukuClient.Stub.asInterface(clientBinder) ?: return null

        val ctx = context ?: return null
        val resolver = AndroidCallerIdentityResolver(ctx)
        val candidates = resolver.resolve(Binder.getCallingUid())
        val registry = DhizukuCompatRuntime.registry
        val pinned = candidates.singleOrNull { candidate ->
            val registered = registry.findByPackage(candidate.packageName)
            registered?.identity == candidate
        } ?: candidates.singleOrNull()

        val admin = ComponentName(ctx.packageName, "${ctx.packageName}.AioDeviceAdminReceiver")
        val policy = AndroidDevicePolicyGateway(ctx, admin)
        val service = SafeDhizukuService(
            initialCaller = pinned,
            identityResolver = resolver,
            authorizer = DelegationAuthorizer(registry),
            delegationPolicy = policy
        )
        return Bundle().apply { putBinder(PARAM_DHIZUKU_BINDER, service.asBinder()) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
