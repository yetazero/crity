package com.yetazero.crity.config

import java.util.Locale

enum class HudPosition { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }
enum class HudStyle { CLASSIC, SLIM, TEXT, BRACKET, SEGMENTED }
enum class HudOrientation { HORIZONTAL, VERTICAL }
enum class TargetDisplayMode { GLOW, BOUNDS }

data class DamageRule(
    val id: String,
    val color: String,
    val minPercent: Float = 0f,
    val minAmount: Float = 0f,
    val maxAmount: Float = Float.MAX_VALUE,
    val cause: String = "",
    val weaponPrefix: String = "",
    val format: String = "{amount}",
    val enabled: Boolean = true
) {
    fun validate() {
        require(id.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Rule id must contain 1-32 lowercase letters, digits, _ or -." }
        validateColor(color)
        require(minPercent.isFinite() && minPercent in 0f..1f) { "minPercent must be between 0 and 1." }
        require(minAmount.isFinite() && maxAmount.isFinite() && minAmount >= 0f && maxAmount >= minAmount) { "Invalid damage amount range." }
        require(cause.length <= 128 && weaponPrefix.length <= 128) { "Cause/weapon filter is too long." }
        validateFormat(format, setOf("amount", "cause"))
    }
}

data class DamageAppearance(
    val color: String = "#00d2d3",
    val format: String = "{amount}",
    val decimals: Int = 0,
    val randomAngle: Boolean = true,
    val minAngle: Float = -75f,
    val maxAngle: Float = 75f,
    val rules: List<DamageRule> = listOf(
        DamageRule("critical", "#ff3838", 0.80f, format = "CRIT! {amount}"),
        DamageRule("high", "#ffd32a", 0.55f),
        DamageRule("medium", "#2ed573", 0.25f),
        DamageRule("low", "#00d2d3")
    ),
    val template: String = "CrityCombat",
    val showCriticalLabel: Boolean = true
) {
    fun validate() {
        validateColor(color)
        validateFormat(format, setOf("amount", "cause"))
        require(decimals in 0..2) { "decimals must be 0, 1 or 2." }
        require(minAngle.isFinite() && maxAngle.isFinite() && minAngle >= -180f && maxAngle <= 180f && minAngle <= maxAngle) { "Angles must be ordered and within -180..180." }
        require(rules.size <= 64 && rules.map { it.id }.toSet().size == rules.size) { "Use up to 64 rules with unique ids." }
        rules.forEach { it.validate() }
        require(template.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) { "Invalid combat text asset id." }
    }
}

data class HudAppearance(
    val position: HudPosition = HudPosition.TOP,
    val x: Int = 0,
    val y: Int = 58,
    val style: HudStyle = HudStyle.CLASSIC,
    val orientation: HudOrientation = HudOrientation.HORIZONTAL,
    val invert: Boolean = false,
    val width: Int = 420,
    val barHeight: Int = 20,
    val padding: Int = 8,
    val fontSize: Int = 13,
    val segments: Int = 10,
    val color: String = "#e74c3c",
    val trailColor: String = "#e67e22",
    val backgroundColor: String = "#000000",
    val trackColor: String = "#141414",
    val textColor: String = "#ffffff",
    val opacity: Float = 0.75f,
    val showText: Boolean = true,
    val showTrail: Boolean = true,
    val format: String = "{current} / {max}",
    val durationMs: Int = 5000,
    val trailDelayMs: Int = 450,
    val trailSmoothing: Float = 0.15f
) {
    val isVertical: Boolean get() = orientation == HudOrientation.VERTICAL
    val inset: Int get() = when (style) {
        HudStyle.CLASSIC -> padding
        HudStyle.BRACKET -> 3
        else -> 0
    }
    val trackWidth: Int get() = width - inset * 2
    val trackHeight: Int get() = if (style == HudStyle.SLIM) minOf(barHeight, 8) else barHeight

    val barLength: Int get() = trackWidth
    val barThickness: Int get() = trackHeight

    private val textOverlayAllowance: Int get() = if (style == HudStyle.CLASSIC && showText) fontSize + 4 else 0

    val stackedTextSpace: Int get() = if (showText && (style in STACKING_STYLES || (isVertical && style != HudStyle.TEXT)))
        (if (isVertical) (fontSize + 4) * 2 + 8 else fontSize + 8) else 0
    private val bracketAllowance: Int get() = if (style == HudStyle.BRACKET) 6 else 0

    val height: Int get() = when (style) {
        HudStyle.CLASSIC -> maxOf(barHeight, textOverlayAllowance) + inset * 2
        HudStyle.TEXT -> fontSize + 8
        else -> trackHeight + stackedTextSpace + bracketAllowance
    }

    private val textReadWidth: Int get() = if (showText || style == HudStyle.TEXT) fontSize * 8 else 0
    val boxWidth: Int get() = if (isVertical) maxOf(barThickness + inset * 2, textReadWidth) else width
    val boxHeight: Int get() = if (isVertical) maxOf(barLength, textOverlayAllowance) + inset * 2 + stackedTextSpace + bracketAllowance else height

    val barTop: Int get() = if (stackedTextSpace > 0) stackedTextSpace else inset

    companion object {
        private val STACKING_STYLES = setOf(HudStyle.SLIM, HudStyle.BRACKET, HudStyle.SEGMENTED)
    }

    fun validate() {
        require(x in -4096..4096 && y in -4096..4096) { "HUD offsets must be within -4096..4096." }
        require(width in 80..1200 && barHeight in 2..100 && padding in 0..32 && fontSize in 8..48) { "Invalid HUD size: width 80..1200, barHeight 2..100, padding 0..32, fontSize 8..48." }
        require(segments in 2..40) { "segments must be 2..40." }
        listOf(color, trailColor, backgroundColor, trackColor, textColor).forEach(::validateColor)
        require(opacity.isFinite() && opacity in 0f..1f) { "opacity must be between 0 and 1." }
        validateFormat(format, setOf("current", "max", "percent"))
        require(durationMs in 500..60000 && trailDelayMs in 0..10000) { "durationMs must be 500..60000; trailDelayMs 0..10000." }
        require(trailSmoothing.isFinite() && trailSmoothing in 0.01f..1f) { "trailSmoothing must be 0.01..1." }
    }
}

data class HitboxAppearance(
    val enabled: Boolean = false,
    val color: String = "#d7e3eb",
    val opacity: Float = 0.7f,
    val range: Int = 32,
    val maxEntities: Int = 128,
    val showSelf: Boolean = false,
    val mode: TargetDisplayMode = TargetDisplayMode.GLOW,
    val thickness: Float = 0.3f
) {
    fun validate() {
        validateColor(color)
        require(thickness.isFinite() && thickness in 0.05f..2f) { "Highlight thickness must be 0.05..2." }
        require(opacity.isFinite() && opacity in 0.1f..1f) { "Hitbox opacity must be 0.1..1." }
        require(range in 4..128) { "Hitbox range must be 4..128 blocks." }
        require(maxEntities in 1..512) { "Hitbox maxEntities must be 1..512." }
    }
}

data class VisualSettings(
    val damage: DamageAppearance = DamageAppearance(),
    val hud: HudAppearance = HudAppearance(),
    val hitboxes: HitboxAppearance = HitboxAppearance(),
    val reticle: ReticleAppearance = ReticleAppearance()
) {
    fun validate() {
        damage.validate()
        hud.validate()
        hitboxes.validate()
        reticle.validate()
    }
}

fun validateColor(value: String) {
    require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "Color must be #RRGGBB, for example #ff55aa." }
}

fun normalizeColor(value: String): String {
    val hex = value.removePrefix("#").lowercase(Locale.ROOT)
    val expanded = if (hex.length == 3 && hex.all { it in "0123456789abcdef" }) hex.map { "$it$it" }.joinToString("") else hex
    return "#$expanded".also(::validateColor)
}

private fun validateFormat(value: String, placeholders: Set<String>) {
    require(value.isNotBlank() && value.length <= 96 && value.none { it.isISOControl() }) { "Text must contain 1-96 printable characters." }
    val remaining = placeholders.fold(value) { text, key -> text.replace("{$key}", "") }
    require('{' !in remaining && '}' !in remaining) { "Supported placeholders: ${placeholders.joinToString { "{$it}" }}" }
}
