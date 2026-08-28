package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.policy.android.parity.AndroidParityRuntimeFactsProvider
import io.dpcaio.policy.parity.OwnerRequirement
import io.dpcaio.policy.parity.ParityAvailability
import io.dpcaio.policy.parity.ParityRuntimeFacts
import io.dpcaio.policy.parity.TestDpcCapabilityResolver
import io.dpcaio.policy.parity.TestDpcImplementationState
import io.dpcaio.policy.parity.TestDpcParityCatalog
import io.dpcaio.policy.parity.TestDpcParityEntry

class TestDpcParityCenterActivity : Activity() {
    private lateinit var facts: ParityRuntimeFacts
    private lateinit var favorites: TestDpcParityFavoriteStore
    private lateinit var search: EditText
    private lateinit var scopeSpinner: Spinner
    private lateinit var apiSpinner: Spinner
    private lateinit var stateSpinner: Spinner
    private lateinit var results: LinearLayout

    private enum class ScopeFilter(val label: String) {
        ALL("All"),
        AVAILABLE("Available"),
        UNSUPPORTED("Unsupported"),
        DEPRECATED("Deprecated"),
        DEVICE_OWNER("Device Owner"),
        PROFILE_OWNER("Profile Owner"),
        COPE("COPE"),
        FAVORITES("Favorites"),
    }

    private enum class ApiFilter(val label: String) {
        ALL("Minimum API: all"),
        SUPPORTED("Minimum API <= device"),
        ABOVE_DEVICE("Minimum API > device"),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TestDPC Parity Center"
        favorites = TestDpcParityFavoriteStore(this)
        facts = AndroidParityRuntimeFactsProvider(
            this,
            ComponentName(this, AioDeviceAdminReceiver::class.java),
        ).read()
        renderShell()
    }

    override fun onResume() {
        super.onResume()
        if (::results.isInitialized) {
            facts = AndroidParityRuntimeFactsProvider(
                this,
                ComponentName(this, AioDeviceAdminReceiver::class.java),
            ).read()
            renderResults()
        }
    }

    private fun renderShell() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 24, 24, 24)
        }
        body.addView(TextView(this).apply {
            text = "Google TestDPC parity\nCatalog-driven view of all 169 pinned direct TestDPC entries. Runtime availability is evaluated before an action can be used."
        })

        search = EditText(this).apply {
            hint = "Search title, TestDPC key, category, description"
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderResults()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        body.addView(search)

        scopeSpinner = spinner(ScopeFilter.entries.map { it.label })
        apiSpinner = spinner(ApiFilter.entries.map { it.label })
        stateSpinner = spinner(
            listOf("Implementation state: all") + TestDpcImplementationState.entries.map { "Implementation state: ${it.name}" }
        )
        body.addView(scopeSpinner)
        body.addView(apiSpinner)
        body.addView(stateSpinner)

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(results)
        setContentView(DpcUiShell.scroll(this, body))
        renderResults()
    }

    private fun spinner(values: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@TestDpcParityCenterActivity,
            android.R.layout.simple_spinner_dropdown_item,
            values,
        )
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (::results.isInitialized) renderResults()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderResults() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        val all = TestDpcParityCatalog.entries
        val resolved = all.associateWith { TestDpcCapabilityResolver.resolve(it, facts) }
        val catalogued = all.size
        val implemented = all.count(::isImplemented)
        val availableOnDevice = all.count { entry -> isOperational(entry, resolved.getValue(entry)) }
        results.addView(TextView(this).apply {
            text = "$catalogued catalogued · $implemented implemented · $availableOnDevice available on this device"
            setPaddingDp(0, 12, 0, 12)
        })

        val query = search.text?.toString()?.trim()?.lowercase().orEmpty()
        val scope = ScopeFilter.entries.getOrElse(scopeSpinner.selectedItemPosition) { ScopeFilter.ALL }
        val apiFilter = ApiFilter.entries.getOrElse(apiSpinner.selectedItemPosition) { ApiFilter.ALL }
        val selectedState = if (stateSpinner.selectedItemPosition <= 0) null
            else TestDpcImplementationState.entries.getOrNull(stateSpinner.selectedItemPosition - 1)

        val filtered = all.filter { entry ->
            val availability = resolved.getValue(entry)
            matchesQuery(entry, query) &&
                matchesScope(entry, availability, scope) &&
                matchesApi(entry, apiFilter) &&
                (selectedState == null || entry.implementationState == selectedState)
        }

        if (filtered.isEmpty()) {
            results.addView(TextView(this).apply { text = "No parity entries match the current filters." })
            return
        }

        filtered.groupBy { it.category }
            .toSortedMap(compareBy(String::lowercase))
            .forEach { (category, entries) ->
                val categoryImplemented = entries.count(::isImplemented)
                val categoryAvailable = entries.count { entry -> isOperational(entry, resolved.getValue(entry)) }
                results.addView(TextView(this).apply {
                    text = "${displayLabel(category)} — ${entries.size} catalogued · $categoryImplemented implemented · $categoryAvailable available on this device"
                    textSize = 17f
                    setPaddingDp(0, 18, 0, 6)
                })
                entries.sortedBy { displayLabel(it.googleTitle).lowercase() }.forEach { entry ->
                    addEntryRow(entry, resolved.getValue(entry))
                }
            }
    }

    private fun addEntryRow(entry: TestDpcParityEntry, availability: ParityAvailability) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingDp(0, 2, 0, 2)
        }
        val star = Button(this).apply {
            text = if (favorites.isFavorite(entry.id)) "★" else "☆"
            contentDescription = if (favorites.isFavorite(entry.id)) "Remove favorite" else "Add favorite"
            isAllCaps = false
            setOnClickListener {
                favorites.toggle(entry.id)
                renderResults()
            }
        }
        row.addView(star, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(Button(this).apply {
            text = "${displayLabel(entry.googleTitle)}\n${entry.testDpcKey} · ${availabilityLabel(entry, availability)}"
            isAllCaps = false
            textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
            setPaddingDp(14, 10, 14, 10)
            setOnClickListener {
                startActivity(Intent(this@TestDpcParityCenterActivity, TestDpcParityDetailActivity::class.java).apply {
                    putExtra(EXTRA_PARITY_ID, entry.id)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        results.addView(row)
    }

    private fun matchesQuery(entry: TestDpcParityEntry, query: String): Boolean {
        if (query.isBlank()) return true
        return listOf(entry.googleTitle, entry.testDpcKey, entry.category, entry.description, displayLabel(entry.googleTitle), displayLabel(entry.category))
            .any { it.lowercase().contains(query) }
    }

    private fun matchesScope(
        entry: TestDpcParityEntry,
        availability: ParityAvailability,
        scope: ScopeFilter,
    ): Boolean = when (scope) {
        ScopeFilter.ALL -> true
        ScopeFilter.AVAILABLE -> isOperational(entry, availability)
        ScopeFilter.UNSUPPORTED -> availability !is ParityAvailability.Deprecated && !isOperational(entry, availability)
        ScopeFilter.DEPRECATED -> availability is ParityAvailability.Deprecated
        ScopeFilter.DEVICE_OWNER -> entry.ownerRequirement == OwnerRequirement.DEVICE_OWNER ||
            entry.ownerRequirement == OwnerRequirement.DEVICE_OR_PROFILE_OWNER
        ScopeFilter.PROFILE_OWNER -> entry.ownerRequirement == OwnerRequirement.PROFILE_OWNER ||
            entry.ownerRequirement == OwnerRequirement.DEVICE_OR_PROFILE_OWNER
        ScopeFilter.COPE -> entry.ownerRequirement == OwnerRequirement.COPE
        ScopeFilter.FAVORITES -> favorites.isFavorite(entry.id)
    }

    private fun matchesApi(entry: TestDpcParityEntry, filter: ApiFilter): Boolean = when (filter) {
        ApiFilter.ALL -> true
        ApiFilter.SUPPORTED -> entry.minSdk <= facts.sdkInt
        ApiFilter.ABOVE_DEVICE -> entry.minSdk > facts.sdkInt
    }

    private fun isImplemented(entry: TestDpcParityEntry): Boolean =
        entry.destination != null || entry.implementationState == TestDpcImplementationState.EXPOSE_BACKEND

    private fun isOperational(entry: TestDpcParityEntry, availability: ParityAvailability): Boolean =
        availability is ParityAvailability.Available && entry.destination != null

    private fun availabilityLabel(entry: TestDpcParityEntry, availability: ParityAvailability): String = when (availability) {
        ParityAvailability.Available -> if (entry.destination != null) "Available" else "Backend ready · handler wiring pending"
        is ParityAvailability.Unavailable -> "Unsupported: ${availability.reason}"
        is ParityAvailability.Deprecated -> "Deprecated: ${availability.reason}"
    }

    private fun displayLabel(raw: String): String {
        val value = raw.removePrefix("@string/").replace('_', ' ')
        return value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    companion object {
        const val EXTRA_PARITY_ID = "io.dpcaio.extra.TESTDPC_PARITY_ID"
    }
}
