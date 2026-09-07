package com.yetazero.crity.hud

import com.yetazero.crity.CrityState
import com.yetazero.crity.config.HudAppearance
import com.yetazero.crity.config.HudStyle
import com.yetazero.crity.display.HealthHudLayout
import com.yetazero.crity.compat.HytaleFeatures
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.lifecycle.WeakSessions
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.ceil

class CrityTargetHealthHud(
    playerRef: PlayerRef,
    private val player: Player,
    private val world: World
) : CustomUIHud(playerRef, KEY) {
    companion object {
        const val KEY = "CrityTargetHealth"
        private val LOGGER = Logger.getLogger("Crity")
        private val active = WeakSessions<CrityTargetHealthHud>()

        fun getOrCreate(playerRef: PlayerRef, player: Player, world: World): CrityTargetHealthHud {
            val previous = player.hudManager.getCustomHud(KEY) as? CrityTargetHealthHud
            if (previous != null && !previous.closed && previous.world === world) return previous
            if (previous != null) player.hudManager.removeCustomHud(playerRef, KEY)
            return CrityTargetHealthHud(playerRef, player, world).also { player.hudManager.addCustomHud(playerRef, it) }
        }

        fun shutdownAll() {
            for (hud in active.values()) {
                hud.onRemove()
                if (hud.world.isAlive) {
                    try {
                        hud.world.execute { hud.remove() }
                    } catch (e: IllegalStateException) {
                        LOGGER.log(Level.FINE, "Crity HUD world already stopped", e)
                    }
                }
            }
        }
    }

    private var currentTargetRef: Ref<EntityStore>? = null
    private var currentPct = 1f
    private var phantomPct = 1f
    private var healthText = "100 / 100"
    private var currentHealth = 100f
    private var maximumHealth = 100f
    private var appearance: HudAppearance = CrityState.getSettings(playerRef.uuid).visual.hud
    private var lastHitNanos = 0L
    private var generation = 0L
    @Volatile private var closed = false
    @Volatile private var task: ScheduledFuture<*>? = null

    fun updateHealth(targetRef: Ref<EntityStore>, current: Float, max: Float, damageAmount: Float) {
        if (closed || !current.isFinite() || !max.isFinite() || max <= 0f) return
        val visibleHealth = current.coerceIn(0f, max)
        val newPct = visibleHealth / max
        val oldPct = ((current + damageAmount) / max).coerceIn(0f, 1f)
        val now = System.nanoTime()
        refreshSettings()
        val isNewTarget = currentTargetRef != targetRef || now - lastHitNanos >= TimeUnit.MILLISECONDS.toNanos(appearance.durationMs.toLong())
        currentTargetRef = targetRef
        phantomPct = if (isNewTarget) oldPct else maxOf(phantomPct, oldPct)
        currentPct = newPct
        currentHealth = visibleHealth
        maximumHealth = max
        healthText = HealthHudLayout.healthText(visibleHealth, max, appearance)
        lastHitNanos = now
        generation++
        task?.cancel(false)
        active.put(playerRef.uuid, this)
        pushBarUpdate()
        scheduleTick(generation, 30L)
    }

    fun refreshSettings() {
        if (closed) return
        val next = CrityState.getSettings(playerRef.uuid).visual.hud
        if (next == appearance) return
        appearance = next
        healthText = HealthHudLayout.healthText(currentHealth, maximumHealth, appearance)
        val builder = UICommandBuilder()
        build(builder)
        update(true, builder)
        pushBarUpdate()
    }

    fun preview() {
        if (closed) return
        currentTargetRef = null
        currentPct = 0.65f
        phantomPct = 0.9f
        currentHealth = 65f
        maximumHealth = 100f
        healthText = HealthHudLayout.healthText(currentHealth, maximumHealth, appearance)
        lastHitNanos = System.nanoTime()
        generation++
        task?.cancel(false)
        active.put(playerRef.uuid, this)
        pushBarUpdate()
        scheduleTick(generation, 30L)
    }

    private fun scheduleTick(expectedGeneration: Long, delayMillis: Long) {
        if (!closed) {
            task = world.scheduleAfter({ tick(expectedGeneration) }, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun tick(expectedGeneration: Long) {
        if (closed || generation != expectedGeneration) return
        try {
            val ref = playerRef.reference
            if (ref == null || !ref.isValid || ref.store.externalData.world !== world ||
                player.hudManager.getCustomHud(KEY) !== this) {
                onRemove()
                return
            }
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastHitNanos)
            if (elapsed >= appearance.durationMs || CrityState.getSettings(playerRef.uuid).healthMode != CrityState.HealthMode.ON) {
                remove()
                return
            }
            refreshSettings()
            if (appearance.showTrail && elapsed >= appearance.trailDelayMs && phantomPct > currentPct) {
                phantomPct = if (phantomPct > currentPct + 0.003f) {
                    phantomPct + (currentPct - phantomPct) * appearance.trailSmoothing
                } else currentPct
                pushBarUpdate()
            }
            scheduleTick(expectedGeneration, if (appearance.showTrail && phantomPct > currentPct) 30L else 150L)
        } catch (e: LinkageError) {
            fail(e)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun remove() {
        if (player.hudManager.getCustomHud(KEY) === this) {
            player.hudManager.removeCustomHud(playerRef, KEY)
        } else onRemove()
    }

    override fun onRemove() {
        closed = true
        task?.cancel(false)
        active.remove(playerRef.uuid, this)
        super.onRemove()
    }

    private fun fail(error: Throwable) {
        HytaleFeatures.hud.disable(error)
        closed = true
        task?.cancel(false)
        active.remove(playerRef.uuid, this)
        try {
            remove()
        } catch (_: LinkageError) {
        } catch (_: Exception) {
        }
    }

    private fun fillAnchor(pct: Float): Anchor {
        val length = (appearance.barLength * pct).toInt().coerceIn(0, appearance.barLength)
        val offset = appearance.barLength - length
        return Anchor().apply {
            if (appearance.isVertical) {
                setWidth(Value.of(appearance.barThickness))
                setHeight(Value.of(length))
                setLeft(Value.of(0))
                setTop(Value.of(if (appearance.invert) 0 else offset))
            } else {
                setWidth(Value.of(length))
                setHeight(Value.of(appearance.barThickness))
                setTop(Value.of(0))
                setLeft(Value.of(if (appearance.invert) offset else 0))
            }
        }
    }

    private fun pushBarUpdate() {
        try {
            val builder = UICommandBuilder()
            if (appearance.style == HudStyle.SEGMENTED) {
                val filled = ceil(currentPct * appearance.segments).toInt().coerceIn(0, appearance.segments)
                val phantomFilled = ceil(phantomPct * appearance.segments).toInt().coerceIn(0, appearance.segments)
                for (i in 0 until appearance.segments) {
                    val on = if (appearance.invert) i >= appearance.segments - filled else i < filled
                    val trailing = if (appearance.invert) i >= appearance.segments - phantomFilled else i < phantomFilled
                    val color = if (on) appearance.color else if (trailing && appearance.showTrail) appearance.trailColor else appearance.trackColor
                    builder.set("#Segment$i.Background", color)
                }
            } else {
                builder.setObject("#HealthFill.Anchor", fillAnchor(currentPct))
                builder.setObject("#PhantomFill.Anchor", fillAnchor(phantomPct))
            }
            builder.set("#HealthText.Text", healthText)
            update(false, builder)
        } catch (e: LinkageError) {
            fail(e)
            throw e
        } catch (e: Exception) {
            fail(e)
            throw e
        }
    }

    override fun build(builder: UICommandBuilder) {
        builder.appendInline(null, HealthHudLayout.build(appearance))
    }
}
