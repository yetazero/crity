package com.yetazero.crity.compat

internal interface SystemRegistryAccess {
    fun hasStock(): Boolean
    fun hasCustom(): Boolean
    fun addCustom()
    fun removeCustom()
    fun removeStock()
    fun restoreStock()
}

internal class SystemReplacement(private val registry: SystemRegistryAccess) : AutoCloseable {
    private var owned = false

    fun install() {
        check(!owned && registry.hasStock() && !registry.hasCustom()) {
            "Combat UI registry is already modified or incompatible; Crity will leave it unchanged."
        }
        owned = true
        try {
            registry.addCustom()
            check(registry.hasCustom()) { "Crity damage system was not registered." }
            registry.removeStock()
            check(!registry.hasStock()) { "Stock damage UI system was not removed." }
        } catch (e: Throwable) {
            try {
                close()
            } catch (rollback: Throwable) {
                e.addSuppressed(rollback)
            }
            throw e
        }
    }

    override fun close() {
        if (!owned) return
        if (registry.hasCustom()) registry.removeCustom()
        check(!registry.hasCustom()) { "Cannot restore stock UI while Crity is still registered." }
        if (!registry.hasStock()) registry.restoreStock()
        check(registry.hasStock()) { "Failed to restore stock damage UI." }
        owned = false
    }
}
