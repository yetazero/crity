package com.yetazero.crity

import com.yetazero.crity.command.CrityCommand
import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.logging.Level
import java.util.logging.Logger

class CrityPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        private val LOGGER = Logger.getLogger("Crity")
    }

    private var stockSystem: DamageSystems.EntityUIEvents? = null

    override fun setup() {
        CrityState.loadConfig()
        commandRegistry.registerCommand(CrityCommand())
    }

    override fun start() {
        val registry = EntityStore.REGISTRY
        val stockClass = DamageSystems.EntityUIEvents::class.java
        check(registry.hasSystemClass(stockClass)) {
            "Crity cannot replace EntityUIEvents: the stock system is not registered. Check other combat UI mods."
        }
        val original = DamageSystems.EntityUIEvents()
        registry.unregisterSystem(stockClass)
        check(!registry.hasSystemClass(stockClass)) { "Crity failed to unregister EntityUIEvents" }
        stockSystem = original
        try {
            entityStoreRegistry.registerSystem(CrityDamageSystem())
        } catch (t: Throwable) {
            registry.registerSystem(original)
            stockSystem = null
            throw t
        }
        LOGGER.log(Level.INFO, "Crity plugin v${manifest.version} (Kotlin) initialized. EntityUIEvents disabled (verified); one text packet per hit.")
    }

    override fun shutdown() {
        CrityState.saveConfig()
        CrityTargetHealthHud.shutdownAll()
        val registry = EntityStore.REGISTRY
        stockSystem?.let { original ->
            if (!registry.isShutdown) {
                if (registry.hasSystemClass(CrityDamageSystem::class.java)) {
                    registry.unregisterSystem(CrityDamageSystem::class.java)
                }
                if (!registry.hasSystemClass(DamageSystems.EntityUIEvents::class.java)) {
                    registry.registerSystem(original)
                }
            }
        }
        stockSystem = null
        super.shutdown()
    }
}
