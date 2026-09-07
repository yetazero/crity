package com.yetazero.crity.ui

import com.yetazero.crity.config.HudOrientation
import com.yetazero.crity.config.HudPosition
import com.yetazero.crity.config.HudStyle
import com.yetazero.crity.config.TargetDisplayMode
import java.math.BigDecimal
import java.math.BigInteger

internal enum class FieldKind { TEXT, NUMBER, COLOR, TOGGLE, CHOICE }
internal data class SettingsField(
    val path: String, val label: String, val kind: FieldKind,
    val min: Double = 0.0, val max: Double = 1.0, val step: Double = 1.0,
    val choices: List<String> = emptyList(), val hint: String = ""
) {
    init {
        if (kind == FieldKind.NUMBER) {
            require(listOf(min, max, step).all { it.isFinite() && BigDecimal.valueOf(it).abs() <= decimalMax }) {
                "Numeric field limits must fit the client's decimal range. Use a text field for larger numbers."
            }
            require(min <= max && step > 0) { "Invalid numeric field range or step." }
        }
    }

    fun numberValue(value: String): Double {
        require(kind == FieldKind.NUMBER) { "This field does not use a numeric control." }
        val number = value.toDouble()
        require(number.isFinite()) { "Enter a finite number." }
        return number.coerceIn(min, max)
    }

    companion object {
        private val decimalMax = BigDecimal(BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE))
    }
}

internal object SettingsFields {
    private fun text(path: String, label: String, hint: String = "") = SettingsField(path, label, FieldKind.TEXT, hint = hint)
    private fun color(path: String, label: String) = SettingsField(path, label, FieldKind.COLOR)
    private fun toggle(path: String, label: String) = SettingsField(path, label, FieldKind.TOGGLE)
    private fun number(path: String, label: String, min: Number, max: Number, step: Number = 1) =
        SettingsField(path, label, FieldKind.NUMBER, min.toDouble(), max.toDouble(), step.toDouble())
    private fun choice(path: String, label: String, choices: List<String>) = SettingsField(path, label, FieldKind.CHOICE, choices = choices)

    val modes = listOf(
        choice("damageMode", "Damage numbers", listOf("ON", "DEFAULT", "OFF")),
        choice("healthMode", "Target health", listOf("ON", "DEFAULT", "OFF")),
        toggle("debugDamage", "Damage diagnostics").copy(hint = "Logs your hits for this session. This switch resets when the server restarts.")
    )
    val damage = listOf(
        toggle("damage.showCriticalLabel", "Show critical text").copy(hint = "Applies to the critical rule. Its custom text is kept while hidden."),
        color("damage.color", "Fallback color"),
        text("damage.format", "Fallback text", "Use {amount} and {cause}. Rules take priority."),
        number("damage.decimals", "Decimal places", 0, 2),
        toggle("damage.randomAngle", "Random spread"),
        number("damage.minAngle", "Minimum angle", -180, 180),
        number("damage.maxAngle", "Maximum angle", -180, 180),
        text("damage.template", "Animation asset", "CrityCombat, CrityCombatSmall, CrityCombatRise, or your own asset ID")
    )
    val hud = listOf(
        choice("hud.position", "Screen anchor", HudPosition.entries.map { it.name }),
        number("hud.x", "Horizontal offset", -4096, 4096),
        number("hud.y", "Vertical offset", -4096, 4096),
        choice("hud.style", "Bar style", HudStyle.entries.map { it.name }),
        choice("hud.orientation", "Orientation", HudOrientation.entries.map { it.name }),
        toggle("hud.invert", "Invert fill direction").copy(hint = "Health drains toward the opposite edge (right instead of left; top instead of bottom)."),
        number("hud.width", "Length", 80, 1200),
        number("hud.barHeight", "Bar thickness", 2, 100),
        number("hud.padding", "Padding", 0, 32),
        number("hud.fontSize", "Text size", 8, 48),
        number("hud.segments", "Segment count", 2, 40).copy(hint = "Segmented style only."),
        color("hud.color", "Health color"),
        color("hud.trailColor", "Damage trail color"),
        color("hud.backgroundColor", "Panel color"),
        color("hud.trackColor", "Empty bar color"),
        color("hud.textColor", "Text color"),
        number("hud.opacity", "Panel opacity", 0, 1, 0.01),
        toggle("hud.showText", "Show health text"),
        toggle("hud.showTrail", "Show damage trail"),
        text("hud.format", "Health text", "Use {current}, {max} and {percent}."),
        number("hud.durationMs", "Visible time (ms)", 500, 60000, 100),
        number("hud.trailDelayMs", "Trail delay (ms)", 0, 10000, 50),
        number("hud.trailSmoothing", "Trail speed", 0.01, 1, 0.01)
    )
    val highlight = listOf(
        toggle("hitboxes.enabled", "Show target highlight"),
        choice("hitboxes.mode", "Display style", TargetDisplayMode.entries.map { it.name }),
        color("hitboxes.color", "Highlight color"),
        number("hitboxes.opacity", "Brightness / opacity", 0.1, 1, 0.01),
        number("hitboxes.thickness", "Glow thickness", 0.05, 2, 0.05),
        number("hitboxes.range", "Bounds distance", 4, 128),
        number("hitboxes.maxEntities", "Maximum bounds", 1, 512),
        toggle("hitboxes.showSelf", "Include own bounds")
    )
    val rule = listOf(
        toggle("enabled", "Rule enabled"),
        color("color", "Color"),
        text("format", "Custom text", "Use {amount} and {cause}; {amount} alone hides the label."),
        number("minPercent", "Minimum damage ratio", 0, 1, 0.01),
        text("minAmount", "Minimum damage", "Leave empty for no minimum. Scientific notation is supported."),
        text("maxAmount", "Maximum damage", "Leave empty for no upper limit. Scientific notation is supported."),
        text("cause", "Damage cause", "Leave empty to match every cause."),
        text("weaponPrefix", "Weapon ID prefix", "Leave empty to match every weapon.")
    )
    val all = modes + damage + hud + highlight
    val byPath = all.associateBy { it.path }
}
