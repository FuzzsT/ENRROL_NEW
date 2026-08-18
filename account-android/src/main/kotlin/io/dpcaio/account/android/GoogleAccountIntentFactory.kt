package io.dpcaio.account.android

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.provider.Settings
import io.dpcaio.account.AccountRecord

object GoogleAccountIntentFactory {
    fun chooseAccount(selected: AccountRecord? = null): Intent =
        AccountManager.newChooseAccountIntent(
            selected?.let { Account(it.name, it.type) },
            null,
            arrayOf(AndroidGoogleAccountRepository.GOOGLE_ACCOUNT_TYPE),
            "Wybierz konto Google",
            null,
            null,
            null
        )

    fun addGoogleAccount(): Intent =
        Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(
            Settings.EXTRA_ACCOUNT_TYPES,
            arrayOf(AndroidGoogleAccountRepository.GOOGLE_ACCOUNT_TYPE)
        )

    fun syncSettings(): Intent = Intent(Settings.ACTION_SYNC_SETTINGS)
}
