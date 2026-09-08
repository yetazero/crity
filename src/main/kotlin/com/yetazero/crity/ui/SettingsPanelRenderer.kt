package com.yetazero.crity.ui

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo
import com.hypixel.hytale.server.core.ui.LocalizableString
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.yetazero.crity.CrityState
import com.yetazero.crity.config.HudAppearance
import com.yetazero.crity.config.HudPosition
import com.yetazero.crity.config.HudStyle
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.display.DamageFormatter
import com.yetazero.crity.display.HealthHudLayout
import com.yetazero.crity.display.ReticleLayout
import java.util.Locale
import kotlin.math.ceil

internal enum class SettingsTab { DAMAGE, RULES, HUD, HIGHLIGHT, RETICLE }

internal data class PreviewState(val settings: SettingsSnapshot, val sample: DamageContext, val fullScreen: Boolean, val reticle: Boolean = false)

internal object SettingsPanelRenderer {
    fun entries(values: List<String>) = values.map {
        DropdownEntryInfo(LocalizableString.fromString(it.lowercase(Locale.ROOT).replace('_', ' ')), it)
    }

    fun action(events: UIEventBuilder, selector: String, action: String, path: String = "") {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action).append("Path", path), false)
    }

    fun bind(events: UIEventBuilder, selector: String, action: String, path: String, key: String, epoch: Int? = null) {
        val data = EventData.of("Action", action).append("Path", path).append(key, "$selector.Value").append("Source", selector)
        if (epoch != null) data.append("Epoch", epoch.toString())
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false)
    }

    fun fields(draft: SettingsDraft, tab: SettingsTab, selected: String?, epoch: Int,
               commands: UICommandBuilder, events: UIEventBuilder, editRangedReticle: Boolean = false) {
        commands.clear("#Fields")
        commands.set("#RuleTools.Visible", tab == SettingsTab.RULES)
        commands.set("#HudTools.Visible", tab == SettingsTab.HUD)
        commands.set("#ReticlePreviewPanel.Visible", tab == SettingsTab.RETICLE)
        commands.set("#CombatPreview.Visible", tab != SettingsTab.RETICLE)
        commands.set("#ReticleProfileTools.Visible", tab == SettingsTab.RETICLE)
        commands.set("#ReticleMelee.Disabled", !editRangedReticle)
        commands.set("#ReticleRanged.Disabled", editRangedReticle)
        commands.set("#SectionTitle.Text", when (tab) {
            SettingsTab.DAMAGE -> "Damage numbers"
            SettingsTab.RULES -> "Colors and custom text"
            SettingsTab.HUD -> "Your target health HUD"
            SettingsTab.HIGHLIGHT -> "Target highlight"
            SettingsTab.RETICLE -> if (editRangedReticle) "Ranged reticle" else "Melee reticle"
        })
        commands.set("#SectionHint.Text", when (tab) {
            SettingsTab.DAMAGE -> "Choose custom, vanilla or hidden numbers. Color rules override the fallback color and text."
            SettingsTab.RULES -> "The first matching rule wins. Add your own colors and text, then move rules to change their priority."
            SettingsTab.HUD -> "Choose a style and colors. Place HUD opens a full-screen preview at its actual size."
            SettingsTab.HIGHLIGHT -> "Glow marks your last confirmed hit for the HUD duration. Bounds are diagnostic boxes; they do not guarantee a hit."
            SettingsTab.RETICLE -> "Edit each profile independently. Both previews use your draft. AUTO switches with weapon tags; MELEE and RANGED lock a profile."
        })
        if (tab == SettingsTab.RULES) {
            val rules = draft.current.visual.damage.rules
            commands.set("#RuleList.Entries", rules.mapIndexed { index, rule ->
                DropdownEntryInfo(LocalizableString.fromString("${index + 1}. ${rule.id}${if (rule.enabled) "" else " (off)"}"), rule.id)
            })
            commands.set("#RuleDelete.Disabled", selected == null)
            commands.set("#RuleUp.Disabled", selected == null || rules.firstOrNull()?.id == selected)
            commands.set("#RuleDown.Disabled", selected == null || rules.lastOrNull()?.id == selected)
            commands.set("#RuleAdd.Disabled", rules.size >= 64)
            if (selected != null) {
                commands.set("#RuleList.Value", selected)
                SettingsFields.rule.forEachIndexed { index, field ->
                    row(commands, events, "#Fields", index, field, draft.ruleValue(selected, field.path), "rule", "$selected.${field.path}", epoch)
                }
            }
        } else {
            val fields = when (tab) {
                SettingsTab.DAMAGE -> SettingsFields.modes + SettingsFields.damage
                SettingsTab.HUD -> SettingsFields.hud
                SettingsTab.HIGHLIGHT -> SettingsFields.highlight
                SettingsTab.RETICLE -> SettingsFields.reticleFor(editRangedReticle)
            }
            fields.forEachIndexed { index, field ->
                row(commands, events, "#Fields", index, field, draft.value(field.path), "field", field.path, epoch)
            }
        }
    }

    fun row(commands: UICommandBuilder, events: UIEventBuilder, parent: String, index: Int, field: SettingsField,
            value: String, action: String, path: String, epoch: Int? = null) {
        val kind = field.kind.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercaseChar() }
        commands.append(parent, "Crity/Field$kind.ui")
        val selector = "$parent[$index]"
        commands.set("$selector #Label.Text", field.label)
        if (field.hint.isNotEmpty()) commands.set("$selector #Label.TooltipText", field.hint)
        when (field.kind) {
            FieldKind.NUMBER -> {
                val number = field.numberValue(value)
                commands.set("$selector #Slider.Min", field.min)
                commands.set("$selector #Slider.Max", field.max)
                commands.set("$selector #Slider.Step", field.step)
                commands.set("$selector #Slider.Value", number)
                commands.set("$selector #Input.Format.MinValue", field.min)
                commands.set("$selector #Input.Format.MaxValue", field.max)
                commands.set("$selector #Input.Format.Step", field.step)
                commands.set("$selector #Input.Format.MaxDecimalPlaces", if (field.step >= 1.0) 0 else 2)
                commands.set("$selector #Input.Value", number)
                bind(events, "$selector #Slider", action, path, "@Number", epoch)
                bind(events, "$selector #Input", action, path, "@Number", epoch)
            }
            FieldKind.TOGGLE -> {
                commands.set("$selector #Input.Value", value.toBoolean())
                bind(events, "$selector #Input", action, path, "@Flag", epoch)
            }
            FieldKind.COLOR -> {
                commands.set("$selector #Input.Value", value)
                commands.set("$selector #Swatch.Background", value)
                bind(events, "$selector #Input", action, path, "@Text", epoch)
            }
            FieldKind.CHOICE -> {
                commands.set("$selector #Input.Entries", entries(field.choices))
                commands.set("$selector #Input.Value", value)
                bind(events, "$selector #Input", action, path, "@Text", epoch)
            }
            FieldKind.TEXT -> {
                commands.set("$selector #Input.Value", value)
                bind(events, "$selector #Input", action, path, "@Text", epoch)
            }
        }
    }

    fun syncControl(commands: UICommandBuilder, selector: String, field: SettingsField, value: String, source: String?) {
        if (field.kind == FieldKind.NUMBER) {
            val number = field.numberValue(value)
            if (source != "$selector #Slider") commands.set("$selector #Slider.Value", number)
            if (source != "$selector #Input") commands.set("$selector #Input.Value", number)
        } else if (field.kind == FieldKind.COLOR) {
            if (source != "$selector #Input") commands.set("$selector #Input.Value", value)
            commands.set("$selector #Swatch.Background", value)
        }
    }

    fun preview(draft: SettingsDraft, sample: DamageContext, fullScreen: Boolean, commands: UICommandBuilder, before: PreviewState? = null, reticle: Boolean = false): PreviewState {
        val previous = before?.takeIf { it.reticle == (reticle && !fullScreen) }
        val visual = draft.current.visual
        if (reticle && !fullScreen) {
            val before = previous?.takeIf { it.reticle && !it.fullScreen }?.settings?.visual?.reticle
            for ((selector, profile, old) in listOf(
                Triple("#MeleeReticlePreview", visual.reticle.melee, before?.melee),
                Triple("#RangedReticlePreview", visual.reticle.ranged, before?.ranged))) {
                if (profile != old) commands.clear(selector).appendInline(selector, ReticleLayout.build(profile))
            }
            commands.set("#ReticlePreviewNote.Text", if (!visual.reticle.enabled) "Custom reticle is off. These previews show your saved or draft designs." else "Both profiles are shown at their actual UI size. Save changes to apply.")
            return PreviewState(draft.current, sample, fullScreen, true)
        }
        val label = when (draft.current.damage) {
            CrityState.DamageMode.OFF -> null
            CrityState.DamageMode.ON -> DamageFormatter.format(sample, visual.damage)
            CrityState.DamageMode.DEFAULT -> DamageFormatter.format(sample, visual.damage.copy(format = "{amount}", rules = emptyList(), color = "#ffffff", decimals = 0))
        }
        commands.set("#SampleDamage.Text", label?.text ?: "Numbers hidden")
        commands.set("#SampleDamage.Style.TextColor", if (draft.current.damage == CrityState.DamageMode.DEFAULT) "#ffffff"
            else label?.let { String.format(Locale.ROOT, "#%06x", it.rgb) } ?: "#96a9be")
        commands.set("#RatioLabel.Text", "Damage ratio: ${(sample.percent * 100).toInt()}%")
        commands.set("#HighlightSwatch.Background", visual.hitboxes.color)
        commands.set("#HighlightState.Text", if (visual.hitboxes.enabled) "${visual.hitboxes.mode} - ${visual.hud.durationMs / 1000f}s" else "Highlight off")
        commands.set("#PreviewSummary.Text", "Damage: ${draft.current.damage} | Health: ${draft.current.health}\n" +
            "HUD: ${visual.hud.position}, ${visual.hud.width} wide\n" +
            "Spread: ${if (visual.damage.randomAngle) "${visual.damage.minAngle} to ${visual.damage.maxAngle}" else "hit direction"}")
        val hudChanged = previous?.settings?.visual?.hud != visual.hud || previous.settings.health != draft.current.health
        if (hudChanged) {
            commands.clear("#MiniHud")
            val miniature = visual.hud.copy(position = HudPosition.CENTER, x = 0, y = 0, width = minOf(310, visual.hud.width))
            commands.setObject("#MiniHud.Anchor", Anchor().apply {
                setHeight(Value.of(maxOf(70, miniature.boxHeight + 12)))
                setBottom(Value.of(4))
            })
            commands.appendInline("#MiniHud", HealthHudLayout.build(miniature))
            commands.set("#MiniHud #HealthText.Text", HealthHudLayout.healthText(65f, 100f, miniature))
            applyBarPreview(commands, "#MiniHud", miniature, 0.65f, 0.9f)
            commands.set("#MiniHud.Visible", draft.current.health == CrityState.HealthMode.ON)
        }
        val paletteChanged = previous?.settings?.visual?.damage != visual.damage || previous.sample != sample
        val structureChanged = previous?.settings?.visual?.damage?.rules?.map { it.id } != visual.damage.rules.map { it.id }
        if (structureChanged) commands.clear("#Palette")
        if (paletteChanged) visual.damage.rules.forEachIndexed { index, rule ->
            if (!structureChanged && previous.sample == sample && previous.settings.visual.damage.decimals == visual.damage.decimals &&
                previous.settings.visual.damage.showCriticalLabel == visual.damage.showCriticalLabel &&
                previous.settings.visual.damage.rules.getOrNull(index) == rule) return@forEachIndexed
            if (structureChanged) commands.append("#Palette", "Crity/PaletteRow.ui")
            val selector = "#Palette[$index]"
            val example = DamageFormatter.format(sample.copy(amount = maxOf(sample.amount, rule.minAmount).coerceAtMost(rule.maxAmount),
                percent = maxOf(sample.percent, rule.minPercent), cause = rule.cause.ifEmpty { sample.cause },
                weapon = rule.weaponPrefix.ifEmpty { sample.weapon }), visual.damage.copy(rules = listOf(rule)))
            commands.set("$selector #Name.Text", rule.id)
            commands.set("$selector #Swatch.Background", rule.color)
            commands.set("$selector #Sample.Text", if (rule.enabled) example?.text ?: "0" else "Disabled")
            commands.set("$selector #Sample.Style.TextColor", rule.color)
        }
        if (fullScreen && (hudChanged || previous.fullScreen != true)) {
            commands.clear("#FullHud")
            commands.appendInline("#FullHud", HealthHudLayout.build(visual.hud))
            commands.set("#FullHud #HealthText.Text", HealthHudLayout.healthText(65f, 100f, visual.hud))
            applyBarPreview(commands, "#FullHud", visual.hud, 0.65f, 0.9f)
        }
        return PreviewState(draft.current, sample, fullScreen)
    }

    private fun applyBarPreview(commands: UICommandBuilder, prefix: String, hud: HudAppearance, currentPct: Float, phantomPct: Float) {
        if (hud.style == HudStyle.SEGMENTED) {
            val filled = ceil(currentPct * hud.segments).toInt().coerceIn(0, hud.segments)
            val phantomFilled = ceil(phantomPct * hud.segments).toInt().coerceIn(0, hud.segments)
            for (i in 0 until hud.segments) {
                val on = if (hud.invert) i >= hud.segments - filled else i < filled
                val trailing = if (hud.invert) i >= hud.segments - phantomFilled else i < phantomFilled
                val color = if (on) hud.color else if (trailing && hud.showTrail) hud.trailColor else hud.trackColor
                commands.set("$prefix #Segment$i.Background", color)
            }
        } else {
            commands.setObject("$prefix #HealthFill.Anchor", fillAnchor(hud, currentPct))
            commands.setObject("$prefix #PhantomFill.Anchor", fillAnchor(hud, phantomPct))
        }
    }

    private fun fillAnchor(hud: HudAppearance, pct: Float): Anchor {
        val length = (hud.barLength * pct).toInt().coerceIn(0, hud.barLength)
        val offset = hud.barLength - length
        return Anchor().apply {
            if (hud.isVertical) {
                setWidth(Value.of(hud.barThickness))
                setHeight(Value.of(length))
                setLeft(Value.of(0))
                setTop(Value.of(if (hud.invert) 0 else offset))
            } else {
                setWidth(Value.of(length))
                setHeight(Value.of(hud.barThickness))
                setTop(Value.of(0))
                setLeft(Value.of(if (hud.invert) offset else 0))
            }
        }
    }
}
