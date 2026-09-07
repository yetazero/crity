package com.yetazero.crity

import com.hypixel.hytale.protocol.Color
import com.hypixel.hytale.protocol.CombatTextUpdate
import com.yetazero.crity.config.DamageAppearance
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.display.DamageFormatter
import kotlin.random.Random

internal object CrityCombatDisplay {
    private val white = Color(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
    fun angle(mode: CrityState.DamageMode, hitAngle: Float, random: Random = Random.Default, appearance: DamageAppearance = DamageAppearance()): Float =
        if (mode == CrityState.DamageMode.ON) DamageFormatter.angle(hitAngle, appearance, random) else hitAngle

    fun text(mode: CrityState.DamageMode, amount: Float, percent: Float, angle: Float,
             appearance: DamageAppearance = DamageAppearance(), cause: String = "", weapon: String = "",
             defaultColor: Color? = white): CombatTextUpdate? {
        if (mode == CrityState.DamageMode.OFF || !amount.isFinite() || amount <= 0f) return null
        val custom = mode == CrityState.DamageMode.ON
        val label = if (custom) DamageFormatter.format(DamageContext(amount, percent, cause, weapon), appearance) else null
        val color = label?.rgb?.let { Color((it shr 16).toByte(), (it shr 8).toByte(), it.toByte()) } ?: defaultColor
        val safeAngle = if (angle.isFinite()) angle else 0f
        return CombatTextUpdate(safeAngle, label?.text ?: amount.toInt().toString(), color)
    }

    fun components(
        original: IntArray,
        isCombatText: (Int) -> Boolean,
        selectedText: Int?,
        healthBar: Int,
        healthMode: CrityState.HealthMode
    ): IntArray {
        val result = original.filterNot(isCombatText).toMutableSet()
        if (healthBar >= 0) {
            if (healthMode == CrityState.HealthMode.DEFAULT) result.add(healthBar)
            else result.remove(healthBar)
        }
        selectedText?.takeIf { it >= 0 }?.let(result::add)
        return result.toIntArray()
    }
}
