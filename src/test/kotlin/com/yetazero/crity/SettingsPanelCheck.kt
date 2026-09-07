package com.yetazero.crity

import com.hypixel.hytale.protocol.packets.interface_.CustomPage
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.yetazero.crity.compat.ApiCapabilities
import com.yetazero.crity.config.HudAppearance
import com.yetazero.crity.config.HudStyle
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.display.DamageFormatter
import com.yetazero.crity.lifecycle.WeakSessions
import com.yetazero.crity.ui.SettingsDraft
import com.yetazero.crity.ui.SettingsFields
import com.yetazero.crity.ui.SettingsPageData
import com.yetazero.crity.ui.SettingsPanelRenderer
import com.yetazero.crity.ui.SettingsSnapshot
import com.yetazero.crity.ui.SettingsTab
import org.bson.BsonDocument
import com.hypixel.hytale.codec.ExtraInfo
import java.lang.foreign.MemorySegment
import java.util.UUID

fun checkSettingsPanel() {
    ApiCapabilities.settingsPanel()
    check(runCatching { ApiCapabilities.requireMethod("java.lang.String", "missingFutureApi") }.isFailure)
    val base = CrityState.PlayerSettings()
    val original = SettingsSnapshot(base)
    val draft = SettingsDraft(original)
    val paths = VisualSettingsCodec.encode(base.visual).entrySet().flatMap { (section, value) ->
        value.asJsonObject.entrySet().filter { it.value.isJsonPrimitive }.map { "$section.${it.key}" }
    }.toSet()
    check(paths == SettingsFields.all.map { it.path }.filter { '.' in it }.toSet())
    check(SettingsFields.byPath.size == SettingsFields.all.size)
    for (field in SettingsFields.all) draft.field(field.path, draft.value(field.path))
    check(!draft.dirty && draft.current == original)
    draft.field("hud.position", "BOTTOM_RIGHT")
    draft.field("hud.x", "350")
    draft.field("hud.color", "123abc")
    check(draft.dirty && base.visual == original.visual)
    val before = draft.current
    check(runCatching { draft.field("hud.width", "2") }.isFailure)
    check(draft.current == before)
    val id = draft.addRule("fire_test")
    draft.rule(id, "cause", "Fire")
    draft.rule(id, "format", "BURN {amount}")
    draft.rule(id, "color", "ff8800")
    draft.rule(id, "maxAmount", "")
    check(draft.current.visual.damage.rules.first().maxAmount == Float.MAX_VALUE)
    check(DamageFormatter.format(DamageContext(42f, 0.9f, "Fire"), draft.current.visual.damage)?.text == "BURN 42")
    draft.rule(id, "enabled", "false")
    check(DamageFormatter.format(DamageContext(42f, 0.9f, "Fire"), draft.current.visual.damage)?.text == "CRIT! 42")
    draft.rule(id, "enabled", "true")
    draft.moveRule(id, 1)
    check(draft.current.visual.damage.rules[1].id == id)
    draft.removeRule(id)
    draft.field("damage.showCriticalLabel", "false")
    check(DamageFormatter.format(DamageContext(42f, 0.9f), draft.current.visual.damage)?.text == "42")
    check(draft.current.visual.damage.rules.first().format == "CRIT! {amount}")
    draft.field("damage.showCriticalLabel", "true")
    check(DamageFormatter.format(DamageContext(42f, 0.9f), draft.current.visual.damage)?.text == "CRIT! 42")
    draft.accept()
    check(!draft.dirty)
    draft.reset(VisualSettings())
    check(draft.current.visual == VisualSettings())

    for ((payload, expected) in listOf("""{"Action":"field","Path":"hud.width","@Number":420.0}""" to "420",
        """{"Action":"field","Path":"hud.showText","@Flag":false}""" to "false",
        """{"Action":"rule","Path":"critical.format","@Text":"HIT {amount}"}""" to "HIT {amount}")) {
        val event = checkNotNull(SettingsPageData.CODEC.decode(BsonDocument.parse(payload), ExtraInfo.THREAD_LOCAL.get()))
        check(event.value() == expected)
    }
    for (tab in SettingsTab.entries) {
        val commands = UICommandBuilder()
        val events = UIEventBuilder()
        SettingsPanelRenderer.fields(draft, tab, "critical", 7, commands, events)
        checkClientCommands(commands)
        check(events.events.isNotEmpty() && events.events.size <= 64)
        check(commands.commands.size <= 300)
        val packet = CustomPage("CritySettingsPage", false, false, CustomPageLifetime.CanDismiss, commands.commands, events.events)
        val bytes = MemorySegment.ofArray(ByteArray(packet.computeSize()))
        packet.serialize(bytes, 0)
        check(CustomPage.toObject(bytes) == packet)
    }
    val first = UICommandBuilder()
    val sample = DamageContext(42f, 0.9f)
    val cache = SettingsPanelRenderer.preview(draft, sample, false, first)
    draft.field("hud.x", "20")
    val hudOnly = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, true, hudOnly, cache)
    check(hudOnly.commands.none { it.selector?.startsWith("#Palette") == true })
    check(hudOnly.commands.any { it.selector == "#FullHud" })
    val updated = SettingsPanelRenderer.preview(draft, sample, false, UICommandBuilder())
    val unchanged = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, false, unchanged, updated)
    check(unchanged.commands.size <= 8)
    check(unchanged.commands.none { it.selector in setOf("#Palette", "#MiniHud", "#FullHud") })
    draft.field("damageMode", "DEFAULT")
    val vanilla = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, false, vanilla)
    check(vanilla.commands.first { it.selector == "#SampleDamage.Text" }.data?.contains("CRIT") != true)
    repeat(60) { draft.addRule() }
    check(draft.current.visual.damage.rules.size == 64)
    check(runCatching { draft.addRule() }.isFailure)
    val full = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, false, full)
    check(full.commands.size < 400)
    checkPreviewAnchors(original, sample)
    checkSettingsUiContracts(original)

    val sessions = WeakSessions<Any>()
    val uuid = UUID.randomUUID()
    val old = Any()
    val current = Any()
    sessions.put(uuid, old)
    sessions.put(uuid, current)
    sessions.remove(uuid, old)
    check(sessions[uuid] === current)
    sessions.remove(uuid, current)
    check(sessions.values().isEmpty())
    repeat(1000) {
        val value = Any()
        val id = UUID.randomUUID()
        sessions.put(id, value)
        check(sessions[id] === value)
        sessions.remove(id, value)
    }
    check(sessions.values().isEmpty())
    println("PASS: complete settings coverage, isolated drafts, critical label toggle, rule CRUD/limits, UI event codec and packet round-trips, complete preview anchors, incremental preview updates, weak session lifecycle")
}

private fun checkPreviewAnchors(original: SettingsSnapshot, sample: DamageContext) {
    for (style in HudStyle.entries) for (appearance in listOf(
        HudAppearance(style = style),
        HudAppearance(style = style, width = 80, barHeight = 2, padding = 0),
        HudAppearance(style = style, width = 1200, barHeight = 100, padding = 32)
    )) {
        val draft = SettingsDraft(original.copy(visual = original.visual.copy(hud = appearance)))
        val builder = UICommandBuilder()
        SettingsPanelRenderer.preview(draft, sample, true, builder)
        checkClientCommands(builder)
        check(builder.commands.none { it.selector?.contains(".Anchor.") == true })
        fun anchor(selector: String): BsonDocument {
            val command = builder.commands.single { it.selector == "$selector.Anchor" }
            return BsonDocument.parse(checkNotNull(command.data)).getDocument("0")
        }
        val miniature = appearance.copy(width = minOf(310, appearance.width))
        val container = anchor("#MiniHud")
        check(container.getInt32("Height").value >= miniature.boxHeight + 12)
        check(container.getInt32("Bottom").value == 4)
        for ((parent, hud) in listOf("#MiniHud" to miniature, "#FullHud" to appearance)) {
            if (hud.style == HudStyle.SEGMENTED) {
                for (i in 0 until hud.segments) check(builder.commands.any { it.selector == "$parent #Segment$i.Background" })
            } else for ((fill, percent) in listOf("#HealthFill" to 0.65, "#PhantomFill" to 0.9)) {
                val bounds = anchor("$parent $fill")
                check(bounds.getInt32("Left").value == 0 && bounds.getInt32("Top").value == 0)
                check(bounds.getInt32("Width").value == (hud.barLength * percent).toInt())
                check(bounds.getInt32("Height").value == hud.barThickness)
                check(bounds.keys == setOf("Left", "Top", "Width", "Height"))
            }
        }
        val packet = CustomPage("CritySettingsPage", false, false, CustomPageLifetime.CanDismiss, builder.commands, emptyArray())
        val bytes = MemorySegment.ofArray(ByteArray(packet.computeSize()))
        packet.serialize(bytes, 0)
        check(CustomPage.toObject(bytes) == packet)
    }
}
