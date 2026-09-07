package com.yetazero.crity

import com.yetazero.crity.command.CrityCommand
import com.yetazero.crity.hud.CrityTargetHealthHud
import com.hypixel.hytale.component.ComponentRegistry
import com.hypixel.hytale.component.system.ISystem
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.util.logging.Level
import java.util.logging.Logger

class CrityPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        private val LOGGER = Logger.getLogger("Crity")
    }

    override fun setup() {
        CrityState.loadConfig()
        entityStoreRegistry.registerSystem(CrityDamageSystem())
        commandRegistry.registerCommand(CrityCommand())
    }

    override fun start() {
        try {
            val regField = entityStoreRegistry.javaClass.getDeclaredField("registry")
            regField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val reg = regField.get(entityStoreRegistry) as ComponentRegistry<Any>

            @Suppress("UNCHECKED_CAST")
            val stockSystemClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems\$EntityUIEvents"
            ) as Class<out ISystem<Any>>

            reg.unregisterSystem(stockSystemClass)
        } catch (t: Throwable) {
            LOGGER.log(Level.WARNING, "EntityUIEvents unregister note: ${t.message}")
        }

        LOGGER.log(Level.INFO, "Crity plugin v2.9.1 (Kotlin) initialized.")
    }

    override fun shutdown() {
        CrityState.saveConfig()
        CrityTargetHealthHud.shutdownExecutor()
        super.shutdown()
    }
}
