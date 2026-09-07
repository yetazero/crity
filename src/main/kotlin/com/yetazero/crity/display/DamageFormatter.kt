package com.yetazero.crity.display

import com.yetazero.crity.config.DamageAppearance
import java.util.Locale
import kotlin.math.floor
import kotlin.random.Random

data class DamageContext(val amount: Float, val percent: Float, val cause: String = "", val weapon: String = "")
data class DamageLabel(val text: String, val rgb: Int)

object DamageFormatter {
    fun format(hit: DamageContext, appearance: DamageAppearance): DamageLabel? {
        if (!hit.amount.isFinite() || hit.amount <= 0f) return null
        val percent = if (hit.percent.isFinite()) hit.percent.coerceIn(0f, 1f) else 0f
        val rule = appearance.rules.firstOrNull {
            it.enabled && percent >= it.minPercent && hit.amount >= it.minAmount && hit.amount <= it.maxAmount &&
                (it.cause.isEmpty() || it.cause.equals(hit.cause, ignoreCase = true)) &&
                (it.weaponPrefix.isEmpty() || hit.weapon.startsWith(it.weaponPrefix, ignoreCase = true))
        }
        val number = if (appearance.decimals == 0) floor(hit.amount.toDouble()).toLong().toString()
            else String.format(Locale.ROOT, "%.${appearance.decimals}f", hit.amount)
        val format = if (rule?.id == "critical" && !appearance.showCriticalLabel) "{amount}" else rule?.format ?: appearance.format
        val text = format.replace("{amount}", number).replace("{cause}", hit.cause).take(160)
        return DamageLabel(text, (rule?.color ?: appearance.color).removePrefix("#").toInt(16))
    }

    fun angle(hitAngle: Float, appearance: DamageAppearance, random: Random = Random.Default): Float {
        if (!appearance.randomAngle) return if (hitAngle.isFinite()) hitAngle else 0f
        return appearance.minAngle + random.nextFloat() * (appearance.maxAngle - appearance.minAngle)
    }
}
