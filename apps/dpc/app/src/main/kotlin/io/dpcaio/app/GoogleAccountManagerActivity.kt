package io.dpcaio.app

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
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
    private lateinit var manualRemovalButton: Button
    private var recoveryPollScheduled = false
    private val recoveryPoll = Runnable {
        recoveryPollScheduled = false
        if (!isFinishing && !isDestroyed) resumePendingReorderIfPossible()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Google Account Priority"
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

    override fun onPause() {
        if (::status.isInitialized) status.removeCallbacks(recoveryPoll)
        recoveryPollScheduled = false
        super.onPause()
    }

    private fun buildUi(): View {
        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Google Account Priority",
                subtitle = "Choose the DPC account and verify the observable AccountManager order",
                eyebrow = "ACCOUNT MANAGEMENT",
            )
        )

        body.addView(ModernUi.section(this, "Observed state"))
        status = TextView(this).apply { setTextIsSelectable(true) }
        body.addView(ModernUi.outputCard(this, status))

        body.addView(ModernUi.section(this, "Visible Google accounts"))
        accounts = TextView(this).apply { setTextIsSelectable(true) }
        body.addView(ModernUi.outputCard(this, accounts))

        body.addView(ModernUi.section(this, "Actions"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.action(this@GoogleAccountManagerActivity, "Wybierz konto dla DPC", primary = true) {
                    startActivityForResult(GoogleAccountIntentFactory.chooseAccount(), REQ_CHOOSE)
                })
                addView(ModernUi.action(this@GoogleAccountManagerActivity, "Ustaw wybrane jako pierwsze (workflow)") {
                    prepareObservedOrderReorder()
                })
                addView(ModernUi.action(this@GoogleAccountManagerActivity, "Dodaj konto Google") {
                    startActivity(GoogleAccountIntentFactory.addGoogleAccount())
                })
                addView(ModernUi.action(this@GoogleAccountManagerActivity, "Otwórz ustawienia synchronizacji/kont") {
                    startActivity(GoogleAccountIntentFactory.syncSettings())
                })
                manualRemovalButton = ModernUi.action(this@GoogleAccountManagerActivity, "Usuń oczekujące konto ręcznie") {
                    startActivity(GoogleAccountIntentFactory.syncSettings())
                }.apply { visibility = View.GONE }
                addView(manualRemovalButton)
                addPendingButton = ModernUi.action(this@GoogleAccountManagerActivity, "Dodaj ponownie oczekujące konto", primary = true) {
                    startActivity(GoogleAccountIntentFactory.addGoogleAccount())
                }.apply { visibility = View.GONE }
                addView(addPendingButton)
            }
        )

        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@GoogleAccountManagerActivity, "Read-back boundary"))
                addView(
                    ModernUi.body(
                        this@GoogleAccountManagerActivity,
                        "Android nie udostępnia publicznego setPrimaryGoogleAccount(). AIO weryfikuje tylko kolejność widoczną dla AccountManager i nie zapisuje haseł ani tokenów.",
                    )
                )
            }
        )
        return ModernUi.scrollPage(this, body)
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
            verifyObservedOrder(targetName)
            return
        }

        val restoreManagementDisabled = runCatching { reorderGateway.isAccountManagementDisabled() }.getOrDefault(false)
        selectionStore.saveReorderRecovery(targetName, accountNames, emptyList(), restoreManagementDisabled)
        if (restoreManagementDisabled) {
            runCatching { reorderGateway.setGoogleAccountManagementDisabled(false) }
        }
        resumePendingReorderIfPossible()
    }

    private fun requestRemoval(name: String) {
        if (!markRemovalRequestActive(name)) return
        selectionStore.markRemovalInFlight(name)
        runCatching {
            reorderGateway.requestRemoveGoogleAccount(this, name) { removed, error ->
                markRemovalRequestInactive(name)
                if (removed) selectionStore.markRemoved(name)
                if (!removed) selectionStore.markRemovalFailed(name)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (removed) {
                        resumePendingReorderIfPossible()
                    } else {
                        showManualRemoval(name, "Nie udało się usunąć $name: ${error?.message ?: "authenticator odmówił"}.")
                    }
                }
            }
        }.onFailure { error ->
            markRemovalRequestInactive(name)
            selectionStore.markRemovalFailed(name)
            if (!isFinishing && !isDestroyed) {
                showManualRemoval(name, "Nie udało się rozpocząć usuwania $name: ${error.message ?: error.javaClass.simpleName}.")
            }
        }
    }

    private fun resumePendingReorderIfPossible() {
        val target = selectionStore.pendingTarget() ?: run {
            manualRemovalButton.visibility = View.GONE
            addPendingButton.visibility = View.GONE
            return
        }
        val visibleNames = repository.listGoogleAccounts().map { it.name }.toSet()
        val pendingRemoval = selectionStore.pendingRemoval()
        pendingRemoval.filterNot { it in visibleNames }.forEach { selectionStore.markRemoved(it) }

        val remainingRemoval = selectionStore.pendingRemoval()
        if (remainingRemoval.isNotEmpty()) {
            addPendingButton.visibility = View.GONE
            val manual = selectionStore.manualRemovalRequired()
            if (manual != null && manual in remainingRemoval) {
                showManualRemoval(manual, "Automatyczne usunięcie nie powiodło się. Usuń konto ręcznie i wróć do AIO.")
                return
            }

            val inFlight = selectionStore.removalInFlight()
            if (inFlight != null) {
                manualRemovalButton.visibility = View.GONE
                if (!isRemovalRequestActive(inFlight)) {
                    selectionStore.markRemovalFailed(inFlight)
                    showManualRemoval(inFlight, "Poprzednia sesja usuwania została przerwana. Dokończ usunięcie ręcznie.")
                    return
                }
                refresh("Usuwanie w toku: $inFlight")
                scheduleRecoveryPoll()
                return
            }

            manualRemovalButton.visibility = View.GONE
            val next = remainingRemoval.first()
            if (!reorderGateway.canRequestAccountRemoval()) {
                selectionStore.markRemovalFailed(next)
                showManualRemoval(next, "Automatyczne rozpoczęcie usuwania nie jest dostępne dla tej tożsamości.")
                return
            }
            requestRemoval(selectionStore.pendingRemoval().first())
            refresh("Rozpoczynam usuwanie: $next")
            return
        }

        manualRemovalButton.visibility = View.GONE
        val pending = selectionStore.pendingReAdd()
        if (pending.isEmpty()) {
            val restorePolicy = selectionStore.shouldRestoreManagementDisabled()
            selectionStore.clearPendingReorder()
            if (restorePolicy) runCatching { reorderGateway.setGoogleAccountManagementDisabled(true) }
            verifyObservedOrder(target)
            return
        }

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

    private fun showManualRemoval(accountName: String, detail: String) {
        manualRemovalButton.text = "Usuń ręcznie: $accountName"
        manualRemovalButton.visibility = View.VISIBLE
        addPendingButton.visibility = View.GONE
        refresh("$detail\nOczekuje na usunięcie: $accountName")
    }

    private fun scheduleRecoveryPoll() {
        if (recoveryPollScheduled || isFinishing || isDestroyed) return
        recoveryPollScheduled = true
        status.postDelayed(recoveryPoll, RECOVERY_POLL_MS)
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
        const val RECOVERY_POLL_MS = 750L
        private val removalRequestLock = Any()
        private val activeRemovalRequests = mutableSetOf<String>()

        fun markRemovalRequestActive(accountName: String): Boolean = synchronized(removalRequestLock) {
            activeRemovalRequests.add(accountName)
        }

        fun markRemovalRequestInactive(accountName: String) = synchronized(removalRequestLock) {
            activeRemovalRequests.remove(accountName)
        }

        fun isRemovalRequestActive(accountName: String): Boolean = synchronized(removalRequestLock) {
            accountName in activeRemovalRequests
        }
    }
}
