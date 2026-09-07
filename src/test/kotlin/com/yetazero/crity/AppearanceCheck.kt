package com.yetazero.crity

import com.google.gson.JsonParser
import com.yetazero.crity.compat.FeatureGate
import com.yetazero.crity.compat.SystemRegistryAccess
import com.yetazero.crity.compat.SystemReplacement
import com.yetazero.crity.config.CrityExports
import com.yetazero.crity.config.DamageAppearance
import com.yetazero.crity.config.DamageRule
import com.yetazero.crity.config.HudAppearance
import com.yetazero.crity.config.HudPosition
import com.yetazero.crity.config.HudStyle
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.config.VisualSettingsEditor
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.display.DamageFormatter
import com.yetazero.crity.display.HealthHudLayout
import com.hypixel.hytale.protocol.Color
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

fun checkAppearance() {
    val base = VisualSettings()
    var custom = VisualSettingsEditor.set(base, "hud.position", "bottom-right")
    custom = VisualSettingsEditor.set(custom, "hud.x", "25")
    custom = VisualSettingsEditor.set(custom, "hud.color", "AB12cd")
    custom = VisualSettingsEditor.set(custom, "damage.decimals", "2")
    check(custom.hud.position == HudPosition.BOTTOM_RIGHT && custom.hud.x == 25 && custom.hud.color == "#ab12cd")
    check(base.hud.position == HudPosition.TOP && base.hud.color == "#e74c3c")
    custom = VisualSettingsEditor.addRule(custom, "fire")
    custom = VisualSettingsEditor.rule(custom, "fire", "cause", "Fire")
    custom = VisualSettingsEditor.rule(custom, "fire", "weaponPrefix", "Weapon_Sword")
    custom = VisualSettingsEditor.rule(custom, "fire", "color", "ff00ff")
    custom = VisualSettingsEditor.rule(custom, "fire", "format", "{cause}: {amount}!")
    custom = VisualSettingsEditor.rule(custom, "fire", "minAmount", "20")
    custom = VisualSettingsEditor.rule(custom, "fire", "maxAmount", "30")
    check(DamageFormatter.format(DamageContext(24.25f, 1f, "Fire", "Weapon_Sword_Iron"), custom.damage)?.text == "Fire: 24.25!")
    check(DamageFormatter.format(DamageContext(24.25f, 1f, "fire", "Weapon_Sword_Iron"), custom.damage)?.rgb == 0xff00ff)
    for (hit in listOf(DamageContext(31f, 1f, "Fire", "Weapon_Sword_Iron"),
        DamageContext(24f, 1f, "Physical", "Weapon_Sword_Iron"), DamageContext(24f, 1f, "Fire", "Weapon_Mace"))) {
        check(DamageFormatter.format(hit, custom.damage)?.text?.startsWith("CRIT!") == true)
    }
    val lowPriority = VisualSettingsEditor.moveRule(custom, "fire", custom.damage.rules.size)
    check(DamageFormatter.format(DamageContext(24f, 1f, "Fire", "Weapon_Sword_Iron"), lowPriority.damage)?.rgb == 0xff3838)
    check(VisualSettingsEditor.removeRule(custom, "fire").damage.rules.size == 4)
    val empty = DamageAppearance(color = "#123456", format = "DMG {amount}", rules = emptyList())
    check(DamageFormatter.format(DamageContext(5.9f, Float.NaN), empty) == com.yetazero.crity.display.DamageLabel("DMG 5", 0x123456))
    for ((path, value) in listOf("hud.color" to "red; Group {}", "hud.width" to "0", "hud.width" to "420.5",
        "hud.width" to "2147483648", "hud.position" to "sideways", "hud.opacity" to "NaN",
        "hud.durationMs" to "-1", "hud.showTrail" to "yes", "damage.decimals" to "3",
        "damage.minAngle" to "90", "damage.format" to "{unknown}", "damage.rules" to "[]", "hud.noSuchField" to "1")) {
        check(runCatching { VisualSettingsEditor.set(base, path, value) }.isFailure) { "$path accepted $value" }
    }
    val invalidJsons = listOf("""{"hud":{"color":null}}""", """{"damage":{"rules":[null]}}""",
        """{"hud":{"showText":"true"}}""", """{"hud":{"fontSize":13.1}}""",
        """{"hud":{"width":"420"}}""", """{"hud":{"position":"UNKNOWN"}}""",
        """{"damage":{"maxAngle":1e100}}""", """{"damage":{"rules":[{"id":"x"}]}}""")
    for (json in invalidJsons) check(runCatching { VisualSettingsCodec.decode(JsonParser.parseString(json).asJsonObject) }.isFailure)
    check(runCatching { base.copy(damage = base.damage.copy(rules = List(65) { DamageRule("r$it", "#ffffff") })).validate() }.isFailure)
    check(runCatching { base.copy(damage = base.damage.copy(rules = listOf(DamageRule("x", "#ffffff"), DamageRule("x", "#ffffff")))).validate() }.isFailure)
    check(VisualSettingsCodec.decode(VisualSettingsCodec.encode(custom)) == custom)
    val locale = Locale.getDefault()
    try {
        Locale.setDefault(Locale.GERMANY)
        check(DamageFormatter.format(DamageContext(1.25f, 0f), custom.damage)?.text == "1.25")
        check("#000000(0.750)" in HealthHudLayout.build(base.hud))
    } finally {
        Locale.setDefault(locale)
    }
    val narrow = base.damage.copy(minAngle = -10f, maxAngle = 10f)
    val angles = List(1000) { DamageFormatter.angle(270f, narrow, Random(it)) }
    check(angles.all { it in -10f..10f } && angles.any { it < 0f } && angles.any { it > 0f })
    check(DamageFormatter.angle(20f, narrow.copy(minAngle = -15f, maxAngle = -15f)) == -15f)
    check(DamageFormatter.angle(Float.NaN, narrow.copy(randomAngle = false)) == 0f)
    val nativeColor = Color(1, 2, 3)
    check(CrityCombatDisplay.text(CrityState.DamageMode.DEFAULT, 5f, 1f, 20f, defaultColor = nativeColor)?.color == nativeColor)
    check(CrityCombatDisplay.text(CrityState.DamageMode.DEFAULT, 5f, 1f, 20f, defaultColor = null)?.color == null)

    for (position in HudPosition.entries) for (style in HudStyle.entries) {
        val hud = HudAppearance(position = position, style = style, x = 24, y = 30, width = 320, fontSize = 24)
        val layout = HealthHudLayout.build(hud)
        check(hud.trackWidth > 0 && hud.trackHeight > 0 && hud.height >= hud.trackHeight)
        check(layout.count { it == '{' } == layout.count { it == '}' })
        val expectedIds = if (style == HudStyle.SEGMENTED) listOf("#TargetHud", "#SegmentTrack", "#Segment0", "#HealthText")
            else listOf("#TargetHud", "#BarTrack", "#HealthFill", "#PhantomFill", "#HealthText")
        check(expectedIds.all { layout.contains(it) })
        val commands = UICommandBuilder().appendInline(null, layout).set("#HealthText.Text", "65 / 100").commands
        check(commands.size == 2)
        check(HealthHudLayout.healthText(65f, 100f, hud.copy(format = "{current}/{max} ({percent})")) == "65/100 (65%)")
    }
    check("Right: 24, Width: 420, Bottom: 30, Height:" in HealthHudLayout.build(base.hud.copy(position = HudPosition.BOTTOM_RIGHT, x = 24, y = 30)))
    check("Left: 24, Right: -24, Top: 30, Bottom: -30" in HealthHudLayout.build(base.hud.copy(position = HudPosition.CENTER, x = 24, y = 30)))

    val directory = Files.createTempDirectory("crity-v3-test-")
    try {
        val path = directory.resolve("settings.json")
        val config = CrityConfigStore(path)
        val one = UUID.randomUUID()
        val two = UUID.randomUUID()
        val legacy = """{"$one":{"damage":"OFF","health":"DEFAULT"}}"""
        Files.writeString(path, legacy)
        val migrated = config.load()
        check(migrated.getValue(one).visual == base)
        config.save(migrated)
        check(Files.readString(directory.resolve("settings.json.v1.bak")) == legacy)
        config.save(mapOf(one to CrityState.PlayerSettings(visual = custom), two to CrityState.PlayerSettings(visual = base)))
        check(config.load().getValue(one).visual == custom && config.load().getValue(two).visual == base)
        Files.writeString(path, """{"schemaVersion":2,"defaults":{"hud":{"width":300}},"players":{"$one":{"visual":{"hud":{"color":"#aabbcc"}}}}}""")
        val inherited = config.loadDocument()
        check(inherited.defaults.hud.width == 300 && inherited.players.getValue(one).visual.hud.width == 300)
        check(inherited.players.getValue(one).visual.hud.color == "#aabbcc")
        for (bad in listOf("""{"schemaVersion":999}""", """{"schemaVersion":2,"players":{"$one":{"visual":{"hud":{"width":-1}}}}}""")) {
            Files.writeString(path, bad)
            check(runCatching { config.load() }.isFailure)
            check(Files.readString(path) == bad)
        }
    } finally {
        Files.list(directory).use { paths -> paths.forEach { Files.delete(it) } }
        Files.delete(directory)
    }
    checkReplacement()
    checkExports()
    println("PASS: custom rules, priorities, HEX validation, strict config, v1 backup/migration, player isolation, 27 HUD layouts, API rollback, export/import round-trip, diagnostics status reporting")
}

private fun checkReplacement() {
    class Registry(private val fail: String = "") : SystemRegistryAccess {
        var stock = true
        var custom = false
        override fun hasStock() = stock
        override fun hasCustom() = custom
        override fun addCustom() {
            if (fail == "before-add") error("add failed")
            custom = true
            if (fail == "after-add") error("add failed after registration")
        }
        override fun removeCustom() { custom = false }
        override fun removeStock() {
            if (fail == "before-remove") error("remove failed")
            stock = false
            if (fail == "after-remove") error("remove failed after unregister")
        }
        override fun restoreStock() { stock = true }
    }
    for (failure in listOf("before-add", "after-add", "before-remove", "after-remove")) {
        val registry = Registry(failure)
        check(runCatching { SystemReplacement(registry).install() }.isFailure)
        check(registry.stock && !registry.custom) { "Failed rollback: $failure" }
    }
    val registry = Registry()
    val replacement = SystemReplacement(registry)
    replacement.install()
    check(!registry.stock && registry.custom)
    replacement.close()
    replacement.close()
    check(registry.stock && !registry.custom)
    registry.stock = false
    check(runCatching { replacement.install() }.isFailure)
    check(!registry.stock && !registry.custom)
    val gate = FeatureGate("test expected failure")
    check(gate.status() == "test expected failure: OK")
    var calls = 0
    gate.run { calls++; throw NoSuchMethodError("intentional compatibility test") }
    gate.run { calls++ }
    check(calls == 1 && !gate.isEnabled)
    check(FeatureGate("independent").run { 42 } == 42)
    check(gate.status() == "test expected failure: DISABLED (java.lang.NoSuchMethodError: intentional compatibility test)")
}

private fun checkExports() {
    val directory = Files.createTempDirectory("crity-exports-test-")
    val original = CrityExports.directory
    CrityExports.directory = directory
    try {
        check(CrityExports.list().isEmpty())
        for (bad in listOf("", "has space", "way-too-long-way-too-long-way-too-long-way-too-long", "../escape", "dots.not.allowed")) {
            check(runCatching { CrityExports.validateName(bad) }.isFailure) { "Should reject: $bad" }
        }
        check(runCatching { CrityExports.import("missing", VisualSettings()) }.isFailure)
        val custom = VisualSettings(hud = HudAppearance(color = "#123456", style = HudStyle.SEGMENTED, segments = 6))
        CrityExports.export("preset-one", custom)
        check(CrityExports.list() == listOf("preset-one"))
        val restored = CrityExports.import("preset-one", VisualSettings())
        check(restored == custom)
        CrityExports.export("preset-two", VisualSettings())
        check(CrityExports.list() == listOf("preset-one", "preset-two"))
        Files.writeString(directory.resolve("preset-one.json"), "{not json")
        check(runCatching { CrityExports.import("preset-one", VisualSettings()) }.isFailure)
        Files.writeString(directory.resolve("preset-one.json"), """{"hud":{"width":-5}}""")
        check(runCatching { CrityExports.import("preset-one", VisualSettings()) }.isFailure)
    } finally {
        CrityExports.directory = original
        Files.list(directory).use { paths -> paths.forEach { Files.delete(it) } }
        Files.delete(directory)
    }
}
