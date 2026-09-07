package com.yetazero.crity

import com.google.gson.JsonParser
import com.hypixel.hytale.math.shape.Box
import com.hypixel.hytale.protocol.DebugFlags
import com.hypixel.hytale.protocol.DebugShape
import com.hypixel.hytale.protocol.packets.player.DisplayDebug
import com.yetazero.crity.compat.NativeHitboxDisplay
import com.yetazero.crity.config.HitboxAppearance
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.config.VisualSettingsEditor
import com.yetazero.crity.display.NearestHitboxes
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import java.lang.foreign.MemorySegment
import java.nio.file.Files
import java.util.UUID

fun checkHitboxes() {
    val settings = HitboxAppearance(enabled = true, color = "#8040ff")
    val localBox = Box(-0.4, -0.1, -0.6, 0.8, 2.0, 0.5)
    val minBefore = Vector3d(localBox.min)
    val maxBefore = Vector3d(localBox.max)
    val position = Vector3d(100.0, 50.0, -20.0)
    val packet = checkNotNull(NativeHitboxDisplay.packet(position, localBox, settings, 1f / 30f))
    check(packet.shape == DebugShape.Cube)
    check(DebugFlags.has(packet.flags, DebugFlags.NoSolid))
    check(!DebugFlags.has(packet.flags, DebugFlags.NoWireframe))
    check(!DebugFlags.has(packet.flags, DebugFlags.Fade))
    check(packet.opacity == 0.7f && packet.time in 0.03f..0.04f)
    val transform = Matrix4f().set(checkNotNull(packet.matrix))
    val worldMin = transform.transformPosition(Vector3f(-0.5f, -0.5f, -0.5f))
    val worldMax = transform.transformPosition(Vector3f(0.5f, 0.5f, 0.5f))
    check(worldMin.distance(Vector3f(99.6f, 49.9f, -20.6f)) < 0.0001f)
    check(worldMax.distance(Vector3f(100.8f, 52f, -19.5f)) < 0.0001f)
    check(localBox.min == minBefore && localBox.max == maxBefore)
    check(position == Vector3d(100.0, 50.0, -20.0))
    val bytes = MemorySegment.ofArray(ByteArray(packet.computeSize()))
    packet.serialize(bytes, 0)
    check(DisplayDebug.toObject(bytes) == packet)
    check(packet.color.x() == 128 / 255f && packet.color.y() == 64 / 255f && packet.color.z() == 1f)
    for (dt in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY, 10f, 0.001f)) {
        check(checkNotNull(NativeHitboxDisplay.packet(position, localBox, settings, dt)).time in 0.016f..0.1f)
    }
    check(NativeHitboxDisplay.packet(position, Box(), settings, 0.05f) == null)
    check(NativeHitboxDisplay.packet(Vector3d(Double.NaN, 0.0, 0.0), localBox, settings, 0.05f) == null)
    check(NativeHitboxDisplay.packet(position, Box(1.0, 1.0, 1.0, -1.0, 2.0, 2.0), settings, 0.05f) == null)
    val nearest = NearestHitboxes<String>(3, 8)
    for ((id, distance) in listOf("far" to 65.0, "edge" to 64.0, "near" to 1.0, "middle" to 16.0,
        "closer" to 4.0, "nan" to Double.NaN, "negative" to -1.0)) nearest.offer(id, distance)
    check(nearest.values() == listOf("near", "closer", "middle"))
    val edge = NearestHitboxes<Int>(1, 8)
    edge.offer(1, 64.0)
    check(edge.values() == listOf(1))
    val disabled = NearestHitboxes<Int>(0, 8)
    disabled.offer(1, 0.0)
    check(disabled.values().isEmpty())

    val base = VisualSettings()
    check(!base.hitboxes.enabled && !base.hitboxes.showSelf)
    val oldJson = VisualSettingsCodec.encode(base).also { it.remove("hitboxes") }
    check(VisualSettingsCodec.decode(oldJson).hitboxes == HitboxAppearance())
    var custom = VisualSettingsEditor.set(base, "hitboxes.enabled", "on")
    custom = VisualSettingsEditor.set(custom, "hitboxes.range", "48")
    custom = VisualSettingsEditor.set(custom, "hitboxes.color", "8040ff")
    check(custom.hitboxes.enabled && custom.hitboxes.range == 48 && custom.hitboxes.color == "#8040ff")
    check(!base.hitboxes.enabled)
    for ((key, value) in listOf("range" to "129", "range" to "0", "range" to "10.5", "maxEntities" to "513",
        "maxEntities" to "0", "opacity" to "0", "color" to "oops", "enabled" to "maybe")) {
        check(runCatching { VisualSettingsEditor.set(base, "hitboxes.$key", value) }.isFailure)
    }
    check(runCatching { VisualSettingsCodec.decode(JsonParser.parseString("""{"hitboxes":{"enabled":null}}""").asJsonObject) }.isFailure)
    val directory = Files.createTempDirectory("crity-hitboxes-")
    try {
        val one = UUID.randomUUID()
        val two = UUID.randomUUID()
        val config = CrityConfigStore(directory.resolve("players.json"))
        config.save(mapOf(one to CrityState.PlayerSettings(visual = custom), two to CrityState.PlayerSettings()))
        val loaded = config.load()
        check(loaded.getValue(one).visual.hitboxes == custom.hitboxes)
        check(!loaded.getValue(two).visual.hitboxes.enabled)
    } finally {
        Files.list(directory).use { paths -> paths.forEach { Files.delete(it) } }
        Files.delete(directory)
    }
    println("PASS: native wireframe packet round-trip, exact bounds, short lifetime, nearest/range cap, hitbox validation and player preferences")
}
