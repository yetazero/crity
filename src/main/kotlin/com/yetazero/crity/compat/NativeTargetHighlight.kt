package com.yetazero.crity.compat

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.Color
import com.hypixel.hytale.protocol.EffectOp
import com.hypixel.hytale.protocol.EntityEffectUpdate
import com.hypixel.hytale.protocol.EntityEffectsUpdate
import com.hypixel.hytale.protocol.UpdateType
import com.hypixel.hytale.protocol.packets.assets.UpdateModelvfxs
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
import com.hypixel.hytale.server.core.asset.type.modelvfx.config.ModelVFX
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.CrityState
import com.yetazero.crity.config.HitboxAppearance
import com.yetazero.crity.config.TargetDisplayMode
import com.yetazero.crity.display.TargetLease
import com.yetazero.crity.lifecycle.WeakSessions
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal object NativeTargetHighlight {
    const val ASSET_ID = "CrityTargetHighlight"
    private val active = WeakSessions<Session>()
    private var effectId = -1

    fun validateAssets() {
        effectId = EntityEffect.getAssetMap().getIndex(ASSET_ID)
        check(effectId >= 0 && ModelVFX.getAssetMap().getIndex(ASSET_ID) >= 0) { "Crity target highlight assets are unavailable." }
    }

    fun hit(player: PlayerRef, viewer: EntityViewer, target: Ref<EntityStore>) {
        val visual = CrityState.getSettings(player.uuid).visual
        if (!visual.hitboxes.enabled || visual.hitboxes.mode != TargetDisplayMode.GLOW) return
        if (!target.isValid || !viewer.visible.contains(target) || !viewer.sent.containsKey(target)) return
        val world = target.store.externalData.world
        val now = System.nanoTime()
        val previous = active[player.uuid]
        if (previous != null && previous.world === world && previous.player === player && previous.lease.target == target) {
            previous.lease.refresh(now)
            previous.show(viewer, visual.hitboxes, visual.hud.durationMs)
            return
        }
        if (previous?.world === world) previous.stop() else previous?.stopOnWorld()
        val session = Session(player, world, TargetLease(target, now), effectId)
        active.put(player.uuid, session)
        try {
            session.show(viewer, visual.hitboxes, visual.hud.durationMs)
            session.schedule()
        } catch (e: Throwable) {
            session.stop()
            throw e
        }
    }

    fun effect(index: Int, remainingSeconds: Float, remove: Boolean = false): EntityEffectsUpdate {
        require(index >= 0 && remainingSeconds.isFinite())
        return EntityEffectsUpdate(arrayOf(EntityEffectUpdate(
            if (remove) EffectOp.Remove else EffectOp.Add, index,
            if (remove) 0f else remainingSeconds.coerceIn(0.001f, 60f), false, false, null
        )))
    }

    fun customize(original: com.hypixel.hytale.protocol.ModelVFX, settings: HitboxAppearance): com.hypixel.hytale.protocol.ModelVFX {
        val rgb = settings.color.removePrefix("#").toInt(16)
        fun channel(shift: Int) = (((rgb shr shift) and 255) * settings.opacity).toInt().toByte()
        return original.clone().also {
            it.highlightColor = Color(channel(16), channel(8), channel(0))
            it.highlightThickness = settings.thickness
            it.postColor = Color((rgb shr 16).toByte(), (rgb shr 8).toByte(), rgb.toByte())
            it.postColorOpacity = 0.08f * settings.opacity
            it.opacity = 1f
        }
    }

    fun shutdownAll() {
        val sessions = active.values()
        active.clear()
        sessions.forEach { it.stopOnWorld() }
    }

    private class Session(
        val player: PlayerRef,
        val world: World,
        val lease: TargetLease<Ref<EntityStore>>,
        val index: Int
    ) {
        private var task: ScheduledFuture<*>? = null
        private var closed = false
        private var lastAppearance: HitboxAppearance? = null
        private var lastAsset: ModelVFX? = null

        fun show(viewer: EntityViewer, settings: HitboxAppearance, durationMs: Int) {
            val map = ModelVFX.getAssetMap()
            val id = map.getIndex(ASSET_ID)
            check(id >= 0) { "Crity highlight VFX was removed." }
            val asset = checkNotNull(map.getAsset(id))
            if (lastAppearance != settings || lastAsset !== asset) {
                val vfx = customize(asset.toPacket(), settings)
                player.packetHandler.write(UpdateModelvfxs(UpdateType.AddOrUpdate, map.nextIndex, mapOf(id to vfx)))
                lastAppearance = settings
                lastAsset = asset
            }
            viewer.queueUpdate(lease.target, effect(index, durationMs / 1000f))
        }

        fun schedule() {
            task = world.scheduleAfter({ tick() }, 100L, TimeUnit.MILLISECONDS)
        }

        private fun viewer(): EntityViewer? {
            val ref = player.reference ?: return null
            if (!player.isValid || !ref.isValid || ref.store.externalData.world !== world) return null
            return ref.store.getComponent(ref, EntityViewer.getComponentType())
        }

        private fun tick() {
            if (closed) return
            val success = HytaleFeatures.highlight.run {
                val visual = CrityState.getSettings(player.uuid).visual
                val viewer = viewer()
                if (active[player.uuid] !== this || !visual.hitboxes.enabled || visual.hitboxes.mode != TargetDisplayMode.GLOW ||
                    lease.remainingSeconds(System.nanoTime(), visual.hud.durationMs) <= 0f ||
                    !lease.target.isValid || viewer == null || !viewer.visible.contains(lease.target)) {
                    stop()
                } else {
                    schedule()
                }
                true
            } ?: false
            if (!success) stop()
        }

        fun stopOnWorld() {
            task?.cancel(false)
            if (world.isAlive) {
                try {
                    world.execute { stop() }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    active.remove(player.uuid, this)
                }
            } else {
                active.remove(player.uuid, this)
            }
        }

        fun stop() {
            if (closed) return
            closed = true
            task?.cancel(false)
            active.remove(player.uuid, this)
            try {
                val viewer = viewer()
                if (lease.target.isValid && viewer != null && viewer.visible.contains(lease.target) && viewer.sent.containsKey(lease.target)) {
                    viewer.queueUpdate(lease.target, effect(index, 0f, true))
                }
            } catch (e: LinkageError) {
                HytaleFeatures.highlight.disable(e)
            } catch (e: Exception) {
                HytaleFeatures.highlight.disable(e)
            }
        }
    }
}
