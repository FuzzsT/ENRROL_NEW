package io.dpcaio.app

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.account.AccountPriorityPlanner
import io.dpcaio.account.AccountPriorityStatus
import io.dpcaio.account.android.AioAccountSelectionStore
import io.dpcaio.account.android.AndroidAccountReorderGateway
import io.dpcaio.account.android.AndroidGoogleAccountRepository
import io.dpcaio.account.android.GoogleAccountIntentFactory

class GoogleAccountManagerActivity : Activity() {
    private lateinit var repository: AndroidGoogleAccountRepository
    private lateinit var selectionStore: AioAccountSelectionStore
    private lateinit var reorderGateway: AndroidAccountReorderGateway
    private val planner = AccountPriorityPlanner()

    private lateinit var status: TextView
    private lateinit var accounts: TextView
    private lateinit var addPendingButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AndroidGoogleAccountRepository(this)
        selectionStore = AioAccountSelectionStore(this)
        reorderGateway = AndroidAccountReorderGateway(this, AioDeviceAdminReceiver.componentName(this))
        setContentView(buildUi())
        ensureAccountPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        resumePendingReorderIfPossible()
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        content.addView(TextView(this).apply {
            text = "Google Account Priority"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply { setPadding(0, 20, 0, 20) }
        accounts = TextView(this).apply { setTextIsSelectable(true) }
        content.addView(status)
        content.addView(accounts)
        content.addView(Button(this).apply {
            text = "Wybierz konto dla DPC"
            setOnClickListener { startActivityForResult(GoogleAccountIntentFactory.chooseAccount(), REQ_CHOOSE) }
        })
        content.addView(Button(this).apply {
            text = "Ustaw wybrane jako pierwsze (workflow)"
            setOnClickListener { prepareObservedOrderReorder() }
        })
        content.addView(Button(this).apply {
            text = "Dodaj konto Google"
            setOnClickListener { startActivity(GoogleAccountIntentFactory.addGoogleAccount()) }
        })
        content.addView(Button(this).apply {
            text = "Otwórz ustawienia synchronizacji/kont"
            setOnClickListener { startActivity(GoogleAccountIntentFactory.syncSettings()) }
        })
        addPendingButton = Button(this).apply {
            visibility = View.GONE
            setOnClickListener { startActivity(GoogleAccountIntentFactory.addGoogleAccount()) }
        }
        content.addView(addPendingButton)
        return ScrollView(this).apply { addView(content) }
    }

    private fun ensureAccountPermission() {
        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.GET_ACCOUNTS), REQ_GET_ACCOUNTS)
        }
    }

    private fun refresh(extra: String? = null) {
        val visible = repository.listGoogleAccounts()
        val selected = selectionStore.selectedAccountName()
        val observedFirst = visible.firstOrNull()?.name ?: "brak/nie jest widoczne dla DPC"
        status.text = buildString {
            append("Wybrane dla DPC: ").append(selected ?: "brak").append('\n')
            append("Obserwowane pierwsze konto: ").append(observedFirst).append('\n')
            append("Uwaga: Android nie udostępnia publicznego setPrimaryGoogleAccount(); wynik jest weryfikowany przez read-back kolejności widocznej dla AccountManager.")
            if (extra != null) append("\n\n").append(extra)
        }
        accounts.text = buildString {
            append("Konta Google widoczne dla DPC:\n")
            if (visible.isEmpty()) append("(brak; użyj wyboru konta lub nadaj dostęp GET_ACCOUNTS)\n")
            visible.forEachIndexed { index, account ->
                append(index + 1).append(". ").append(account.name)
                if (account.name == selected) append("  [DPC]")
                append('\n')
            }
        }
    }

    private fun prepareObservedOrderReorder() {
        val selected = selectionStore.selectedAccountName()
        if (selected == null) {
            refresh("Najpierw wybierz konto dla DPC.")
            return
        }
        val plan = planner.plan(repository.listGoogleAccounts(), selected)
        when (plan.status) {
            AccountPriorityStatus.TARGET_NOT_FOUND -> refresh("Wybrane konto nie jest obecnie widoczne. Użyj systemowego wyboru konta.")
            AccountPriorityStatus.ALREADY_FIRST -> refresh("VERIFIED: wybrane konto jest już pierwsze w obserwowanej kolejności.")
            AccountPriorityStatus.REORDER_REQUIRED -> {
                val toRemove = plan.accountsToTemporarilyRemove.joinToString("\n") { "• ${it.name}" }
                AlertDialog.Builder(this)
                    .setTitle("Zmiana obserwowanej kolejności kont")
                    .setMessage(
                        "Aby ${selected} stało się pierwsze, konta przed nim muszą zostać czasowo usunięte i ponownie dodane. " +
                            "Może to wylogować aplikacje i wymaga ponownego uwierzytelnienia. DPC nie zapisuje haseł ani tokenów.\n\nDo czasowego usunięcia:\n$toRemove"
                    )
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Kontynuuj") { _, _ -> executeRemovalPhase(plan.accountsToTemporarilyRemove.map { it.name }, selected) }
                    .show()
            }
        }
    }

    private fun executeRemovalPhase(accountNames: List<String>, targetName: String) {
        if (accountNames.isEmpty()) {
            selectionStore.savePendingReAdd(targetName, emptyList())
            verifyObservedOrder(targetName)
            return
        }

        val restoreManagementDisabled = runCatching { reorderGateway.isAccountManagementDisabled() }.getOrDefault(false)
        if (restoreManagementDisabled) {
            runCatching { reorderGateway.setGoogleAccountManagementDisabled(false) }
        }

        if (!reorderGateway.canRequestAccountRemoval()) {
            selectionStore.savePendingReAdd(targetName, accountNames, restoreManagementDisabled)
            refresh("Automatyczne rozpoczęcie usuwania kont nie jest dostępne dla tej tożsamości. Usuń wskazane konta w ustawieniach systemowych, wróć tutaj, a AIO wznowi weryfikację/re-add.")
            startActivity(GoogleAccountIntentFactory.syncSettings())
            return
        }
        removeSequentially(accountNames, targetName, 0, restoreManagementDisabled)
    }

    private fun removeSequentially(accountNames: List<String>, targetName: String, index: Int, restoreManagementDisabled: Boolean) {
        if (index >= accountNames.size) {
            selectionStore.savePendingReAdd(targetName, accountNames, restoreManagementDisabled)
            refresh("Konta przed targetem usunięte. Dodaj je ponownie; AIO będzie sprawdzał kolejność po każdym powrocie.")
            resumePendingReorderIfPossible()
            return
        }
        val name = accountNames[index]
        reorderGateway.requestRemoveGoogleAccount(this, name) { removed, error ->
            runOnUiThread {
                if (removed) removeSequentially(accountNames, targetName, index + 1, restoreManagementDisabled)
                else {
                    selectionStore.savePendingReAdd(targetName, accountNames.drop(index), restoreManagementDisabled)
                    refresh("Nie udało się usunąć $name: ${error?.message ?: "authenticator odmówił"}. Kontynuuj ręcznie w ustawieniach kont.")
                    startActivity(GoogleAccountIntentFactory.syncSettings())
                }
            }
        }
    }

    private fun resumePendingReorderIfPossible() {
        val target = selectionStore.pendingTarget() ?: run {
            addPendingButton.visibility = View.GONE
            return
        }
        val pending = selectionStore.pendingReAdd()
        if (pending.isEmpty()) {
            val restorePolicy = selectionStore.shouldRestoreManagementDisabled()
            selectionStore.clearPendingReorder()
            if (restorePolicy) runCatching { reorderGateway.setGoogleAccountManagementDisabled(true) }
            verifyObservedOrder(target)
            return
        }
        val visibleNames = repository.listGoogleAccounts().map { it.name }.toSet()
        val alreadyReturned = pending.firstOrNull { it in visibleNames }
        if (alreadyReturned != null) {
            selectionStore.markReAdded(alreadyReturned)
            resumePendingReorderIfPossible()
            return
        }
        val next = pending.first()
        addPendingButton.text = "Dodaj ponownie: $next"
        addPendingButton.visibility = View.VISIBLE
        refresh("Oczekuje na ponowne dodanie: $next")
    }

    private fun verifyObservedOrder(targetName: String) {
        val verification = planner.plan(repository.listGoogleAccounts(), targetName)
        if (verification.status == AccountPriorityStatus.ALREADY_FIRST) {
            refresh("GREEN_VERIFIED: $targetName jest pierwsze w obserwowanej kolejności AccountManager.")
        } else {
            refresh("NOT_VERIFIED: kolejność AccountManager nadal nie pokazuje $targetName jako pierwszego. Niektóre aplikacje Google mogą mieć własny wybór konta niezależny od tej kolejności.")
        }
    }

    @Deprecated("Activity result API kept dependency-free for the bootstrap UI")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CHOOSE && resultCode == RESULT_OK) {
            val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) selectionStore.setSelectedAccountName(accountName)
            refresh()
        }
    }

    private companion object {
        const val REQ_CHOOSE = 3101
        const val REQ_GET_ACCOUNTS = 3102
    }
}
