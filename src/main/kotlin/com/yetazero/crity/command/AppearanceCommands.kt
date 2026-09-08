package com.yetazero.crity.command

import com.yetazero.crity.CrityState
import com.yetazero.crity.config.CrityExports
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
import com.yetazero.crity.config.VisualSettingsEditor
import com.yetazero.crity.config.normalizeColor
import com.yetazero.crity.compat.CrityDiagnostics
import com.yetazero.crity.compat.HytaleFeatures
import com.yetazero.crity.config.TargetDisplayMode
import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.concurrent.CompletableFuture

internal object AppearanceCommands {
    fun register(parent: AbstractCommand) {
        parent.addSubCommand(object : AbstractCommand("set", "Set any combat appearance setting") {
            private val path = withRequiredArg("setting", "For example hud.position or damage.decimals", ArgTypes.STRING)
            private val value = withRequiredArg("value", "New value; text can include spaces", ArgTypes.GREEDY_STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) {
                VisualSettingsEditor.set(it, ctx.get(path), ctx.get(value))
            }
        })
        parent.addSubCommand(object : AbstractCommand("color", "Set a damage rule color") {
            private val rule = withRequiredArg("rule", "low, medium, high, critical, or your rule id", ArgTypes.STRING)
            private val color = withRequiredArg("color", "RRGGBB or #RRGGBB", ArgTypes.STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) {
                if (ctx.get(rule) == "all") {
                    val rgb = normalizeColor(ctx.get(color))
                    it.copy(damage = it.damage.copy(color = rgb, rules = it.damage.rules.map { rule -> rule.copy(color = rgb) }))
                } else VisualSettingsEditor.rule(it, ctx.get(rule), "color", ctx.get(color))
            }
        })
        parent.addSubCommand(object : AbstractCommand("rule", "Edit a damage rule; first matching rule wins") {
            private val id = withRequiredArg("id", "Rule id", ArgTypes.STRING)
            private val field = withRequiredArg("field", "enabled, color, minPercent, minAmount, maxAmount, cause, weaponPrefix, format", ArgTypes.STRING)
            private val value = withRequiredArg("value", "New value; use - to clear cause or weaponPrefix", ArgTypes.GREEDY_STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) {
                VisualSettingsEditor.rule(it, ctx.get(id), ctx.get(field), ctx.get(value))
            }
        })
        for (add in listOf(true, false)) parent.addSubCommand(object : AbstractCommand(
            if (add) "rule-add" else "rule-remove", if (add) "Insert a rule at the start" else "Delete a rule") {
            private val id = withRequiredArg("id", "Rule id", ArgTypes.STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) {
                if (add) VisualSettingsEditor.addRule(it, ctx.get(id)) else VisualSettingsEditor.removeRule(it, ctx.get(id))
            }
        })
        parent.addSubCommand(object : AbstractCommand("rule-move", "Change rule priority") {
            private val id = withRequiredArg("id", "Rule id", ArgTypes.STRING)
            private val position = withRequiredArg("position", "1 is highest priority", ArgTypes.INTEGER)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) {
                VisualSettingsEditor.moveRule(it, ctx.get(id), ctx.get(position))
            }
        })
        parent.addSubCommand(object : AbstractCommand("settings", "List all of your appearance settings") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> {
                val ref = player(ctx) ?: return CompletableFuture.completedFuture(null)
                val json = VisualSettingsCodec.encode(CrityState.getSettings(ref.uuid).visual)
                fun listSettings(value: com.google.gson.JsonObject, prefix: String = "") {
                    for ((key, field) in value.entrySet()) {
                        val path = if (prefix.isEmpty()) key else "$prefix.$key"
                        if (field.isJsonPrimitive) ctx.sendMessage(Message.raw("$path = ${field.asString}"))
                        else if (field.isJsonObject) listSettings(field.asJsonObject, path)
                    }
                }
                listSettings(json)
                CrityState.getSettings(ref.uuid).visual.damage.rules.forEachIndexed { index, rule ->
                    ctx.sendMessage(Message.raw("Rule ${index + 1}: ${rule.id} enabled=${rule.enabled} ${rule.color}, minPercent=${rule.minPercent}, amount=${rule.minAmount}..${rule.maxAmount}, cause=${rule.cause}, weapon=${rule.weaponPrefix}, text=${rule.format}"))
                }
                return CompletableFuture.completedFuture(null)
            }
        })
        parent.addSubCommand(object : AbstractCommand("reset", "Reset your appearance to server defaults") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) { CrityState.defaults }
        })
        parent.addSubCommand(object : AbstractCommand("export", "Save your current appearance under a shared name") {
            private val name = withRequiredArg("name", "Letters, digits, _ or -; shared server-wide, so pick something distinctive", ArgTypes.STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> = withPlayer(ctx) { ref, _ ->
                CrityExports.export(ctx.get(name), CrityState.getSettings(ref.uuid).visual)
                ctx.sendMessage(Message.raw("[Crity] Exported your appearance as '${ctx.get(name)}'. /crity import ${ctx.get(name)} applies it anywhere."))
            }
        })
        parent.addSubCommand(object : AbstractCommand("import", "Replace your appearance with a previously exported one") {
            private val name = withRequiredArg("name", "An existing export name; see /crity exports", ArgTypes.STRING)
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext) = change(ctx) { CrityExports.import(ctx.get(name), CrityState.defaults) }
        })
        parent.addSubCommand(object : AbstractCommand("exports", "List available exported appearances") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> {
                val names = CrityExports.list()
                ctx.sendMessage(Message.raw(if (names.isEmpty()) "[Crity] No exports yet. /crity export <name> creates one."
                    else "[Crity] Exports: ${names.joinToString(", ")}"))
                return CompletableFuture.completedFuture(null)
            }
        })
        parent.addSubCommand(object : AbstractCommand("diagnostics", "Report which Crity features are active, for bug reports") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> {
                for (line in CrityDiagnostics.report()) ctx.sendMessage(Message.raw(line))
                return CompletableFuture.completedFuture(null)
            }
        })
        parent.addSubCommand(object : AbstractCommand("preview", "Show a sample target health HUD") {
            init { requireNoPermission() }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> = withPlayer(ctx) { ref, player ->
                if (CrityState.getSettings(ref.uuid).healthMode != CrityState.HealthMode.ON) {
                    ctx.sendMessage(Message.raw("[Crity] Enable the custom HUD first: /crity health on"))
                } else {
                    val hud = CrityTargetHealthHud.getOrCreate(ref, player, ref.reference!!.store.externalData.world)
                    hud.refreshSettings()
                    hud.preview()
                }
            }
        })
        parent.addSubCommand(object : AbstractCommand("reload", "Reload server defaults and player settings from disk") {
            init { requirePermission("crity.admin") }
            override fun execute(ctx: CommandContext): CompletableFuture<Void> {
                val loaded = CrityState.loadConfig()
                ctx.sendMessage(Message.raw(if (loaded) "[Crity] Config reloaded. Active HUDs refresh on their next tick."
                    else "[Crity] Reload failed; file preserved. See server log."))
                return CompletableFuture.completedFuture(null)
            }
        })
    }

    private fun change(ctx: CommandContext, transform: (VisualSettings) -> VisualSettings): CompletableFuture<Void> = withPlayer(ctx) { ref, player ->
        val saved = CrityState.updateSettings(ref.uuid) { settings ->
            settings.visual = transform(settings.visual).also {
                it.validate()
                require(!it.hitboxes.enabled || (if (it.hitboxes.mode == TargetDisplayMode.GLOW) HytaleFeatures.highlight.isEnabled else HytaleFeatures.hitboxes.isEnabled)) { "Target display is unavailable on this server. See server log." }
            }
        }
        (player.hudManager.getCustomHud(CrityTargetHealthHud.KEY) as? CrityTargetHealthHud)?.refreshSettings()
        ctx.sendMessage(Message.raw(if (saved) "[Crity] Appearance saved. /crity preview to check the HUD."
            else "[Crity] Applied for this session, but could not save. See server log."))
    }

    private fun withPlayer(ctx: CommandContext, action: (PlayerRef, Player) -> Unit): CompletableFuture<Void> {
        val playerRef = player(ctx) ?: return CompletableFuture.completedFuture(null)
        val ref = playerRef.reference
        if (ref == null || !ref.isValid) return CompletableFuture.completedFuture(null)
        val store = ref.store
        return CompletableFuture.runAsync({
            if (!ref.isValid || playerRef.reference != ref) return@runAsync
            val player = store.getComponent(ref, Player.getComponentType()) ?: return@runAsync
            try {
                action(playerRef, player)
            } catch (e: IllegalArgumentException) {
                ctx.sendMessage(Message.raw("[Crity] ${e.message}"))
            } catch (e: LinkageError) {
                java.util.logging.Logger.getLogger("Crity").log(java.util.logging.Level.WARNING, "Crity command API mismatch", e)
                ctx.sendMessage(Message.raw("[Crity] This display feature is incompatible with the current server API. See server log."))
            }
        }, store.externalData.world)
    }

    private fun player(ctx: CommandContext): PlayerRef? {
        if (ctx.isPlayer) return ctx.senderAs(PlayerRef::class.java)
        ctx.sendMessage(Message.raw("[Crity] This command is available to players only."))
        return null
    }
}
