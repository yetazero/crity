package com.yetazero.crity.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object VisualSettingsCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(settings: VisualSettings): JsonObject = gson.toJsonTree(settings).asJsonObject

    fun decode(json: JsonObject, defaults: VisualSettings = VisualSettings()): VisualSettings {
        val merged = merge(encode(defaults), json, "visual")
        return try {
            val d = merged.getAsJsonObject("damage")
            val h = merged.getAsJsonObject("hud")
            val b = merged.getAsJsonObject("hitboxes")
            val rules = d.getAsJsonArray("rules").map { value ->
                val r = value.asJsonObject
                DamageRule(r.string("id"), r.string("color"), r.float("minPercent"),
                    r.float("minAmount"), r.float("maxAmount"), r.string("cause"),
                    r.string("weaponPrefix"), r.string("format"), r.bool("enabled"))
            }
            VisualSettings(
                DamageAppearance(d.string("color"), d.string("format"), d.int("decimals"),
                    d.bool("randomAngle"), d.float("minAngle"), d.float("maxAngle"), rules, d.string("template"), d.bool("showCriticalLabel")),
                HudAppearance(
                    position = HudPosition.valueOf(h.string("position")), x = h.int("x"), y = h.int("y"),
                    style = HudStyle.valueOf(h.string("style")), orientation = HudOrientation.valueOf(h.string("orientation")),
                    invert = h.bool("invert"), width = h.int("width"),
                    barHeight = h.int("barHeight"), padding = h.int("padding"), fontSize = h.int("fontSize"),
                    segments = h.int("segments"),
                    color = h.string("color"), trailColor = h.string("trailColor"),
                    backgroundColor = h.string("backgroundColor"), trackColor = h.string("trackColor"),
                    textColor = h.string("textColor"), opacity = h.float("opacity"),
                    showText = h.bool("showText"), showTrail = h.bool("showTrail"), format = h.string("format"),
                    durationMs = h.int("durationMs"), trailDelayMs = h.int("trailDelayMs"), trailSmoothing = h.float("trailSmoothing")
                ),
                HitboxAppearance(b.bool("enabled"), b.string("color"), b.float("opacity"),
                    b.int("range"), b.int("maxEntities"), b.bool("showSelf"),
                    TargetDisplayMode.valueOf(b.string("mode")), b.float("thickness"))
            ).also { it.validate() }
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Invalid visual settings: ${e.message}", e)
        }
    }

    private fun JsonObject.string(key: String) = get(key).asString
    private fun JsonObject.int(key: String) = get(key).asInt
    private fun JsonObject.float(key: String) = get(key).asFloat
    private fun JsonObject.bool(key: String) = get(key).asBoolean

    private fun merge(base: JsonObject, patch: JsonObject, path: String): JsonObject {
        val result = base.deepCopy()
        for ((key, value) in patch.entrySet()) {
            val expected = base[key] ?: throw IllegalArgumentException("Unknown setting: $path.$key")
            require(!value.isJsonNull) { "$path.$key cannot be null." }
            when {
                expected.isJsonObject -> {
                    require(value.isJsonObject) { "$path.$key must be an object." }
                    result.add(key, merge(expected.asJsonObject, value.asJsonObject, "$path.$key"))
                }
                expected.isJsonArray -> {
                    require(value.isJsonArray && value.asJsonArray.size() <= 64) { "$path.$key must be an array of up to 64 rules." }
                    val rules = com.google.gson.JsonArray()
                    for (rule in value.asJsonArray) {
                        require(rule.isJsonObject && rule.asJsonObject.has("id") && rule.asJsonObject.has("color")) { "Rules require id and color." }
                        rules.add(merge(gson.toJsonTree(DamageRule("rule", "#ffffff")).asJsonObject, rule.asJsonObject, "$path.rules"))
                    }
                    result.add(key, rules)
                }
                else -> {
                    require(samePrimitiveType(expected, value)) { "$path.$key has the wrong value type." }
                    if (expected.asJsonPrimitive.isNumber) {
                        val number = value.asDouble
                        require(number.isFinite()) { "$path.$key must be finite." }
                        if (key in setOf("decimals", "x", "y", "width", "barHeight", "padding", "fontSize", "durationMs", "trailDelayMs", "range", "maxEntities")) {
                            require(number == kotlin.math.floor(number) && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) { "$path.$key must be an integer." }
                        }
                    }
                    result.add(key, value.deepCopy())
                }
            }
        }
        return result
    }

    private fun samePrimitiveType(a: JsonElement, b: JsonElement): Boolean {
        if (!a.isJsonPrimitive || !b.isJsonPrimitive) return false
        return a.asJsonPrimitive.isBoolean == b.asJsonPrimitive.isBoolean &&
            a.asJsonPrimitive.isNumber == b.asJsonPrimitive.isNumber &&
            a.asJsonPrimitive.isString == b.asJsonPrimitive.isString
    }
}
