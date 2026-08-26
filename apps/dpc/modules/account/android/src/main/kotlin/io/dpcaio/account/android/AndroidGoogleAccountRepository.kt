package io.dpcaio.account.android

import android.accounts.AccountManager
import android.content.Context
import android.os.Process
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.account.AccountRecord

class AndroidGoogleAccountRepository(
    context: Context
) {
    private val accountManager = AccountManager.get(context)

    fun listGoogleAccounts(userId: Int = AndroidUserId.fromUid(Process.myUid())): List<AccountRecord> =
        accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE)
            .map { AccountRecord(name = it.name, type = it.type, userId = userId) }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
