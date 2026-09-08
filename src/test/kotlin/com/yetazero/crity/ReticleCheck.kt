package com.yetazero.crity

import com.google.gson.JsonParser
import com.yetazero.crity.config.*
import com.yetazero.crity.display.ReticleLayout
import com.yetazero.crity.ui.*
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

fun checkReticle() {
    val defaults = VisualSettings()
    val old = JsonParser.parseString("""{"reticle":{"enabled":true,"shape":"CROSS","rangedShape":"RING","centerColor":"#123456","rangedColor":"#abcdef","showForecast":true,"length":64}}""").asJsonObject
    val migrated = VisualSettingsCodec.decode(old)
    check(migrated.reticle.enabled && migrated.reticle.melee.color == "#123456" && migrated.reticle.ranged.color == "#abcdef")
    check(migrated.reticle.ranged.shape == ReticleShape.RING)
    check(!VisualSettingsCodec.encode(migrated).getAsJsonObject("reticle").has("showForecast"))
    check(VisualSettingsCodec.decode(VisualSettingsCodec.encode(migrated)) == migrated)
    check(VisualSettingsCodec.decode(JsonParser.parseString("{}").asJsonObject) == defaults)
    val edited = VisualSettingsEditor.set(defaults, "reticle.ranged.color", "abc")
    check(edited.reticle.ranged.color == "#aabbcc" && edited.reticle.melee == defaults.reticle.melee)
    check(VisualSettingsCodec.decode(VisualSettingsCodec.encode(edited)) == edited)
    for (path in listOf("size", "thickness", "centerSize", "outlineWidth", "rotation", "stretchX", "offsetY")) {
        check(runCatching { VisualSettingsEditor.set(defaults, "reticle.melee.$path", "3.5") }.isFailure)
        check(runCatching { VisualSettingsEditor.set(defaults, "reticle.melee.$path", "1000000") }.isFailure)
    }
    for (value in listOf("NaN", "Infinity", "-1", "2")) check(runCatching { VisualSettingsEditor.set(defaults, "reticle.ranged.opacity", value) }.isFailure)
    check(runCatching { VisualSettingsEditor.set(defaults, "reticle.ranged.shape", "UNKNOWN") }.isFailure)
    check(runCatching { VisualSettingsEditor.set(defaults, "reticle.mode", "UNKNOWN") }.isFailure)
    check(runCatching { VisualSettingsEditor.set(defaults, "reticle.ranged.centerShape", "UNKNOWN") }.isFailure)
    val settings = defaults.reticle
    val faint = ReticleLayout.build(settings.melee.copy(showCenter = false, outlineWidth = 0, opacity = 0.01f))
    check(faint.count { it == '{' } > 1) { "Low-opacity reticle must remain visible" }
    val transparent = ReticleLayout.build(settings.melee.copy(showCenter = false, opacity = 0f))
    check(transparent.count { it == '{' } == 1)
    check(settings.profile(false) == settings.melee && settings.profile(true) == settings.ranged)
    check(settings.copy(mode = ReticleMode.MELEE).profile(true) == settings.melee)
    check(settings.copy(mode = ReticleMode.RANGED).profile(false) == settings.ranged)
    var maximumNodes = 0
    val output = System.getenv("CRITY_RETICLE_PREVIEW_DIR")?.let { Files.createDirectories(Path.of(it)) }
    for (shape in ReticleShape.entries) {
        for (extreme in listOf(false, true)) {
            val p = if (extreme) ReticleProfile(shape = shape, size = 40, gap = 24, armLength = 32, thickness = 8,
                outlineWidth = 4, rotation = 37, stretchX = 150, stretchY = 150, offsetX = 24, offsetY = -24)
                else ReticleProfile(shape = shape)
            val image = ReticleLayout.raster(p)
            check((0 until 256).all { image.getRGB(it, 0) == 0 && image.getRGB(it, 255) == 0 && image.getRGB(0, it) == 0 && image.getRGB(255, it) == 0 }) { "Clipped $shape" }
            val layout = ReticleLayout.build(p)
            maximumNodes = maxOf(maximumNodes, layout.count { it == '{' })
            check(layout.length < 350_000) { "Excessive UI geometry for $shape: ${layout.length}" }
            check(layout == ReticleLayout.build(p))
            if (output != null && !extreme) {
                ImageIO.write(ReticleLayout.raster(p.copy(size = 22, armLength = 14, centerSize = 4)), "png", output.resolve("$shape.png").toFile())
                Files.writeString(output.resolve("$shape.ui"), layout)
            }
        }
    }
    for (shape in listOf(ReticleShape.RING, ReticleShape.DIAMOND, ReticleShape.CHEVRON, ReticleShape.TRIANGLE, ReticleShape.HEXAGON, ReticleShape.STAR)) {
        val image = ReticleLayout.raster(ReticleProfile(shape = shape, showCenter = false, outlineWidth = 0))
        val pixels = (0 until 256 * 256).filter { image.getRGB(it % 256, it / 256) ushr 24 > 127 }.toMutableSet()
        val pending = ArrayDeque<Int>()
        pending.add(pixels.first().also { pixels.remove(it) })
        while (pending.isNotEmpty()) {
            val at = pending.removeFirst()
            for (dx in -1..1) for (dy in -1..1) {
                val next = at + dx + dy * 256
                if (pixels.remove(next)) pending.add(next)
            }
        }
        check(pixels.isEmpty()) { "$shape is disconnected" }
    }
    val draft = SettingsDraft(SettingsSnapshot(CrityState.PlayerSettings()))
    val initial = UICommandBuilder()
    val sample = com.yetazero.crity.display.DamageContext(42f, 0.9f)
    val preview = SettingsPanelRenderer.preview(draft, sample, false, initial, reticle = true)
    check(initial.commands.any { it.selector == "#MeleeReticlePreview" } && initial.commands.any { it.selector == "#RangedReticlePreview" })
    check(initial.commands.none { it.selector.orEmpty().contains("Portrait") || it.selector.orEmpty().contains("SampleDamage") })
    draft.field("reticle.ranged.color", "00ff00")
    val update = UICommandBuilder()
    val next = SettingsPanelRenderer.preview(draft, sample, false, update, preview, true)
    check(update.commands.none { it.selector == "#MeleeReticlePreview" })
    check(update.commands.any { it.selector == "#RangedReticlePreview" })
    val idle = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, false, idle, next, true)
    check(idle.commands.none { it.selector.orEmpty().endsWith("ReticlePreview") })
    val back = UICommandBuilder()
    val backState = SettingsPanelRenderer.preview(draft, sample, false, back, next, false)
    check(back.commands.any { it.selector == "#MiniHud" && it.text.orEmpty().contains("HealthFill") })
    val returnToReticles = UICommandBuilder()
    SettingsPanelRenderer.preview(draft, sample, false, returnToReticles, backState, true)
    for (selector in listOf("#MeleeReticlePreview", "#RangedReticlePreview")) {
        check(returnToReticles.commands.any { it.selector == selector && it.text.orEmpty().contains("Background:") })
    }
    val dot = ReticleLayout.raster(ReticleProfile(shape = ReticleShape.DOT, size = 12))
    for (x in 126..129) for (y in 126..129) check(dot.getRGB(x, y) and 0xffffff == 0xf2e8d5)
    for (size in listOf(4, 12, 40)) for (rotation in listOf(0, 45, 90, 180, 270)) {
        val chevron = ReticleLayout.raster(ReticleProfile(shape = ReticleShape.CHEVRON, size = size, rotation = rotation,
            showCenter = false, outlineWidth = 0, offsetX = 7, offsetY = -3))
        check((134..135).any { x -> (124..125).any { y -> chevron.getRGB(x, y) ushr 24 > 127 } }) {
            "Chevron tip must stay at the aim point for size $size and rotation $rotation"
        }
        if (rotation == 0) check((0 until 123).all { y -> (0 until 256).all { x -> chevron.getRGB(x, y) == 0 } })
    }
    println("PASS: independent reticle profiles, migration, all 16 continuous shapes, bounds, profile switching and independent previews; maximum $maximumNodes native geometry groups")
}
