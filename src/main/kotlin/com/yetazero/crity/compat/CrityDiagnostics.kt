package com.yetazero.crity.compat

internal object CrityDiagnostics {
    @Volatile var version: String = "unknown"
    @Volatile var serverBuild: ServerBuild? = null
    @Volatile private var bootstrapError: Throwable? = null

    fun recordBootstrapFailure(error: Throwable) {
        bootstrapError = error
    }

    fun report(): List<String> = buildList {
        add("Crity $version - Hytale ${serverBuild?.version ?: "unknown version"} (${serverBuild?.revision ?: "unknown revision"})")
        val bootstrap = bootstrapError
        if (bootstrap != null) {
            add("bootstrap: FAILED - Crity did not start at all (${bootstrap.javaClass.name}: ${bootstrap.message}). The world and other mods started normally.")
        } else {
            for (gate in HytaleFeatures.all) add(gate.status())
        }
    }
}
