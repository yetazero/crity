package com.yetazero.crity.hud

import com.hypixel.hytale.protocol.packets.interface_.HudComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.yetazero.crity.config.ReticleProfile
import com.yetazero.crity.display.ReticleLayout
import com.yetazero.crity.lifecycle.WeakSessions

internal class CrityReticleHud(playerRef: PlayerRef, private val player: Player, val world: World) : CustomUIHud(playerRef, KEY) {
    var nextUpdate = 0L
    private var lastSettings: ReticleProfile? = null
    private var hidReticle = false
    var closed = false
        private set

    override fun build(builder: UICommandBuilder) {
        builder.appendInline(null, "Group #ReticleRoot { HitTestVisible: false; LayoutMode: CenterMiddle; }")
    }

    fun render(settings: ReticleProfile) {
        if (settings == lastSettings) return
        val next = ReticleLayout.build(settings)
        update(false, UICommandBuilder().clear("#ReticleRoot").appendInline("#ReticleRoot", next))
        lastSettings = settings
        if (!hidReticle && HudComponent.Reticle in player.hudManager.visibleHudComponents) {
            player.hudManager.hideHudComponents(playerRef, HudComponent.Reticle)
            hidReticle = true
        }
    }

    override fun onRemove() {
        closed = true
        active.remove(playerRef.uuid, this)
        if (hidReticle) {
            hidReticle = false
            if (playerRef.isValid) player.hudManager.showHudComponents(playerRef, HudComponent.Reticle)
        }
        super.onRemove()
    }

    fun remove() {
        if (player.hudManager.getCustomHud(KEY) === this) player.hudManager.removeCustomHud(playerRef, KEY)
        else onRemove()
    }

    companion object {
        const val KEY = "yetazero:CrityReticle"
        private val active = WeakSessions<CrityReticleHud>()

        fun getOrCreate(playerRef: PlayerRef, player: Player, world: World): CrityReticleHud {
            val old = player.hudManager.getCustomHud(KEY) as? CrityReticleHud
            if (old != null && !old.closed && old.world === world) return old
            old?.remove()
            val hud = CrityReticleHud(playerRef, player, world)
            player.hudManager.addCustomHud(playerRef, hud)
            active.put(playerRef.uuid, hud)
            return hud
        }

        fun shutdownAll() {
            val current = active.values()
            active.clear()
            for (hud in current) {
                if (hud.world.isAlive) runCatching { hud.world.execute { runCatching { hud.remove() } } }
            }
        }
    }
}
