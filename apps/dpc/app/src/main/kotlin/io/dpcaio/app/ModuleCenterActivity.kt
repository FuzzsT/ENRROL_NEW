package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
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
        val body = LinearLayout(this)
        val visibleCount = resolved.count { shouldShow(it.first, it.second, state.selectedFilter) }

        body.addView(ModernUi.hero(
            this,
            title = "Module Center",
            subtitle = "$visibleCount visible • API ${management.apiLevel} • ${management.ownership}",
            eyebrow = "${DpcModuleRegistry.modules.size} integrated modules",
        ))

        body.addView(ModernUi.section(this, "Visibility & filters"))
        body.addView(ModernUi.card(this, emphasized = true).apply {
            addView(ModernUi.title(this@ModuleCenterActivity, "Module visibility"))
            addView(ModernUi.body(
                this@ModuleCenterActivity,
                "Filter operational, unavailable, Samsung/Knox and Lab surfaces without leaving the module registry.",
            ))
            addView(ModernUi.spacer(this@ModuleCenterActivity, 8))
            addView(CheckBox(this@ModuleCenterActivity).apply {
                text = "Show hidden"
                isChecked = state.showHidden
                setOnCheckedChangeListener { _, checked ->
                    DpcUiPreferences.setShowHidden(this@ModuleCenterActivity, checked)
                    render()
                }
            })
            addView(CheckBox(this@ModuleCenterActivity).apply {
                text = "Developer / Lab"
                isChecked = state.developerMode
                setOnCheckedChangeListener { _, checked ->
                    DpcUiPreferences.setDeveloperMode(this@ModuleCenterActivity, checked)
                    render()
                }
            })
            addView(CheckBox(this@ModuleCenterActivity).apply {
                text = "Show experimental"
                isChecked = state.showExperimental
                setOnCheckedChangeListener { _, checked ->
                    DpcUiPreferences.setShowExperimental(this@ModuleCenterActivity, checked)
                    render()
                }
            })

            val spinner = Spinner(this@ModuleCenterActivity)
            spinner.adapter = ArrayAdapter(this@ModuleCenterActivity, android.R.layout.simple_spinner_dropdown_item, filterLabels)
            val selected = filterIds.indexOf(state.selectedFilter).takeIf { it >= 0 } ?: 0
            spinner.setSelection(selected, false)
            spinner.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
                val selectedFilter = filterIds[position]
                if (selectedFilter != DpcUiPreferences.read(this@ModuleCenterActivity).selectedFilter) {
                    DpcUiPreferences.setSelectedFilter(this@ModuleCenterActivity, selectedFilter)
                    render()
                }
            })
            addView(spinner)
        })

        DpcModuleGroup.entries.forEach { group ->
            val grouped = resolved.filter { (module, resolution) ->
                module.group == group && shouldShow(module, resolution, DpcUiPreferences.read(this).selectedFilter)
            }
            if (grouped.isEmpty()) return@forEach
            body.addView(ModernUi.section(this, "${group.label} (${grouped.size})"))
            grouped.forEach { (module, resolution) -> addModule(body, module, resolution) }
        }

        setContentView(ModernUi.page(this, body))
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
        val tone = if (resolution.executable) "success" else "warning"
        val card = ModernUi.card(this, emphasized = resolution.executable).apply {
            addView(ModernUi.title(this@ModuleCenterActivity, module.title))
            addView(ModernUi.body(
                this@ModuleCenterActivity,
                "${module.id} • ${module.surface} • API ${module.requirements.minApi}+ • ${module.requirements.ownership} • ${module.requirements.risk}",
            ))
            addView(ModernUi.chip(this@ModuleCenterActivity, resolution.availability.name, tone))
            resolution.reason?.let { reason ->
                addView(ModernUi.spacer(this@ModuleCenterActivity, 8))
                addView(ModernUi.body(this@ModuleCenterActivity, reason))
            }
            module.entryActivity?.let { target ->
                addView(ModernUi.spacer(this@ModuleCenterActivity, 10))
                addView(ModernUi.action(this@ModuleCenterActivity, "Open UI") {
                    if (resolution.executable) startActivity(Intent(this@ModuleCenterActivity, target))
                }.apply { isEnabled = resolution.executable })
            }
        }
        root.addView(card)
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: (Int) -> Unit,
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) =
        onSelected(position)

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
