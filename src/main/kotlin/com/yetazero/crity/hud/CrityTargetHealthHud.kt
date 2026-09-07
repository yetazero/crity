package com.yetazero.crity.hud

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager
import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class CrityTargetHealthHud(
    playerRef: PlayerRef,
    private val player: Player
) : CustomUIHud(playerRef, KEY) {

    companion object {
        const val KEY = "CrityTargetHealth"
        const val BAR_WIDTH = 404
        private val LOGGER = Logger.getLogger("Crity")

        private val EXECUTOR: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
            ThreadFactory { r -> Thread(r, "Crity-HudManager").apply { isDaemon = true } }
        )

        fun shutdownExecutor() {
            EXECUTOR.shutdownNow()
        }
    }

    private var currentTargetRef: Ref<EntityStore>? = null
    @Volatile private var currentPct: Float = 1.0f
    @Volatile private var phantomPct: Float = 1.0f
    @Volatile private var healthText: String = "100 / 100"
    @Volatile private var lastHitTime: Long = System.currentTimeMillis()

    private var drainTask: ScheduledFuture<*>? = null
    private var autoCloseTask: ScheduledFuture<*>? = null

    @Synchronized
    fun updateHealth(targetRef: Ref<EntityStore>, current: Float, max: Float, damageAmount: Float) {
        val safeMax = max.coerceAtLeast(1.0f)
        val newPct = (current / safeMax).coerceIn(0.0f, 1.0f)
        val oldPct = ((current + damageAmount) / safeMax).coerceIn(0.0f, 1.0f)

        val isNewTarget = currentTargetRef == null || currentTargetRef != targetRef || isExpired()
        currentTargetRef = targetRef

        phantomPct = if (isNewTarget) oldPct else maxOf(phantomPct, oldPct)
        currentPct = newPct
        healthText = "${Math.ceil(current.toDouble()).toInt()} / ${Math.ceil(max.toDouble()).toInt()}"
        lastHitTime = System.currentTimeMillis()

        drainTask?.cancel(false)
        autoCloseTask?.cancel(false)

        pushBarUpdate()

        drainTask = EXECUTOR.scheduleWithFixedDelay({
            try {
                val now = System.currentTimeMillis()
                if (now - lastHitTime < 450L) return@scheduleWithFixedDelay

                if (phantomPct > currentPct + 0.003f) {
                    phantomPct += (currentPct - phantomPct) * 0.15f
                    pushBarUpdate()
                } else {
                    phantomPct = currentPct
                    pushBarUpdate()
                    drainTask?.cancel(false)
                }
            } catch (t: Throwable) {
                LOGGER.log(Level.WARNING, "Crity drain task failed", t)
                drainTask?.cancel(false)
            }
        }, 450L, 30L, TimeUnit.MILLISECONDS)

        autoCloseTask = EXECUTOR.schedule({
            try {
                drainTask?.cancel(false)
                val hudManager: HudManager? = player.hudManager
                hudManager?.removeCustomHud(playerRef, KEY)
            } catch (t: Throwable) {
                LOGGER.log(Level.WARNING, "Crity auto-close task failed", t)
            }
        }, 5000L, TimeUnit.MILLISECONDS)
    }

    fun isExpired(): Boolean = (System.currentTimeMillis() - lastHitTime) > 5000L

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
        } catch (t: Throwable) {
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
