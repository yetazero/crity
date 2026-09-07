package com.yetazero.crity.command

import com.yetazero.crity.CrityState
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.UUID
import java.util.concurrent.CompletableFuture

class CrityCommand : AbstractCommand("crity", "Manage combat text and health display") {

    init {
        requireNoPermission()
        addSubCommand(DamageSubCommand())
        addSubCommand(HealthSubCommand())
    }

    override fun execute(ctx: CommandContext): CompletableFuture<Void> {
        val settings = CrityState.getSettings(getPlayerUuid(ctx))

        ctx.sendMessage(Message.raw("=== [ Crity Status ] ==="))
        ctx.sendMessage(Message.raw("Damage: ${settings.damageMode.name} (on | default | off)"))
        ctx.sendMessage(Message.raw("Health: ${settings.healthMode.name} (on | default | off)"))
        ctx.sendMessage(Message.raw("Usage: /crity damage [on|default|off] | /crity health [on|default|off]"))
        return CompletableFuture.completedFuture(null)
    }

    companion object {
        fun getPlayerUuid(ctx: CommandContext): UUID? = try {
            ctx.senderAs(PlayerRef::class.java).uuid
        } catch (t: Throwable) {
            null
        }
    }

    private class DamageSubCommand : AbstractCommand("damage", "Set damage number display mode") {
        init {
            requireNoPermission()
            setAllowsExtraArguments(true)

            addSubCommand(ModeOptionCommand("on", "Enable custom tier damage numbers and CRIT!") { settings ->
                settings.damageMode = CrityState.DamageMode.ON
                "[Crity] Damage display: ON (Custom tiers + CRIT!)"
            })
            addSubCommand(ModeOptionCommand("default", "Use standard vanilla white damage") { settings ->
                settings.damageMode = CrityState.DamageMode.DEFAULT
                "[Crity] Damage display: DEFAULT (Vanilla white)"
            })
            addSubCommand(ModeOptionCommand("off", "Disable damage numbers") { settings ->
                settings.damageMode = CrityState.DamageMode.OFF
                "[Crity] Damage display: OFF"
            })
        }

        override fun execute(ctx: CommandContext): CompletableFuture<Void> {
            val settings = CrityState.getSettings(getPlayerUuid(ctx))
            val input = ctx.inputString.trim().lowercase()

            if (input.contains("off") || input.contains("disable")) {
                settings.damageMode = CrityState.DamageMode.OFF
            } else if (input.contains("default") || input.contains("vanilla") || input.contains("white")) {
                settings.damageMode = CrityState.DamageMode.DEFAULT
            } else if (input.contains("on") || input.contains("crity") || input.contains("enable") || input.contains("custom")) {
                settings.damageMode = CrityState.DamageMode.ON
            } else {
                settings.damageMode = when (settings.damageMode) {
                    CrityState.DamageMode.ON -> CrityState.DamageMode.DEFAULT
                    CrityState.DamageMode.DEFAULT -> CrityState.DamageMode.OFF
                    CrityState.DamageMode.OFF -> CrityState.DamageMode.ON
                }
            }

            ctx.sendMessage(Message.raw("[Crity] Damage display: ${settings.damageMode.name}"))
            CrityState.saveConfig()
            return CompletableFuture.completedFuture(null)
        }
    }

    private class HealthSubCommand : AbstractCommand("health", "Set entity health display mode") {
        init {
            requireNoPermission()
            setAllowsExtraArguments(true)

            addSubCommand(ModeOptionCommand("on", "Enable RPG Target Health HUD") { settings ->
                settings.healthMode = CrityState.HealthMode.ON
                "[Crity] Health display: ON (RPG Target HUD)"
            })
            addSubCommand(ModeOptionCommand("default", "Use standard 3D entity health bar") { settings ->
                settings.healthMode = CrityState.HealthMode.DEFAULT
                "[Crity] Health display: DEFAULT (Classic 3D bar)"
            })
            addSubCommand(ModeOptionCommand("off", "Disable entity health display") { settings ->
                settings.healthMode = CrityState.HealthMode.OFF
                "[Crity] Health display: OFF"
            })
        }

        override fun execute(ctx: CommandContext): CompletableFuture<Void> {
            val settings = CrityState.getSettings(getPlayerUuid(ctx))
            val input = ctx.inputString.trim().lowercase()

            if (input.contains("off") || input.contains("disable")) {
                settings.healthMode = CrityState.HealthMode.OFF
            } else if (input.contains("default") || input.contains("vanilla") || input.contains("bar")) {
                settings.healthMode = CrityState.HealthMode.DEFAULT
            } else if (input.contains("on") || input.contains("hud") || input.contains("rpg") || input.contains("custom")) {
                settings.healthMode = CrityState.HealthMode.ON
            } else {
                settings.healthMode = when (settings.healthMode) {
                    CrityState.HealthMode.ON -> CrityState.HealthMode.DEFAULT
                    CrityState.HealthMode.DEFAULT -> CrityState.HealthMode.OFF
                    CrityState.HealthMode.OFF -> CrityState.HealthMode.ON
                }
            }

            ctx.sendMessage(Message.raw("[Crity] Health display: ${settings.healthMode.name}"))
            CrityState.saveConfig()
            return CompletableFuture.completedFuture(null)
        }
    }

    private class ModeOptionCommand(
        name: String,
        description: String,
        private val action: (CrityState.PlayerSettings) -> String
    ) : AbstractCommand(name, description) {
        init {
            requireNoPermission()
        }

        override fun execute(ctx: CommandContext): CompletableFuture<Void> {
            val settings = CrityState.getSettings(getPlayerUuid(ctx))
            val response = action(settings)
            CrityState.saveConfig()
            ctx.sendMessage(Message.raw(response))
            return CompletableFuture.completedFuture(null)
        }
    }
}
