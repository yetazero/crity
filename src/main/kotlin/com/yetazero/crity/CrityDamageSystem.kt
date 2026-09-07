package com.yetazero.crity

import com.yetazero.crity.hud.CrityTargetHealthHud
import com.yetazero.crity.compat.NativeTargetHighlight
import com.yetazero.crity.compat.FeatureGate
import com.yetazero.crity.compat.HytaleFeatures
import com.yetazero.crity.compat.HytaleDamageMetadata
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.protocol.UIComponentsUpdate
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.InventoryComponent
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

class CrityDamageSystem(private val stock: DamageEventSystem) : DamageEventSystem() {

    companion object {
        private val LOGGER = Logger.getLogger("Crity")
    }

    private val weaponGate = FeatureGate("weapon damage metadata")
    private val causeGate = FeatureGate("damage cause metadata")
    private val displayGate = HytaleFeatures.combat

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
        if (!displayGate.isEnabled) {
            stock.handle(index, chunk, store, cmdBuf, damage)
            return
        }
        displayGate.run { handleCustom(index, chunk, store, cmdBuf, damage) }
    }

    private fun handleCustom(
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
        HytaleFeatures.highlight.run { NativeTargetHighlight.hit(playerRef, viewer, targetRef) }

        val baseAngle = damage.getIfPresentMetaObject(Damage.HIT_ANGLE) ?: 0f
        val percent = if (settings.damageMode == CrityState.DamageMode.ON) {
            weaponGate.run { HytaleDamageMetadata.resolveDamagePercent(cmdBuf, attackerRef, amount, damage) }
                ?: if (amount >= damage.initialAmount.coerceAtLeast(1f) * 1.15f) 0.9f else 0.5f
        } else 0f
        val appearance = settings.visual.damage
        val cause = causeGate.run { DamageCause.getAssetMap().getAsset(damage.damageCauseIndex) }
        val weapon = if (appearance.rules.any { it.weaponPrefix.isNotEmpty() }) {
            weaponGate.run { InventoryComponent.getItemInHand(cmdBuf, attackerRef)?.item?.id } ?: ""
        } else ""
        val angle = CrityCombatDisplay.angle(settings.damageMode, baseAngle, appearance = appearance)
        val defaultColor = causeGate.run { cause?.resolveDamageTextColor() }
        val text = CrityCombatDisplay.text(settings.damageMode, amount, percent, angle,
            appearance, cause?.id ?: "", weapon, defaultColor)

        if (settings.healthMode == CrityState.HealthMode.ON && player != null) {
            HytaleFeatures.hud.run { updateTargetHealthHud(cmdBuf, targetRef, playerRef, player, amount) }
        }
        val effectiveHealthMode = if (settings.healthMode == CrityState.HealthMode.ON && !HytaleFeatures.hud.isEnabled)
            CrityState.HealthMode.DEFAULT else settings.healthMode

        val map = EntityUIComponent.getAssetMap()
        val originalUi = cmdBuf.getComponent(targetRef, UIComponentList.getComponentType())
            ?.componentIds ?: intArrayOf()
        val preferredId = if (settings.damageMode == CrityState.DamageMode.ON) appearance.template else "CombatText"
        val selectedIndex = if (text == null) null else {
            map.getIndex(preferredId).takeIf { it >= 0 && map.getAsset(it) is CombatTextUIComponent }
                ?: map.getIndex("CrityCombat").takeIf { settings.damageMode == CrityState.DamageMode.ON && it >= 0 }
                ?: map.getIndex("CombatText").takeIf { it >= 0 }
        }
        val activeUi = CrityCombatDisplay.components(
            originalUi,
            { map.getAsset(it) is CombatTextUIComponent },
            selectedIndex,
            map.getIndex("Healthbar"),
            effectiveHealthMode
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

    }

    private fun updateTargetHealthHud(
        cmdBuf: CommandBuffer<EntityStore>,
        targetRef: Ref<EntityStore>,
        playerRef: PlayerRef,
        player: Player,
        damageAmount: Float
    ) {
        val stats = cmdBuf.getComponent(targetRef, EntityStatMap.getComponentType()) ?: return
        val healthIdx = DefaultEntityStatTypes.getHealth()
        val statVal = stats.get(healthIdx) ?: return
        val cur = statVal.get()
        val max = statVal.max

        val hud = CrityTargetHealthHud.getOrCreate(playerRef, player, targetRef.store.externalData.world)
        hud.updateHealth(targetRef, cur, max, damageAmount)
    }
}
