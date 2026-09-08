package com.yetazero.crity.compat

import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

internal object HytaleFeatures {
    val commands = FeatureGate("commands")
    val assets = FeatureGate("custom assets")
    val combat = FeatureGate("custom combat display")
    val settings = FeatureGate("settings panel")
    val highlight = FeatureGate("native target highlight")
    val hud = FeatureGate("target health HUD")
    val hitboxes = FeatureGate("diagnostic bounds display")
    val reticle = FeatureGate("custom reticle")
    val reticleWeapons = FeatureGate("reticle weapon selection")

    val all: List<FeatureGate> get() = listOf(commands, assets, combat, settings, highlight, hud, hitboxes, reticle, reticleWeapons)
}

internal class FeatureGate(private val name: String) {
    private val enabled = AtomicBoolean(true)
    @Volatile private var lastError: Throwable? = null

    val isEnabled: Boolean get() = enabled.get()

    fun <T> run(action: () -> T): T? {
        if (!enabled.get()) return null
        return try {
            action()
        } catch (e: LinkageError) {
            disable(e)
            null
        } catch (e: Exception) {
            disable(e)
            null
        }
    }

    fun disable(error: Throwable) {
        lastError = error
        if (enabled.getAndSet(false)) Logger.getLogger("Crity").log(Level.WARNING,
            "[Crity][diagnostic] module=\"$name\" disabled after an integration failure: " +
                "${error.javaClass.name}: ${error.message}. Other Crity features, other mods and the world are unaffected. " +
                "Run /crity diagnostics for a full report, or paste this line with the stack trace below when reporting an issue for a game update.", error)
    }

    fun status(): String {
        val error = lastError
        return if (enabled.get()) "$name: OK"
            else "$name: DISABLED (${error?.javaClass?.name ?: "unknown"}: ${error?.message ?: "no message"})"
    }
}
