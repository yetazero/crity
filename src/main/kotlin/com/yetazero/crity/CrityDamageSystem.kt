package com.yetazero.crity

import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.query.Query
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
import com.hypixel.hytale.server.core.modules.entityui.asset.CombatTextUIComponent
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.logging.Level
import java.util.logging.Logger

class CrityDamageSystem : DamageEventSystem() {

    companion object {
        private val LOGGER = Logger.getLogger("Crity")
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
        if (damage.isCancelled || !amount.isFinite() || amount <= 0f) return

        val source = damage.source as? Damage.EntitySource ?: return
        val attackerRef = source.ref
        if (!attackerRef.isValid) return

        val playerRef = cmdBuf.getComponent(attackerRef, PlayerRef.getComponentType()) ?: return
        if (!playerRef.isValid) return

        val player = cmdBuf.getComponent(attackerRef, Player.getComponentType())
        val viewer = cmdBuf.getComponent(attackerRef, EntityViewer.getComponentType()) ?: return

        val settings = CrityState.getSettings(playerRef.uuid)
        val targetRef = chunk.getReferenceTo(index)

        val baseAngle = damage.getIfPresentMetaObject(Damage.HIT_ANGLE) ?: 0f
        val percent = if (settings.damageMode == CrityState.DamageMode.ON) {
            resolveDamagePercent(cmdBuf, attackerRef, amount, damage)
        } else 0f
        val angle = CrityCombatDisplay.angle(settings.damageMode, baseAngle)
        val text = CrityCombatDisplay.text(settings.damageMode, amount, percent, angle)

        val map = EntityUIComponent.getAssetMap()
        val originalUi = cmdBuf.getComponent(targetRef, UIComponentList.getComponentType())
            ?.componentIds ?: intArrayOf()
        val preferredId = if (settings.damageMode == CrityState.DamageMode.ON) "CrityCombat" else "CombatText"
        val selectedIndex = if (text == null) null else {
            map.getIndex(preferredId).takeIf { it >= 0 }
                ?: map.getIndex("CombatText").takeIf { it >= 0 }
        }
        val activeUi = CrityCombatDisplay.components(
            originalUi,
            { map.getAsset(it) is CombatTextUIComponent },
            selectedIndex,
            map.getIndex("Healthbar"),
            settings.healthMode
        )
        viewer.queueUpdate(targetRef, UIComponentsUpdate(activeUi))
        if (text != null && selectedIndex != null) viewer.queueUpdate(targetRef, text)

        if (settings.debugDamage) {
            LOGGER.log(Level.INFO, "Crity DEBUG hit event=${System.identityHashCode(damage)} " +
                "time=${System.nanoTime()} target=${targetRef.index} attacker=${attackerRef.index} " +
                "amount=$amount initial=${damage.initialAmount} cause=${damage.damageCauseIndex} " +
                "percent=$percent baseAngle=$baseAngle angle=${text?.hitAngleDeg} " +
                "uiBefore=${originalUi.joinToString { map.getAsset(it)?.id ?: it.toString() }} " +
                "uiAfter=${activeUi.joinToString { map.getAsset(it)?.id ?: it.toString() }} " +
                "packets=${if (text != null && selectedIndex != null) 1 else 0} text=${text?.text}")
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
        } catch (t: Exception) {
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
                ?: CrityTargetHealthHud(playerRef, player, targetRef.store.externalData.world).also { hudManager.addCustomHud(playerRef, it) }
            hud.updateHealth(targetRef, cur, max, damageAmount)
        } catch (t: Exception) {
            LOGGER.log(Level.WARNING, "Crity: failed to update target health HUD", t)
        }
    }
}
