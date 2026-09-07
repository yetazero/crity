package com.yetazero.crity.ui

import com.yetazero.crity.CrityState
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.config.VisualSettingsEditor

internal data class SettingsSnapshot(
    val visual: VisualSettings,
    val damage: CrityState.DamageMode,
    val health: CrityState.HealthMode,
    val debug: Boolean
) {
    constructor(settings: CrityState.PlayerSettings) : this(settings.visual, settings.damageMode, settings.healthMode, settings.debugDamage)
}

internal class SettingsDraft(initial: SettingsSnapshot) {
    var original = initial
        private set
    var current = initial
        private set
    val dirty get() = current != original

    fun field(path: String, input: String) {
        require(path in SettingsFields.byPath) { "Unknown setting." }
        val next = when (path) {
            "damageMode" -> current.copy(damage = CrityState.DamageMode.valueOf(input))
            "healthMode" -> current.copy(health = CrityState.HealthMode.valueOf(input))
            "debugDamage" -> current.copy(debug = input.toBooleanStrict())
            else -> current.copy(visual = VisualSettingsEditor.set(current.visual, path, input))
        }
        current = next
    }

    fun rule(id: String, field: String, input: String) {
        require(SettingsFields.rule.any { it.path == field }) { "Unknown rule field." }
        val normalized = when {
            field == "minAmount" && input.isBlank() -> "0"
            field == "maxAmount" && input.isBlank() -> Float.MAX_VALUE.toString()
            field in setOf("cause", "weaponPrefix") && input.isEmpty() -> "-"
            else -> input
        }
        current = current.copy(visual = VisualSettingsEditor.rule(current.visual, id, field, normalized))
    }

    fun addRule(requestedId: String = ""): String {
        val ids = current.visual.damage.rules.map { it.id }.toSet()
        val id = requestedId.ifEmpty { (1..65).map { "custom_$it" }.first { it !in ids } }
        current = current.copy(visual = VisualSettingsEditor.addRule(current.visual, id))
        return id
    }

    fun removeRule(id: String) {
        current = current.copy(visual = VisualSettingsEditor.removeRule(current.visual, id))
    }

    fun moveRule(id: String, offset: Int) {
        val rules = current.visual.damage.rules
        val index = rules.indexOfFirst { it.id == id }
        require(index >= 0) { "Unknown rule." }
        current = current.copy(visual = VisualSettingsEditor.moveRule(current.visual, id, (index + 1 + offset).coerceIn(1, rules.size)))
    }

    fun reset(defaults: VisualSettings) {
        defaults.validate()
        current = current.copy(visual = defaults)
    }

    fun accept(snapshot: SettingsSnapshot = current) {
        original = snapshot
        current = snapshot
    }

    fun value(path: String): String = when (path) {
        "damageMode" -> current.damage.name
        "healthMode" -> current.health.name
        "debugDamage" -> current.debug.toString()
        else -> {
            val keys = path.split('.')
            VisualSettingsCodec.encode(current.visual).getAsJsonObject(keys[0])[keys[1]].asString
        }
    }

    fun ruleValue(id: String, field: String): String {
        val rule = current.visual.damage.rules.first { it.id == id }
        return when (field) {
            "enabled" -> rule.enabled.toString()
            "color" -> rule.color
            "format" -> rule.format
            "minPercent" -> rule.minPercent.toString()
            "minAmount" -> rule.minAmount.toString()
            "maxAmount" -> if (rule.maxAmount == Float.MAX_VALUE) "" else rule.maxAmount.toString()
            "cause" -> rule.cause
            "weaponPrefix" -> rule.weaponPrefix
            else -> throw IllegalArgumentException("Unknown rule field.")
        }
    }
}
