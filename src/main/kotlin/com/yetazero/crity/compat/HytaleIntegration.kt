package com.yetazero.crity.compat

import com.yetazero.crity.CrityDamageSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

internal class HytaleIntegration : AutoCloseable {
    private val registry = EntityStore.REGISTRY
    private val stock = DamageSystems.EntityUIEvents()
    private val system = CrityDamageSystem(stock)
    private val replacement = SystemReplacement(object : SystemRegistryAccess {
        override fun hasStock() = registry.hasSystemClass(DamageSystems.EntityUIEvents::class.java)
        override fun hasCustom() = registry.hasSystemClass(CrityDamageSystem::class.java)
        override fun addCustom() = registry.registerSystem(system)
        override fun removeCustom() = registry.unregisterSystem(CrityDamageSystem::class.java)
        override fun removeStock() = registry.unregisterSystem(DamageSystems.EntityUIEvents::class.java)
        override fun restoreStock() = registry.registerSystem(stock)
    })

    fun install() = replacement.install()

    override fun close() {
        if (!registry.isShutdown) replacement.close()
    }
}
