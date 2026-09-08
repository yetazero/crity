package com.yetazero.crity.compat

import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.asset.AssetModule
import com.hypixel.hytale.assetstore.AssetPack
import com.hypixel.hytale.server.core.command.system.CommandRegistration
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.yetazero.crity.CrityState
import com.yetazero.crity.command.CrityCommand
import com.yetazero.crity.hud.CrityTargetHealthHud
import java.util.logging.Level
import java.util.logging.Logger

internal class CrityRuntime(private val plugin: JavaPlugin) : CritySession {
    private var integration: HytaleIntegration? = null
    private var command: CommandRegistration? = null
    private var ownsPack = false
    private var ownsTick = false
    private var ownsReticleTick = false
    private var started = false
    private val packId = "yetazero:Crity"

    override fun setup() {
        val assetsReady = HytaleFeatures.assets.run {
            val assets = AssetModule.get()
            check(assets.getAssetPack(packId) == null) { "Crity asset pack is already registered." }
            ownsPack = assets.registerPack(packId, checkNotNull(plugin.file), plugin.manifest, AssetPack.PackSource.RUNTIME)
            check(ownsPack) { "Could not register Crity assets." }
            true
        } ?: false
        if (!assetsReady) {
            val reason = IllegalStateException("Crity assets are unavailable.")
            HytaleFeatures.settings.disable(reason)
            HytaleFeatures.highlight.disable(reason)
        }
    }

    override fun start() {
        CrityState.loadConfig()
        started = true
        HytaleFeatures.settings.run { ApiCapabilities.settingsPanel(); ApiCapabilities.nativeUiAssets() }
        HytaleFeatures.highlight.run { NativeTargetHighlight.validateAssets() }
        HytaleFeatures.hitboxes.run {
            plugin.entityStoreRegistry.registerSystem(CrityHitboxSystem())
            ownsTick = true
        }
        HytaleFeatures.commands.run { command = plugin.commandRegistry.registerCommand(CrityCommand()) }
        HytaleFeatures.reticle.run {
            plugin.entityStoreRegistry.registerSystem(CrityReticleSystem())
            ownsReticleTick = true
        }
        HytaleFeatures.combat.run {
            val adapter = HytaleIntegration()
            integration = adapter
            adapter.install()
        }
        Logger.getLogger("Crity").info("Crity v${plugin.manifest.version} initialized. " +
            "Combat: ${HytaleFeatures.combat.isEnabled}; settings: ${HytaleFeatures.settings.isEnabled}; " +
            "highlight: ${HytaleFeatures.highlight.isEnabled}. Settings schema 2.")
    }

    override fun close() {
        val actions = listOf<() -> Unit>(
            { command?.unregister(); command = null },
            { com.yetazero.crity.ui.CritySettingsPage.shutdownAll() },
            { NativeTargetHighlight.shutdownAll() },
            { CrityTargetHealthHud.shutdownAll() },
            { com.yetazero.crity.hud.CrityReticleHud.shutdownAll() },
            {
                val registry = EntityStore.REGISTRY
                if (ownsReticleTick && !registry.isShutdown && registry.hasSystemClass(CrityReticleSystem::class.java)) {
                    registry.unregisterSystem(CrityReticleSystem::class.java)
                }
                ownsReticleTick = false
                com.yetazero.crity.display.ReticleLayout.clear()
            },
            { integration?.close(); integration = null },
            {
                val registry = EntityStore.REGISTRY
                if (ownsTick && !registry.isShutdown && registry.hasSystemClass(CrityHitboxSystem::class.java)) {
                    registry.unregisterSystem(CrityHitboxSystem::class.java)
                }
                ownsTick = false
            },
            { if (started) CrityState.saveConfig(); started = false },
            { if (ownsPack && !HytaleServer.get().isShuttingDown) AssetModule.get().unregisterPack(packId); ownsPack = false }
        )
        for (action in actions) {
            try {
                action()
            } catch (e: LinkageError) {
                Logger.getLogger("Crity").log(Level.WARNING, "Crity cleanup step failed.", e)
            } catch (e: Exception) {
                Logger.getLogger("Crity").log(Level.WARNING, "Crity cleanup step failed.", e)
            }
        }
    }
}
