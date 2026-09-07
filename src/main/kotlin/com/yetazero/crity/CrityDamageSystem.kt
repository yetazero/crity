package com.yetazero.crity

import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.protocol.CombatTextUpdate
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.UIComponentsUpdate
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.DamageBreakdown
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.WeaponDamageDataCollector
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.EntityModule
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.modules.entityui.EntityUIModule
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.logging.Level
import java.util.logging.Logger

class CrityDamageSystem : DamageEventSystem() {

    companion object {
        private val LOGGER = Logger.getLogger("Crity")

        var healthBarIdx = -1; private set
        var defaultCombatTextIdx = -1; private set
        var cyanIdx = -1; private set
        var greenIdx = -1; private set
        var yellowIdx = -1; private set
        var redIdx = -1; private set

        fun ensureIndicesLoaded(): Boolean {
            if (cyanIdx >= 0 && greenIdx >= 0 && yellowIdx >= 0 && redIdx >= 0) return true
            return try {
                val map = EntityUIComponent.getAssetMap() ?: return false
                healthBarIdx = map.getIndex(CrityState.UI_HEALTHBAR)
                defaultCombatTextIdx = map.getIndex(CrityState.UI_COMBAT_TEXT)
                cyanIdx = map.getIndex(CrityState.UI_CYAN)
                greenIdx = map.getIndex(CrityState.UI_GREEN)
                yellowIdx = map.getIndex(CrityState.UI_YELLOW)
                redIdx = map.getIndex(CrityState.UI_RED)
                (cyanIdx >= 0 && greenIdx >= 0 && yellowIdx >= 0 && redIdx >= 0)
            } catch (t: Throwable) {
                LOGGER.log(Level.FINE, "Crity: asset map not fully initialized yet", t)
                false
            }
        }
    }

    private val query: Query<EntityStore> = Query.and(
        EntityModule.get().visibleComponentType,
        EntityUIModule.get().uiComponentListType
    )

    override fun getGroup(): SystemGroup<EntityStore> = DamageModule.get().inspectDamageGroup

    override fun getQuery(): Query<EntityStore> = query

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        cmdBuf: CommandBuffer<EntityStore>,
        damage: Damage
    ) {
        val amount = damage.amount
        if (amount <= 0f) return

        val source = damage.source as? Damage.EntitySource ?: return
        val attackerRef = source.ref
        if (!attackerRef.isValid) return

        val playerRef = cmdBuf.getComponent(attackerRef, PlayerRef.getComponentType()) ?: return
        if (!playerRef.isValid) return

        val player = cmdBuf.getComponent(attackerRef, Player.getComponentType())
        val viewer = cmdBuf.getComponent(attackerRef, EntityViewer.getComponentType()) ?: return

        val settings = CrityState.getSettings(playerRef.uuid)
        val targetRef = chunk.getReferenceTo(index)

        val angleVal = damage.getIfPresentMetaObject(Damage.HIT_ANGLE) ?: 0f
        val damageText = amount.toInt().toString()

        ensureIndicesLoaded()

        val activeUiSet = HashSet<Int>()
        try {
            val targetUiList = cmdBuf.getComponent(targetRef, UIComponentList.getComponentType())
            targetUiList?.componentIds?.let { activeUiSet.addAll(it.toList()) }
        } catch (t: Throwable) {
            LOGGER.log(Level.WARNING, "Crity: failed to read target UI list", t)
        }

        listOf(defaultCombatTextIdx, cyanIdx, greenIdx, yellowIdx, redIdx)
            .filter { it >= 0 }
            .forEach { activeUiSet.remove(it) }

        if (healthBarIdx >= 0) {
            when (settings.healthMode) {
                CrityState.HealthMode.OFF, CrityState.HealthMode.ON -> activeUiSet.remove(healthBarIdx)
                CrityState.HealthMode.DEFAULT -> activeUiSet.add(healthBarIdx)
            }
        }

        when (settings.damageMode) {
            CrityState.DamageMode.DEFAULT -> {
                if (defaultCombatTextIdx >= 0) activeUiSet.add(defaultCombatTextIdx)
                viewer.queueUpdate(targetRef, UIComponentsUpdate(activeUiSet.toIntArray()))
                viewer.queueUpdate(targetRef, CombatTextUpdate(angleVal, damageText))
            }

            CrityState.DamageMode.ON -> {
                val pct = resolveDamagePercent(cmdBuf, attackerRef, amount, damage)
                val isCrit = pct >= 0.80f
                val chosenColorIdx = when {
                    isCrit -> redIdx
                    pct >= 0.55f -> yellowIdx
                    pct >= 0.25f -> greenIdx
                    else -> cyanIdx
                }
                if (chosenColorIdx >= 0) activeUiSet.add(chosenColorIdx)

                viewer.queueUpdate(targetRef, UIComponentsUpdate(activeUiSet.toIntArray()))
                viewer.queueUpdate(targetRef, CombatTextUpdate(angleVal, damageText))
                if (isCrit) {
                    viewer.queueUpdate(targetRef, CombatTextUpdate(angleVal + 180f, "CRIT!"))
                }
            }

            CrityState.DamageMode.OFF -> {
                if (activeUiSet.isNotEmpty()) {
                    viewer.queueUpdate(targetRef, UIComponentsUpdate(activeUiSet.toIntArray()))
                }
            }
        }

        if (settings.healthMode == CrityState.HealthMode.ON && player != null) {
            updateTargetHealthHud(cmdBuf, targetRef, playerRef, player, amount)
        }
    }

    private fun resolveDamagePercent(
        cmdBuf: CommandBuffer<EntityStore>,
        attackerRef: Ref<EntityStore>,
        amount: Float,
        damage: Damage
    ): Float {
        try {
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
        } catch (t: Throwable) {
            LOGGER.log(Level.WARNING, "Crity: failed to resolve weapon damage breakdown", t)
        }

        val initial = damage.initialAmount.coerceAtLeast(1.0f)
        return if (amount >= initial * 1.15f) 0.9f else 0.5f
    }

    private fun updateTargetHealthHud(
        cmdBuf: CommandBuffer<EntityStore>,
        targetRef: Ref<EntityStore>,
        playerRef: PlayerRef,
        player: Player,
        damageAmount: Float
    ) {
        try {
            val stats = cmdBuf.getComponent(targetRef, EntityStatMap.getComponentType()) ?: return
            val healthIdx = DefaultEntityStatTypes.getHealth()
            val statVal = stats.get(healthIdx) ?: return
            val cur = statVal.get()
            val max = statVal.max

            val hudManager: HudManager = player.hudManager
            val hud = (hudManager.getCustomHud(CrityTargetHealthHud.KEY) as? CrityTargetHealthHud)
                ?: CrityTargetHealthHud(playerRef, player).also { hudManager.addCustomHud(playerRef, it) }
            hud.updateHealth(targetRef, cur, max, damageAmount)
        } catch (t: Throwable) {
            LOGGER.log(Level.WARNING, "Crity: failed to update target health HUD", t)
        }
    }
}
