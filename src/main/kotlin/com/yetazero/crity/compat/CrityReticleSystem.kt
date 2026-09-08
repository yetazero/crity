package com.yetazero.crity.compat

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.CrityState
import com.yetazero.crity.hud.CrityReticleHud
import com.yetazero.crity.ui.CritySettingsPage

internal class CrityReticleSystem : EntityTickingSystem<EntityStore>() {
    private val query = Query.and(PlayerRef.getComponentType(), Player.getComponentType())
    override fun getQuery(): Query<EntityStore> = query
    override fun isParallel(archetypeChunkSize: Int, taskCount: Int) = false

    override fun tick(dt: Float, index: Int, chunk: ArchetypeChunk<EntityStore>, store: Store<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
        val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        if (!playerRef.isValid) return
        if (!HytaleFeatures.reticle.isEnabled) {
            runCatching { (player.hudManager.getCustomHud(CrityReticleHud.KEY) as? CrityReticleHud)?.remove() }
            return
        }
        val success = HytaleFeatures.reticle.run {
            val settings = CrityState.getSettings(playerRef.uuid).visual.reticle
            if (!settings.enabled || player.pageManager.customPage is CritySettingsPage) {
                (player.hudManager.getCustomHud(CrityReticleHud.KEY) as? CrityReticleHud)?.remove()
                return@run true
            }
            val old = player.hudManager.getCustomHud(CrityReticleHud.KEY) as? CrityReticleHud
            val now = System.nanoTime()
            if (old != null && now < old.nextUpdate) return@run true
            val ranged = HytaleFeatures.reticleWeapons.run {
                val item = InventoryComponent.getItemInHand(commandBuffer, chunk.getReferenceTo(index))?.item
                val root = item?.interactions?.get(InteractionType.Primary)?.let { RootInteraction.getAssetMap().getAsset(it) }
                root?.data?.rawTags?.get("Attack")?.contains("Ranged") == true
            } ?: false
            val profile = settings.profile(ranged)
            if (!profile.enabled) {
                old?.remove()
                return@run true
            }
            val hud = CrityReticleHud.getOrCreate(playerRef, player, store.externalData.world)
            hud.nextUpdate = now + 100_000_000L
            hud.render(profile)
            true
        } ?: false
        if (!success) runCatching { (player.hudManager.getCustomHud(CrityReticleHud.KEY) as? CrityReticleHud)?.remove() }
    }
}
