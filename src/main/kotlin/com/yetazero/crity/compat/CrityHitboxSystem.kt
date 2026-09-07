package com.yetazero.crity.compat

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.CrityState
import com.yetazero.crity.config.TargetDisplayMode
import com.yetazero.crity.display.NearestHitboxes

internal class CrityHitboxSystem : EntityTickingSystem<EntityStore>() {
    private val query = Query.and(PlayerRef.getComponentType(), EntityViewer.getComponentType(), TransformComponent.getComponentType())

    override fun getQuery(): Query<EntityStore> = query

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int) = false

    override fun tick(dt: Float, index: Int, chunk: ArchetypeChunk<EntityStore>, store: Store<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
        if (!HytaleFeatures.hitboxes.isEnabled) return
        HytaleFeatures.hitboxes.run {
            val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return@run
            if (!player.isValid) return@run
            val settings = CrityState.getSettings(player.uuid).visual.hitboxes
            if (!settings.enabled || settings.mode != TargetDisplayMode.BOUNDS) return@run
            val viewer = chunk.getComponent(index, EntityViewer.getComponentType()) ?: return@run
            val origin = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: return@run
            val self = chunk.getReferenceTo(index)
            val candidates = NearestHitboxes<Ref<EntityStore>>(settings.maxEntities, settings.range)
            for (target in viewer.visible) {
                if (target == self || !target.isValid || target.store !== store || !viewer.sent.containsKey(target)) continue
                val bounds = commandBuffer.getComponent(target, BoundingBox.getComponentType())?.boundingBox ?: continue
                if (!bounds.hasVolume()) continue
                val position = commandBuffer.getComponent(target, TransformComponent.getComponentType())?.position ?: continue
                candidates.offer(target, origin.distanceSquared(position))
            }
            if (settings.showSelf) candidates.offer(self, 0.0)
            for (target in candidates.values()) {
                val bounds = commandBuffer.getComponent(target, BoundingBox.getComponentType())?.boundingBox ?: continue
                val position = commandBuffer.getComponent(target, TransformComponent.getComponentType())?.position ?: continue
                val packet = NativeHitboxDisplay.packet(position, bounds, settings, dt) ?: continue
                player.packetHandler.write(packet)
            }
        }
    }
}
