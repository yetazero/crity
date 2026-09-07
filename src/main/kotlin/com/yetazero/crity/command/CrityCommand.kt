package com.yetazero.crity.command

import com.yetazero.crity.CrityState
import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.concurrent.CompletableFuture

class CrityCommand : AbstractCommand("crity", "Manage combat text and health display") {
    init {
        requireNoPermission()
        addSubCommand(ModeCommand("damage", "Damage display mode", CrityState.DamageMode.entries.map { it.name.lowercase() }) { settings, mode ->
            settings.damageMode = CrityState.DamageMode.valueOf(mode.uppercase())
        })
        addSubCommand(ModeCommand("health", "Health display mode", CrityState.HealthMode.entries.map { it.name.lowercase() }) { settings, mode ->
            settings.healthMode = CrityState.HealthMode.valueOf(mode.uppercase())
        })
        addSubCommand(ModeCommand("debug", "Log your damage events", listOf("on", "off"), false) { settings, mode ->
            settings.debugDamage = mode == "on"
        })
    }

    override fun execute(ctx: CommandContext): CompletableFuture<Void> {
        val player = player(ctx) ?: return CompletableFuture.completedFuture(null)
        val settings = CrityState.getSettings(player.uuid)
        ctx.sendMessage(Message.raw("[Crity] Damage: ${settings.damageMode.name}; Health: ${settings.healthMode.name}; Debug: ${settings.debugDamage}"))
        ctx.sendMessage(Message.raw("/crity damage on|default|off | /crity health on|default|off | /crity debug on|off"))
        return CompletableFuture.completedFuture(null)
    }

    private class ModeCommand(
        private val modeName: String,
        description: String,
        modes: List<String>,
        private val persist: Boolean = true,
        private val action: (CrityState.PlayerSettings, String) -> Unit
    ) : AbstractCommand(modeName, description) {
        init {
            requireNoPermission()
            for (mode in modes) {
                addSubCommand(object : AbstractCommand(mode, "Set $modeName to $mode") {
                    init { requireNoPermission() }
                    override fun execute(ctx: CommandContext): CompletableFuture<Void> = update(ctx, mode)
                })
            }
        }

        override fun execute(ctx: CommandContext): CompletableFuture<Void> {
            ctx.sendMessage(Message.raw("Usage: /crity $modeName ${if (modeName == "debug") "on|off" else "on|default|off"}"))
            return CompletableFuture.completedFuture(null)
        }

        private fun update(ctx: CommandContext, mode: String): CompletableFuture<Void> {
            val playerRef = player(ctx) ?: return CompletableFuture.completedFuture(null)
            val ref = playerRef.reference
            if (ref == null || !ref.isValid) return CompletableFuture.completedFuture(null)
            val store = ref.store
            val world = store.externalData.world
            return CompletableFuture.runAsync({
                if (!ref.isValid || playerRef.reference != ref) return@runAsync
                val settings = CrityState.getSettings(playerRef.uuid)
                action(settings, mode)
                if (modeName == "health" && settings.healthMode != CrityState.HealthMode.ON) {
                    val player = store.getComponent(ref, Player.getComponentType())
                    player?.hudManager?.removeCustomHud(playerRef, CrityTargetHealthHud.KEY)
                }
                if (persist) CrityState.saveConfig()
                ctx.sendMessage(Message.raw("[Crity] $modeName: ${mode.uppercase()}"))
            }, world)
        }
    }

    companion object {
        private fun player(ctx: CommandContext): PlayerRef? {
            if (ctx.isPlayer) return ctx.senderAs(PlayerRef::class.java)
            ctx.sendMessage(Message.raw("[Crity] This command is available to players only."))
            return null
        }
    }
}
