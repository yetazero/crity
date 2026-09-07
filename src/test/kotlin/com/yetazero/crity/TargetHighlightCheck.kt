package com.yetazero.crity

import com.google.gson.JsonParser
import com.hypixel.hytale.protocol.EffectOp
import com.hypixel.hytale.protocol.EntityEffectsUpdate
import com.hypixel.hytale.protocol.ModelVFX
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.yetazero.crity.compat.NativeTargetHighlight
import com.yetazero.crity.compat.ServerBuild
import com.yetazero.crity.compat.ServerCompatibility
import com.yetazero.crity.config.HitboxAppearance
import com.yetazero.crity.config.TargetDisplayMode
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.config.VisualSettingsEditor
import com.yetazero.crity.display.TargetLease
import java.lang.foreign.MemorySegment

fun checkTargetHighlight() {
    val supported = ServerCompatibility.read(JavaPlugin::class.java)
    check(!supported.version.isNullOrBlank() && !supported.revision.isNullOrBlank())
    for (unsupported in listOf(supported.copy(version = "0.7.1"), supported.copy(revision = "hotfix"),
        supported.copy(revision = null), ServerBuild(null, null))) {
        check(ServerCompatibility.describe(unsupported).contains("version numbers do not disable Crity"))
    }
    val lease = TargetLease("target A", 1_000_000_000L)
    check(lease.remainingSeconds(2_000_000_000L, 5000) == 4f)
    lease.refresh(3_000_000_000L)
    check(lease.remainingSeconds(6_000_000_000L, 5000) == 2f)
    check(lease.remainingSeconds(8_000_000_000L, 5000) == 0f)
    check(lease.remainingSeconds(8_000_000_000L, 8000) == 3f)
    check(lease.remainingSeconds(12_000_000_000L, 5000) == 0f)
    for (remove in listOf(false, true)) {
        val packet = NativeTargetHighlight.effect(123, 8f, remove)
        val operation = packet.entityEffectUpdates.single()
        check(operation.id == 123 && !operation.infinite && !operation.debuff && operation.statusEffectIcon == null)
        check(operation.type == if (remove) EffectOp.Remove else EffectOp.Add)
        check(operation.remainingTime == if (remove) 0f else 8f)
        val bytes = MemorySegment.ofArray(ByteArray(packet.computeSize()))
        packet.serialize(bytes, 0)
        check(EntityEffectsUpdate.toObject(bytes) == packet)
    }
    val original = ModelVFX().also { it.id = NativeTargetHighlight.ASSET_ID; it.opacity = 1f }
    val customized = NativeTargetHighlight.customize(original, HitboxAppearance(color = "#80ffff", opacity = 0.5f, thickness = 0.6f))
    check(customized !== original && original.highlightColor == null && original.highlightThickness == 0f)
    check(customized.opacity == 1f && customized.highlightThickness == 0.6f)
    check(customized.highlightColor!!.red == 64.toByte() && customized.highlightColor!!.blue == 127.toByte())
    val old = JsonParser.parseString("""{"hitboxes":{"enabled":true,"color":"#a6daff","opacity":0.64}}""").asJsonObject
    val migrated = VisualSettingsCodec.decode(old)
    check(migrated.hitboxes.enabled && migrated.hitboxes.mode == TargetDisplayMode.GLOW && migrated.hitboxes.color == "#a6daff")
    check(VisualSettingsEditor.set(migrated, "hitboxes.mode", "bounds").hitboxes.mode == TargetDisplayMode.BOUNDS)
    for ((key, value) in listOf("mode" to "invalid", "thickness" to "0", "thickness" to "2.1")) {
        check(runCatching { VisualSettingsEditor.set(migrated, "hitboxes.$key", value) }.isFailure)
    }
    val effect = checkNotNull(object {}.javaClass.getResourceAsStream("/Server/Entity/Effects/CrityTargetHighlight.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    check(effect.keySet() == setOf("Duration", "OverlapBehavior", "ApplicationEffects"))
    check(effect.getAsJsonObject("ApplicationEffects").keySet() == setOf("ModelVFXId"))
    println("PASS: informational build detection, highlight deadline/refresh, additive finite effect packets, isolated color customization and old-config migration")
}
