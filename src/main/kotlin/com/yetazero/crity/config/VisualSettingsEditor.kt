package com.yetazero.crity.config

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Locale

object VisualSettingsEditor {
    fun set(settings: VisualSettings, path: String, input: String): VisualSettings {
        val parts = path.split('.')
        require(parts.size == 2 && parts[0] in setOf("damage", "hud", "hitboxes")) { "Use damage.<setting>, hud.<setting> or hitboxes.<setting>." }
        val root = VisualSettingsCodec.encode(settings)
        setField(root.getAsJsonObject(parts[0]), parts[1], input)
        return VisualSettingsCodec.decode(root)
    }

    fun rule(settings: VisualSettings, id: String, field: String, input: String): VisualSettings {
        val rules = settings.damage.rules.toMutableList()
        val index = rules.indexOfFirst { it.id == id }
        require(index >= 0) { "Unknown rule: $id. Add it with /crity rule-add $id." }
        require(field != "id") { "Rule ids cannot be edited." }
        val root = VisualSettingsCodec.encode(settings)
        setField(root.getAsJsonObject("damage").getAsJsonArray("rules")[index].asJsonObject, field, input)
        return VisualSettingsCodec.decode(root)
    }

    fun addRule(settings: VisualSettings, id: String): VisualSettings {
        require(settings.damage.rules.none { it.id == id }) { "Rule already exists: $id." }
        return settings.copy(damage = settings.damage.copy(rules = listOf(DamageRule(id, settings.damage.color)) + settings.damage.rules)).also { it.validate() }
    }

    fun removeRule(settings: VisualSettings, id: String): VisualSettings {
        require(settings.damage.rules.any { it.id == id }) { "Unknown rule: $id." }
        return settings.copy(damage = settings.damage.copy(rules = settings.damage.rules.filterNot { it.id == id }))
    }

    fun moveRule(settings: VisualSettings, id: String, position: Int): VisualSettings {
        val rules = settings.damage.rules.toMutableList()
        val selected = rules.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown rule: $id.")
        require(position in 1..rules.size) { "Rule position must be 1..${rules.size}." }
        rules.remove(selected)
        rules.add(position - 1, selected)
        return settings.copy(damage = settings.damage.copy(rules = rules))
    }

    private fun setField(target: JsonObject, key: String, input: String) {
        val old = target[key] ?: throw IllegalArgumentException("Unknown setting: $key.")
        require(old.isJsonPrimitive) { "Use /crity rule to edit individual rules." }
        val primitive = old.asJsonPrimitive
        val value = when {
            primitive.isBoolean -> JsonPrimitive(when (input.lowercase(Locale.ROOT)) {
                "true", "on" -> true
                "false", "off" -> false
                else -> throw IllegalArgumentException("Use true/false or on/off.")
            })
            primitive.isNumber -> JsonPrimitive(input.toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a finite number."))
            key.endsWith("color", ignoreCase = true) -> JsonPrimitive(normalizeColor(input))
            key in setOf("position", "style", "mode", "orientation") -> JsonPrimitive(input.uppercase(Locale.ROOT).replace('-', '_'))
            else -> JsonPrimitive(if (input == "-") "" else input)
        }
        target.add(key, value)
    }
}
