package com.yetazero.crity.compat

import java.nio.file.Path
import java.util.jar.JarFile

internal data class ServerBuild(val version: String?, val revision: String?)

internal object ServerCompatibility {
    fun read(anchor: Class<*>): ServerBuild {
        val location = Path.of(anchor.protectionDomain.codeSource.location.toURI())
        return JarFile(location.toFile()).use { jar ->
            val attributes = jar.manifest?.mainAttributes
            ServerBuild(attributes?.getValue("Implementation-Version"), attributes?.getValue("Implementation-Revision-Id"))
        }
    }

    fun describe(build: ServerBuild?) = "Crity detected Hytale ${build?.version ?: "unknown"} " +
        "(${build?.revision ?: "unknown revision"}). Compatibility is checked per feature; version numbers do not disable Crity."

}

internal interface CritySession : AutoCloseable {
    fun setup()
    fun start()
}
