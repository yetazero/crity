package com.yetazero.crity

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.yetazero.crity.compat.CrityDiagnostics
import com.yetazero.crity.compat.CritySession
import com.yetazero.crity.compat.ServerCompatibility
import java.util.logging.Level
import java.util.logging.Logger

class CrityPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    private val log = Logger.getLogger("Crity")
    private var session: CritySession? = null

    override fun setup() {
        guarded {
            CrityDiagnostics.version = manifest.version.toString()
            val build = try { ServerCompatibility.read(JavaPlugin::class.java) } catch (_: Exception) { null }
            CrityDiagnostics.serverBuild = build
            log.info(ServerCompatibility.describe(build))
            val runtime = Class.forName("com.yetazero.crity.compat.CrityRuntime", true, javaClass.classLoader)
                .getConstructor(JavaPlugin::class.java).newInstance(this) as CritySession
            session = runtime
            runtime.setup()
        }
    }

    override fun start() {
        guarded { session?.start() }
    }

    override fun shutdown() {
        closeSession()
    }

    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            failed(error)
        }
    }

    private fun failed(error: Throwable) {
        CrityDiagnostics.recordBootstrapFailure(error)
        log.log(Level.WARNING, "[Crity][diagnostic] bootstrap FAILED: ${error.javaClass.name}: ${error.message}. " +
            "Crity is shutting down its own integration; the world and every other mod are unaffected. " +
            "Please install a compatible Crity update, or paste this line with the stack trace below when reporting the issue.", error)
        closeSession()
    }

    private fun closeSession() {
        val previous = session ?: return
        session = null
        try {
            previous.close()
        } catch (e: LinkageError) {
            log.log(Level.WARNING, "Crity integration cleanup failed.", e)
        } catch (e: Exception) {
            log.log(Level.WARNING, "Crity integration cleanup failed.", e)
        }
    }
}
