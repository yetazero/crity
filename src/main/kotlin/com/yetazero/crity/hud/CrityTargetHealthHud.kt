package com.yetazero.crity.hud

import com.yetazero.crity.CrityState
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class CrityTargetHealthHud(
    playerRef: PlayerRef,
    private val player: Player,
    private val world: World
) : CustomUIHud(playerRef, KEY) {
    companion object {
        const val KEY = "CrityTargetHealth"
        private const val BAR_WIDTH = 404
        private val LOGGER = Logger.getLogger("Crity")
        private val active = ConcurrentHashMap.newKeySet<CrityTargetHealthHud>()

        fun shutdownAll() {
            for (hud in active.toList()) {
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
        val isNewTarget = currentTargetRef != targetRef || now - lastHitNanos >= TimeUnit.SECONDS.toNanos(5)
        currentTargetRef = targetRef
        phantomPct = if (isNewTarget) oldPct else maxOf(phantomPct, oldPct)
        currentPct = newPct
        healthText = "${kotlin.math.ceil(visibleHealth).toInt()} / ${kotlin.math.ceil(max).toInt()}"
        lastHitNanos = now
        generation++
        task?.cancel(false)
        active.add(this)
        pushBarUpdate()
        scheduleTick(generation, 450L)
    }

    private fun scheduleTick(expectedGeneration: Long, delayMillis: Long) {
        if (!closed) {
            task = world.scheduleAfter({ tick(expectedGeneration) }, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun tick(expectedGeneration: Long) {
        if (closed || generation != expectedGeneration) return
        val ref = playerRef.reference
        if (ref == null || !ref.isValid || ref.store.externalData.world !== world ||
            player.hudManager.getCustomHud(KEY) !== this) {
            onRemove()
            return
        }
        try {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastHitNanos)
            if (elapsed >= 5000L || CrityState.getSettings(playerRef.uuid).healthMode != CrityState.HealthMode.ON) {
                remove()
                return
            }
            if (elapsed >= 450L && phantomPct > currentPct) {
                phantomPct = if (phantomPct > currentPct + 0.003f) {
                    phantomPct + (currentPct - phantomPct) * 0.15f
                } else currentPct
                pushBarUpdate()
            }
            scheduleTick(expectedGeneration, 30L)
        } catch (e: Exception) {
            onRemove()
            LOGGER.log(Level.WARNING, "Crity HUD update failed", e)
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
        active.remove(this)
        super.onRemove()
    }

    private fun pushBarUpdate() {
        try {
            val healthW = (BAR_WIDTH * currentPct).toInt().coerceIn(0, BAR_WIDTH)
            val phantomW = (BAR_WIDTH * phantomPct).toInt().coerceIn(0, BAR_WIDTH)

            val healthAnchor = Anchor().apply {
                setLeft(Value.of(0))
                setWidth(Value.of(healthW))
                setHeight(Value.of(20))
                setTop(Value.of(0))
                setBottom(Value.of(0))
            }
            val phantomAnchor = Anchor().apply {
                setLeft(Value.of(0))
                setWidth(Value.of(phantomW))
                setHeight(Value.of(20))
                setTop(Value.of(0))
                setBottom(Value.of(0))
            }

            val builder = UICommandBuilder()
            builder.setObject("#HealthFill.Anchor", healthAnchor)
            builder.setObject("#PhantomFill.Anchor", phantomAnchor)
            builder.set("#HealthText.Text", healthText)
            update(false, builder)
        } catch (t: Exception) {
            LOGGER.log(Level.WARNING, "Crity HUD push failed", t)
        }
    }

    override fun build(builder: UICommandBuilder) {
        val layout = """
            Group {
              Anchor: (Top: 58, Height: 34);
              LayoutMode: Center;

              Group #TargetHud {
                Anchor: (Width: 420, Height: 34);
                Background: #000000(0.75);
                Padding: (Horizontal: 8, Vertical: 7);

                Group #BarTrack {
                  Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
                  Background: #141414;

                  Group #PhantomFill {
                    Anchor: (Left: 0, Width: 404, Height: 20, Top: 0, Bottom: 0);
                    Background: #e67e22;
                  }

                  Group #HealthFill {
                    Anchor: (Left: 0, Width: 404, Height: 20, Top: 0, Bottom: 0);
                    Background: #e74c3c;
                  }

                  Label #HealthText {
                    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
                    Text: "100 / 100";
                    Style: (
                      FontSize: 13,
                      TextColor: #ffffff,
                      Alignment: Center
                    );
                  }
                }
              }
            }
        """.trimIndent()

        builder.appendInline(null, layout)
    }
}
