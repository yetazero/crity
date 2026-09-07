package com.yetazero.crity

import com.hypixel.hytale.protocol.CombatTextUpdate
import com.hypixel.hytale.protocol.UIComponentsUpdate
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityUpdate
import java.lang.foreign.MemorySegment
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random

fun main() {
    val original = intArrayOf(1, 2, 3, 10, 20, 30, 30)
    for (damageMode in CrityState.DamageMode.entries) {
        for (healthMode in CrityState.HealthMode.entries) {
            val template = when (damageMode) {
                CrityState.DamageMode.ON -> 4
                CrityState.DamageMode.DEFAULT -> 1
                CrityState.DamageMode.OFF -> null
            }
            val selected = CrityCombatDisplay.components(original, { it in 1..4 }, template, 10, healthMode)
            check(selected.count { it in 1..4 } == if (template == null) 0 else 1)
            check((10 in selected) == (healthMode == CrityState.HealthMode.DEFAULT))
            check(20 in selected && 30 in selected && selected.size == selected.toSet().size)
            check(original.contentEquals(intArrayOf(1, 2, 3, 10, 20, 30, 30)))
        }
    }
    check(CrityCombatDisplay.components(intArrayOf(20), { false }, null, -1,
        CrityState.HealthMode.DEFAULT).contentEquals(intArrayOf(20)))

    val tiers = listOf(0f to 0x00d2d3, 0.25f to 0x2ed573, 0.55f to 0xffd32a, 0.80f to 0xff3838)
    for ((percent, rgb) in tiers) {
        val packet = checkNotNull(CrityCombatDisplay.text(CrityState.DamageMode.ON, 42.9f, percent, -25f))
        val color = checkNotNull(packet.color)
        val actual = ((color.red.toInt() and 255) shl 16) or
            ((color.green.toInt() and 255) shl 8) or (color.blue.toInt() and 255)
        check(actual == rgb)
        check(packet.text == if (percent >= 0.8f) "CRIT! 42" else "42")
        check(packet.hitAngleDeg == -25f)
        val bytes = MemorySegment.ofArray(ByteArray(packet.computeSize()))
        packet.serialize(bytes, 0)
        check(CombatTextUpdate.toObject(bytes) == packet)
    }
    check(CrityCombatDisplay.text(CrityState.DamageMode.OFF, 42f, 1f, 0f) == null)
    for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
        check(CrityCombatDisplay.text(CrityState.DamageMode.ON, invalid, 1f, 0f) == null)
    }
    val default = checkNotNull(CrityCombatDisplay.text(CrityState.DamageMode.DEFAULT, 42f, 1f, Float.NaN))
    check(default.text == "42" && default.hitAngleDeg == 0f)
    val defaultColor = checkNotNull(default.color)
    check(defaultColor.red == (-1).toByte() && defaultColor.green == (-1).toByte() && defaultColor.blue == (-1).toByte())
    val queue = EntityUpdate()
    for (percent in listOf(0.25f, 0.25f, 0.9f)) {
        queue.queueUpdate(UIComponentsUpdate(intArrayOf(4, 20)))
        queue.queueUpdate(checkNotNull(CrityCombatDisplay.text(CrityState.DamageMode.ON, 42f, percent, 0f)))
    }
    val updates = checkNotNull(queue.toUpdatesArray())
    check(updates.size == 6)
    check(updates.filterIsInstance<CombatTextUpdate>().map { it.text } == listOf("42", "42", "CRIT! 42"))
    check(updates.withIndex().all { (index, update) -> if (index % 2 == 0) update is UIComponentsUpdate else update is CombatTextUpdate })
    val random = Random(298)
    val angles = List(4096) { CrityCombatDisplay.angle(CrityState.DamageMode.ON, 0f, random) }
    check(angles.all { it >= -75f && it < 75f })
    val quadrants = angles.groupingBy { ((it + 75f) / 37.5f).toInt() }.eachCount()
    check(quadrants.size == 4 && quadrants.values.all { it in 800..1250 })
    check(CrityCombatDisplay.angle(CrityState.DamageMode.DEFAULT, 37f, Random(0)) == 37f)
    check(CrityCombatDisplay.angle(CrityState.DamageMode.ON, 0f, Random(1)) ==
        CrityCombatDisplay.angle(CrityState.DamageMode.ON, 270f, Random(1)))
    val templatePath = java.nio.file.Path.of("src/main/resources/Server/Entity/UI/CrityCombat.json")
    val template = Files.newBufferedReader(templatePath).use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
    val x = template["RandomPositionOffsetRange"].asJsonObject["X"].asJsonObject
    check(x["Min"].asFloat >= 0f && x["Max"].asFloat >= x["Min"].asFloat)

    val directory = Files.createTempDirectory("crity-config-test-")
    try {
        val path = directory.resolve("settings.json")
        val config = CrityConfigStore(path)
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        check(config.load().isEmpty())
        for (json in listOf(
            """{"$uuid":{"damage":"OFF","health":"DEFAULT"}}""",
            """{
                "$uuid": {
                    "damage": "OFF",
                    "health": "DEFAULT"
                }
            }"""
        )) {
            Files.writeString(path, json)
            check(config.load()[uuid] == CrityState.PlayerSettings(CrityState.DamageMode.OFF, CrityState.HealthMode.DEFAULT))
        }
        val settings = CrityState.PlayerSettings(CrityState.DamageMode.DEFAULT, CrityState.HealthMode.OFF, true)
        config.save(mapOf(uuid to settings))
        check(config.load()[uuid] == settings.copy(debugDamage = false))
        check(!Files.readString(path).contains("debug"))
        Files.writeString(path, """{"bad-uuid":{},"$uuid":{"damage":"invalid","health":"OFF"}}""")
        check(config.load()[uuid] == CrityState.PlayerSettings(CrityState.DamageMode.ON, CrityState.HealthMode.OFF))
        Files.writeString(path, "{broken")
        check(runCatching { config.load() }.isFailure)
        check(Files.readString(path) == "{broken")
        Files.list(directory).use { check(it.count() == 2L) }
    } finally {
        Files.deleteIfExists(directory.resolve("settings.json"))
        Files.deleteIfExists(directory.resolve("settings.json.v1.bak"))
        Files.delete(directory)
    }
    println("PASS: display modes, protocol, queue, 4096 random angles, signed protocol angles, config parsing and atomic replacement")
    checkAppearance()
    checkHitboxes()
    checkTargetHighlight()
    checkSettingsPanel()
}
