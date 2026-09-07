package com.yetazero.crity.command

import com.yetazero.crity.CrityState
import com.yetazero.crity.ui.CritySettingsPage
import com.yetazero.crity.compat.HytaleFeatures
import com.yetazero.crity.config.TargetDisplayMode
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
        AppearanceCommands.register(this)
        addSubCommand(object : AbstractCommand("help", "List Crity text commands") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = help(ctx)
        })
        addSubCommand(ModeCommand("hitboxes", "Target glow or diagnostic entity bounds", listOf("on", "off")) { settings, mode ->
            settings.visual = settings.visual.copy(hitboxes = settings.visual.hitboxes.copy(enabled = mode == "on"))
        })
        addSubCommand(ModeCommand("highlight", "Highlight the last damaged target", listOf("on", "off")) { settings, mode ->
            settings.visual = settings.visual.copy(hitboxes = settings.visual.hitboxes.copy(enabled = mode == "on", mode = TargetDisplayMode.GLOW))
        })
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
        if (!ctx.isPlayer) return help(ctx)
        val playerRef = player(ctx) ?: return CompletableFuture.completedFuture(null)
        val ref = playerRef.reference ?: return CompletableFuture.completedFuture(null)
        if (!ref.isValid) return CompletableFuture.completedFuture(null)
        val store = ref.store
        return CompletableFuture.runAsync({
            if (!ref.isValid || playerRef.reference != ref) return@runAsync
            val player = store.getComponent(ref, Player.getComponentType()) ?: return@runAsync
            val opened = HytaleFeatures.settings.run { CritySettingsPage.open(playerRef, player, ref, store); true } ?: false
            if (!opened) {
                ctx.sendMessage(Message.raw("[Crity] Settings panel unavailable. Commands remain available; /crity help. See server log."))
                help(ctx)
            }
        }, store.externalData.world)
    }

    private fun help(ctx: CommandContext): CompletableFuture<Void> {
        if (!ctx.isPlayer) {
            ctx.sendMessage(Message.raw("[Crity] Players: /crity opens settings. /crity reload reloads configuration."))
            return CompletableFuture.completedFuture(null)
        }
        val player = player(ctx) ?: return CompletableFuture.completedFuture(null)
        val settings = CrityState.getSettings(player.uuid)
        ctx.sendMessage(Message.raw("[Crity] Damage: ${settings.damageMode.name}; Health: ${settings.healthMode.name}; Debug: ${settings.debugDamage}"))
        ctx.sendMessage(Message.raw("[Crity] Target display: ${settings.visual.hitboxes.mode}, enabled: ${settings.visual.hitboxes.enabled}; /crity highlight on|off"))
        ctx.sendMessage(Message.raw("/crity damage on|default|off | /crity health on|default|off | /crity debug on|off"))
        ctx.sendMessage(Message.raw("/crity settings | /crity set <setting> <value> | /crity color <rule> <hex> | /crity preview | /crity reset"))
        ctx.sendMessage(Message.raw("/crity rule <id> <field> <value> | /crity rule-add <id> | /crity rule-remove <id> | /crity rule-move <id> <position>"))
        ctx.sendMessage(Message.raw("/crity export <name> | /crity import <name> | /crity exports | /crity diagnostics"))
        return CompletableFuture.completedFuture(null)
    }

    private class ModeCommand(
        private val modeName: String,
        description: String,
        private val modes: List<String>,
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
            ctx.sendMessage(Message.raw("Usage: /crity $modeName ${modes.joinToString("|")}"))
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
                val displayMode = if (modeName == "highlight") TargetDisplayMode.GLOW else CrityState.getSettings(playerRef.uuid).visual.hitboxes.mode
                val displayAvailable = if (displayMode == TargetDisplayMode.GLOW) HytaleFeatures.highlight.isEnabled else HytaleFeatures.hitboxes.isEnabled
                if (modeName in setOf("hitboxes", "highlight") && mode == "on" && !displayAvailable) {
                    ctx.sendMessage(Message.raw("[Crity] Target display is unavailable on this server. See server log."))
                    return@runAsync
                }
                val saved = CrityState.updateSettings(playerRef.uuid, persist) { action(it, mode) }
                val settings = CrityState.getSettings(playerRef.uuid)
                if (modeName == "health" && settings.healthMode != CrityState.HealthMode.ON) {
                    val player = store.getComponent(ref, Player.getComponentType())
                    player?.hudManager?.removeCustomHud(playerRef, CrityTargetHealthHud.KEY)
                }
                if (!saved) ctx.sendMessage(Message.raw("[Crity] Could not save; changed for this session only. See server log."))
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
