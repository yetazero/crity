package com.yetazero.crity.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.Value
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.CrityState
import com.yetazero.crity.compat.HytaleFeatures
import com.yetazero.crity.config.HudPosition
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.hud.CrityTargetHealthHud
import com.yetazero.crity.lifecycle.WeakSessions
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class CritySettingsPage(playerRef: PlayerRef, private val world: World) :
    InteractiveCustomUIPage<SettingsPageData>(playerRef, CustomPageLifetime.CanDismiss, SettingsPageData.CODEC) {
    private val draft = SettingsDraft(SettingsSnapshot(CrityState.getSettings(playerRef.uuid)))
    private var tab = SettingsTab.DAMAGE
    private var selectedRule = draft.current.visual.damage.rules.firstOrNull()?.id
    private var epoch = 0
    private var fullScreen = false
    private var dockTop = false
    private var moveStep = 8
    private var sample = DamageContext(42f, 0.9f)
    private var previousPreview: PreviewState? = null
    private var previewTask: ScheduledFuture<*>? = null
    private var closed = false
    private val invalid = mutableSetOf<String>()

    override fun build(ref: Ref<EntityStore>, commands: UICommandBuilder, events: UIEventBuilder, store: Store<EntityStore>) {
        previousPreview = null
        commands.append("Crity/Settings.ui")
        for ((selector, value) in listOf("DamageTab" to "DAMAGE", "RulesTab" to "RULES", "HudTab" to "HUD", "HighlightTab" to "HIGHLIGHT")) {
            SettingsPanelRenderer.action(events, "#$selector", "tab", value)
        }
        for (action in listOf("Save", "Close", "Reset", "Reload", "EditHud", "EditorBack", "RuleUp", "RuleDown", "RuleDelete", "Step", "Dock")) {
            SettingsPanelRenderer.action(events, "#$action", action)
        }
        for (direction in listOf("Left", "Right", "Up", "Down")) SettingsPanelRenderer.action(events, "#Move$direction", "move", direction)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleAdd",
            EventData.of("Action", "RuleAdd").append("@Text", "#NewRuleId.Value"), false)
        SettingsPanelRenderer.bind(events, "#RuleList", "selectRule", "", "@Text")
        SettingsPanelRenderer.bind(events, "#EditAnchor", "anchor", "", "@Text")
        SettingsPanelRenderer.bind(events, "#PortraitChoice", "portrait", "", "@Text")
        SettingsPanelRenderer.bind(events, "#SampleAmount", "sample", "amount", "@Number")
        SettingsPanelRenderer.bind(events, "#SampleRatio", "sample", "ratio", "@Number")
        SettingsPanelRenderer.bind(events, "#SampleCause", "sample", "cause", "@Text")
        SettingsPanelRenderer.bind(events, "#SampleWeapon", "sample", "weapon", "@Text")
        commands.set("#EditAnchor.Entries", SettingsPanelRenderer.entries(HudPosition.entries.map { it.name }))
        renderFields(commands, events)
        renderEditorFields(commands, events)
        previousPreview = SettingsPanelRenderer.preview(draft, sample, fullScreen, commands, previousPreview)
        active.put(playerRef.uuid, this)
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: String) {
        if (closed || data.length > 8192) return
        try {
            super.handleDataEvent(ref, store, data)
        } catch (e: IllegalArgumentException) {
            status(e.message ?: "Invalid input.")
        } catch (e: LinkageError) {
            fail(e)
        } catch (e: Exception) {
            fail(e)
        }
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: SettingsPageData) {
        if (closed || !ref.isValid || playerRef.reference != ref || store.externalData.world !== world) return
        if (data.epoch != null && data.epoch != epoch.toString()) return
        if (!HytaleFeatures.settings.isEnabled) { dismiss(); return }
        try {
            require((data.path?.length ?: 0) <= 160 && (data.text?.length ?: 0) <= 256) { "The value is too long." }
            when (data.action) {
                "field", "editorField" -> {
                    val path = checkNotNull(data.path)
                    draft.field(path, data.value())
                    invalid.remove(path)
                    syncField(path, draft.value(path), data.source)
                    changed()
                }
                "rule" -> {
                    val path = checkNotNull(data.path)
                    val keys = path.split('.', limit = 2)
                    require(keys.size == 2 && keys[0] == selectedRule) { "Select a rule first." }
                    draft.rule(keys[0], keys[1], data.value())
                    invalid.remove(path)
                    syncRuleField(keys[1], draft.ruleValue(keys[0], keys[1]), data.source)
                    changed()
                }
                "tab" -> {
                    tab = SettingsTab.valueOf(checkNotNull(data.path))
                    refreshFields()
                }
                "selectRule" -> {
                    require(draft.current.visual.damage.rules.any { it.id == data.text }) { "Unknown rule." }
                    selectedRule = data.text
                    refreshFields()
                }
                "RuleAdd" -> {
                    selectedRule = draft.addRule(data.text.orEmpty().trim())
                    refreshFields()
                    changed()
                }
                "RuleDelete" -> {
                    draft.removeRule(checkNotNull(selectedRule))
                    selectedRule = draft.current.visual.damage.rules.firstOrNull()?.id
                    refreshFields()
                    changed()
                }
                "RuleUp", "RuleDown" -> {
                    draft.moveRule(checkNotNull(selectedRule), if (data.action == "RuleUp") -1 else 1)
                    refreshFields()
                    changed()
                }
                "Reset" -> { draft.reset(CrityState.defaults); resetSelection(); refreshFields(); changed() }
                "Reload" -> {
                    draft.accept(SettingsSnapshot(CrityState.getSettings(playerRef.uuid)))
                    resetSelection()
                    refreshFields()
                    previewNow()
                    status("Saved settings loaded.")
                }
                "Save" -> save(ref, store)
                "Close" -> dismiss()
                "EditHud" -> editor(true)
                "EditorBack" -> editor(false)
                "anchor" -> {
                    val position = HudPosition.valueOf(data.value())
                    draft.field("hud.position", data.value())
                    draft.field("hud.x", (if (position in setOf(HudPosition.TOP_LEFT, HudPosition.LEFT, HudPosition.BOTTOM_LEFT,
                        HudPosition.TOP_RIGHT, HudPosition.RIGHT, HudPosition.BOTTOM_RIGHT)) EDGE_MARGIN else 0).toString())
                    draft.field("hud.y", (if (position in setOf(HudPosition.TOP_LEFT, HudPosition.TOP, HudPosition.TOP_RIGHT,
                        HudPosition.BOTTOM_LEFT, HudPosition.BOTTOM, HudPosition.BOTTOM_RIGHT)) EDGE_MARGIN else 0).toString())
                    refreshEditor()
                    changed()
                }
                "move" -> {
                    val hud = draft.current.visual.hud
                    val right = hud.position in setOf(HudPosition.TOP_RIGHT, HudPosition.RIGHT, HudPosition.BOTTOM_RIGHT)
                    val bottom = hud.position in setOf(HudPosition.BOTTOM_LEFT, HudPosition.BOTTOM, HudPosition.BOTTOM_RIGHT)
                    when (data.path) {
                        "Left", "Right" -> draft.field("hud.x", (hud.x + moveStep * (if (data.path == "Right") 1 else -1) * (if (right) -1 else 1)).coerceIn(-4096, 4096).toString())
                        "Up", "Down" -> draft.field("hud.y", (hud.y + moveStep * (if (data.path == "Down") 1 else -1) * (if (bottom) -1 else 1)).coerceIn(-4096, 4096).toString())
                    }
                    refreshEditor()
                    changed()
                }
                "Step" -> {
                    moveStep = when (moveStep) { 1 -> 8; 8 -> 32; else -> 1 }
                    sendUpdate(UICommandBuilder().set("#Step.Text", "Step: $moveStep"))
                }
                "Dock" -> {
                    dockTop = !dockTop
                    val anchor = Anchor().also {
                        it.setWidth(Value.of(710)); it.setHeight(Value.of(310))
                        if (dockTop) it.setTop(Value.of(35)) else it.setBottom(Value.of(35))
                    }
                    sendUpdate(UICommandBuilder().setObject("#MovePanel.Anchor", anchor))
                }
                "portrait" -> {
                    require(data.text in setOf("Trork_Warrior", "Kweebec_Sapling", "Kweebec_Rootling")) { "Unknown preview portrait." }
                    sendUpdate(UICommandBuilder().set("#Portrait.AssetPath", "Icons/ModelsGenerated/${data.text}.png"))
                }
                "sample" -> {
                    sample = when (data.path) {
                        "amount" -> sample.copy(amount = checkNotNull(data.number).toFloat().also { require(it.isFinite() && it in 0.1f..1000000f) })
                        "ratio" -> sample.copy(percent = checkNotNull(data.number).toFloat().also { require(it.isFinite() && it in 0f..1f) })
                        "cause" -> sample.copy(cause = data.text.orEmpty().take(128))
                        "weapon" -> sample.copy(weapon = data.text.orEmpty().take(128))
                        else -> sample
                    }
                    requestPreview()
                }
            }
        } catch (e: IllegalArgumentException) {
            if (data.action in setOf("field", "rule", "editorField")) invalid.add(data.path.orEmpty())
            status(e.message ?: "Invalid setting.")
        } catch (e: LinkageError) {
            fail(e)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun renderFields(commands: UICommandBuilder, events: UIEventBuilder) {
        epoch++
        invalid.clear()
        SettingsPanelRenderer.fields(draft, tab, selectedRule, epoch, commands, events)
    }

    private fun renderEditorFields(commands: UICommandBuilder, events: UIEventBuilder) {
        commands.clear("#EditFields")
        commands.set("#EditAnchor.Value", draft.current.visual.hud.position.name)
        listOf("hud.x", "hud.y").forEachIndexed { index, path ->
            SettingsPanelRenderer.row(commands, events, "#EditFields", index, SettingsFields.byPath.getValue(path), draft.value(path), "editorField", path)
        }
    }

    private fun refreshFields() {
        val commands = UICommandBuilder()
        val events = UIEventBuilder()
        renderFields(commands, events)
        commands.set("#Save.Disabled", !draft.dirty)
        commands.set("#Status.Text", if (draft.dirty) "Unsaved changes. Save to apply." else "Your saved settings.")
        sendUpdate(commands, events, false)
    }

    private fun refreshEditor() {
        val commands = UICommandBuilder()
        commands.set("#EditAnchor.Value", draft.current.visual.hud.position.name)
        listOf("hud.x", "hud.y").forEachIndexed { index, path ->
            commands.set("#EditFields[$index] #Input.Value", draft.value(path).toDouble())
            commands.set("#EditFields[$index] #Slider.Value", draft.value(path).toDouble())
        }
        sendUpdate(commands)
    }

    private fun syncField(path: String, value: String, source: String?) {
        val fields = when (tab) {
            SettingsTab.DAMAGE -> SettingsFields.modes + SettingsFields.damage
            SettingsTab.HUD -> SettingsFields.hud
            SettingsTab.HIGHLIGHT -> SettingsFields.highlight
            else -> emptyList()
        }
        val index = fields.indexOfFirst { it.path == path }
        if (index >= 0) syncControl("#Fields[$index]", fields[index], value, source)
        if (fullScreen && path in setOf("hud.x", "hud.y")) refreshEditor()
    }

    private fun syncRuleField(path: String, value: String, source: String?) {
        val index = SettingsFields.rule.indexOfFirst { it.path == path }
        if (index >= 0) syncControl("#Fields[$index]", SettingsFields.rule[index], value, source)
    }

    private fun syncControl(selector: String, field: SettingsField, value: String, source: String?) {
        val commands = UICommandBuilder()
        SettingsPanelRenderer.syncControl(commands, selector, field, value, source)
        if (commands.commands.isNotEmpty()) sendUpdate(commands)
    }

    private fun changed() {
        status(if (invalid.isEmpty()) "Unsaved changes - preview updated. Save to apply." else "Fix the invalid value before saving.")
        requestPreview()
    }

    private fun requestPreview() {
        if (closed || previewTask != null) return
        previewTask = world.scheduleAfter({
            previewTask = null
            if (isCurrent()) {
                try { previewNow() } catch (e: LinkageError) { fail(e) } catch (e: Exception) { fail(e) }
            } else release()
        }, 80L, TimeUnit.MILLISECONDS)
    }

    private fun previewNow() {
        previewTask?.cancel(false)
        previewTask = null
        val commands = UICommandBuilder()
        previousPreview = SettingsPanelRenderer.preview(draft, sample, fullScreen, commands, previousPreview)
        sendUpdate(commands)
    }

    private fun editor(show: Boolean) {
        fullScreen = show
        val commands = UICommandBuilder().set("#Window.Visible", !show).set("#Overlay.Visible", !show).set("#Editor.Visible", show)
        previousPreview = SettingsPanelRenderer.preview(draft, sample, fullScreen, commands, previousPreview)
        sendUpdate(commands)
        if (show) refreshEditor() else refreshFields()
    }

    private fun save(ref: Ref<EntityStore>, store: Store<EntityStore>) {
        require(invalid.isEmpty()) { "Fix the invalid value before saving." }
        if (!draft.dirty) { status("No changes to save."); return }
        draft.current.visual.validate()
        val saved = CrityState.updateSettings(playerRef.uuid) { settings ->
            require(SettingsSnapshot(settings) == draft.original) { "Settings changed elsewhere. Use Reload saved before saving this panel." }
            settings.visual = draft.current.visual
            settings.damageMode = draft.current.damage
            settings.healthMode = draft.current.health
            settings.debugDamage = draft.current.debug
        }
        draft.accept()
        HytaleFeatures.hud.run {
            val player = store.getComponent(ref, Player.getComponentType()) ?: return@run
            if (draft.current.health != CrityState.HealthMode.ON) player.hudManager.removeCustomHud(playerRef, CrityTargetHealthHud.KEY)
            else (player.hudManager.getCustomHud(CrityTargetHealthHud.KEY) as? CrityTargetHealthHud)?.refreshSettings()
        }
        status(if (saved) "Settings saved and applied." else "Applied for this session. Could not save to disk; see server log.")
    }

    private fun resetSelection() {
        selectedRule = draft.current.visual.damage.rules.firstOrNull()?.id
    }

    private fun status(text: String) {
        if (!closed) sendUpdate(UICommandBuilder().set("#Status.Text", text.take(200)).set("#EditStatus.Text", text.take(110))
            .set("#Save.Disabled", !draft.dirty || invalid.isNotEmpty()))
    }

    private fun isCurrent(): Boolean {
        val ref = playerRef.reference ?: return false
        return !closed && ref.isValid && ref.store.externalData.world === world &&
            ref.store.getComponent(ref, Player.getComponentType())?.pageManager?.customPage === this
    }

    private fun dismiss() {
        release()
        close()
    }

    private fun release() {
        closed = true
        previewTask?.cancel(false)
        previewTask = null
        invalid.clear()
        active.remove(playerRef.uuid, this)
    }

    private fun fail(error: Throwable) {
        HytaleFeatures.settings.disable(error)
        release()
        try {
            close()
            playerRef.sendMessage(Message.raw("[Crity] The settings panel is unavailable on this API. Other features can continue. Please contact the developer with the server log."))
        } catch (_: LinkageError) {
        } catch (_: Exception) {
        }
    }

    override fun onDismiss(ref: Ref<EntityStore>, store: Store<EntityStore>) {
        release()
    }

    companion object {
        private const val EDGE_MARGIN = 24
        private val active = WeakSessions<CritySettingsPage>()

        fun open(playerRef: PlayerRef, player: Player, ref: Ref<EntityStore>, store: Store<EntityStore>) {
            player.pageManager.openCustomPage(ref, store, CritySettingsPage(playerRef, store.externalData.world))
        }

        fun shutdownAll() {
            val pages = active.values()
            active.clear()
            for (page in pages) {
                page.previewTask?.cancel(false)
                if (page.world.isAlive) {
                    try { page.world.execute { if (page.isCurrent()) page.dismiss() else page.release() } }
                    catch (_: IllegalStateException) { page.release() }
                } else page.release()
            }
        }
    }
}
