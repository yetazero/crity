package com.yetazero.crity

import com.hypixel.hytale.protocol.Color
import com.hypixel.hytale.protocol.CombatTextUpdate
import kotlin.random.Random

internal object CrityCombatDisplay {
    private val white = Color(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
    private val cyan = Color(0x00, 0xd2.toByte(), 0xd3.toByte())
    private val green = Color(0x2e, 0xd5.toByte(), 0x73)
    private val yellow = Color(0xff.toByte(), 0xd3.toByte(), 0x2a)
    private val red = Color(0xff.toByte(), 0x38, 0x38)

    fun angle(mode: CrityState.DamageMode, hitAngle: Float, random: Random = Random.Default): Float =
        if (mode == CrityState.DamageMode.ON) random.nextFloat() * 150f - 75f else hitAngle

    fun text(mode: CrityState.DamageMode, amount: Float, percent: Float, angle: Float): CombatTextUpdate? {
        if (mode == CrityState.DamageMode.OFF || !amount.isFinite() || amount <= 0f) return null
        val custom = mode == CrityState.DamageMode.ON
        val critical = custom && percent >= 0.80f
        val color = when {
            !custom -> white
            critical -> red
            percent >= 0.55f -> yellow
            percent >= 0.25f -> green
            else -> cyan
        }
        val number = amount.toInt().toString()
        val safeAngle = if (angle.isFinite()) angle else 0f
        return CombatTextUpdate(safeAngle, if (critical) "CRIT! $number" else number, color)
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
