package com.yetazero.crity.compat

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.DamageBreakdown
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.WeaponDamageDataCollector
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

internal object HytaleDamageMetadata {
    fun resolveDamagePercent(
        cmdBuf: CommandBuffer<EntityStore>,
        attackerRef: Ref<EntityStore>,
        amount: Float,
        damage: Damage
    ): Float {
        val stack: ItemStack? = InventoryComponent.getItemInHand(cmdBuf, attackerRef)
        val item: Item? = stack?.item
        if (item != null) {
            val weapon: ItemWeapon? = item.weapon
            val allEntries = ArrayList<DamageBreakdown.Entry>()

            weapon?.basicDamageBreakdown?.entries()?.let { allEntries.addAll(it) }
            weapon?.ultimateDamageBreakdown?.entries()?.let { allEntries.addAll(it) }

            if (allEntries.isEmpty()) {
                val calc = WeaponDamageDataCollector.calculate(item, InteractionType.Primary)
                allEntries.addAll(calc.entries())
            }

            var bestEntry: DamageBreakdown.Entry? = null
            var minDistance = Float.MAX_VALUE

            for (entry in allEntries) {
                val min = entry.min()
                val max = entry.max()
                if (amount >= (min - 0.5f) && amount <= (max + 0.5f)) {
                    bestEntry = entry
                    break
                }
                val center = (min + max) * 0.5f
                val dist = kotlin.math.abs(amount - center)
                if (dist < minDistance) {
                    minDistance = dist
                    bestEntry = entry
                }
            }

            if (bestEntry != null && bestEntry.max() > bestEntry.min()) {
                return ((amount - bestEntry.min()) / (bestEntry.max() - bestEntry.min())).coerceIn(0f, 1f)
            }
        }

        val initial = damage.initialAmount.coerceAtLeast(1.0f)
        return if (amount >= initial * 1.15f) 0.9f else 0.5f
    }

}
