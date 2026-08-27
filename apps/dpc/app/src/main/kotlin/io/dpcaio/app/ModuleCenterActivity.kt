package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.core.model.CapabilityAvailability
import io.dpcaio.core.model.CapabilityResolution
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.VisibilityClass

class ModuleCenterActivity : Activity() {
    private val filterIds = listOf("all", "available", "unavailable", "samsung_knox", "lab")
    private val filterLabels = listOf("All", "Available", "Unavailable", "Samsung / Knox", "Lab")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Module Center"
        render()
    }

    private fun render() {
        val state = DpcUiPreferences.read(this)
        val management = ManagementContextFactory.create(this)
        val resolved = DpcModuleRegistry.modules.map { it to CapabilityResolver.resolve(it.requirements, management) }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 24, 24, 24)
        }
        val visibleCount = resolved.count { shouldShow(it.first, it.second, state.selectedFilter) }
        body.addView(TextView(this).apply {
            text = "${DpcModuleRegistry.modules.size}/${DpcModuleRegistry.modules.size} modules integrated into :app-dpc\n" +
                "$visibleCount visible for current context • API ${management.apiLevel} • ${management.ownership}"
        })

        body.addView(CheckBox(this).apply {
            text = "Show hidden"
            isChecked = state.showHidden
            setOnCheckedChangeListener { _, checked ->
                DpcUiPreferences.setShowHidden(this@ModuleCenterActivity, checked)
                render()
            }
        })
        body.addView(CheckBox(this).apply {
            text = "Developer / Lab"
            isChecked = state.developerMode
            setOnCheckedChangeListener { _, checked ->
                DpcUiPreferences.setDeveloperMode(this@ModuleCenterActivity, checked)
                render()
            }
        })
        body.addView(CheckBox(this).apply {
            text = "Show experimental"
            isChecked = state.showExperimental
            setOnCheckedChangeListener { _, checked ->
                DpcUiPreferences.setShowExperimental(this@ModuleCenterActivity, checked)
                render()
            }
        })

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterLabels)
        val selected = filterIds.indexOf(state.selectedFilter).takeIf { it >= 0 } ?: 0
        spinner.setSelection(selected, false)
        spinner.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            val selectedFilter = filterIds[position]
            if (selectedFilter != DpcUiPreferences.read(this).selectedFilter) {
                DpcUiPreferences.setSelectedFilter(this, selectedFilter)
                render()
            }
        })
        body.addView(spinner)

        DpcModuleGroup.entries.forEach { group ->
            val grouped = resolved.filter { (module, resolution) ->
                module.group == group && shouldShow(module, resolution, DpcUiPreferences.read(this).selectedFilter)
            }
            if (grouped.isEmpty()) return@forEach
            body.addView(TextView(this).apply {
                text = "\n${group.label} (${grouped.size})"
                setTypeface(typeface, Typeface.BOLD)
                textSize = 18f
            })
            grouped.forEach { (module, resolution) -> addModule(body, module, resolution) }
        }

        setContentView(DpcUiShell.scroll(this, body))
    }

    private fun shouldShow(
        module: DpcModuleDescriptor,
        resolution: CapabilityResolution,
        selectedFilter: String,
    ): Boolean {
        if (!resolution.visible) return false
        return when (selectedFilter) {
            "available" -> resolution.executable
            "unavailable" -> !resolution.executable
            "samsung_knox" -> "samsung_knox" in module.tags
            "lab" -> module.requirements.visibility == VisibilityClass.LAB || "lab" in module.tags
            "all" -> true
            else -> true
        }
    }

    private fun addModule(root: LinearLayout, module: DpcModuleDescriptor, resolution: CapabilityResolution) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(0, 12, 0, 12)
        }
        row.addView(TextView(this).apply {
            text = "${module.title}  [${module.id}]"
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = "${resolution.availability.name} • ${module.surface} • API ${module.requirements.minApi}+ • " +
                "${module.requirements.ownership} • ${module.requirements.risk}"
        })
        resolution.reason?.let { reason ->
            row.addView(TextView(this).apply { text = reason })
        }
        module.entryActivity?.let { target ->
            row.addView(Button(this).apply {
                text = "Open UI"
                isEnabled = resolution.executable
                setOnClickListener { startActivity(Intent(this@ModuleCenterActivity, target)) }
            })
        }
        root.addView(row)
        root.addView(View(this).apply {
            minimumHeight = 1
            setBackgroundColor(0x22000000)
        })
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: (Int) -> Unit,
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) =
        onSelected(position)

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
