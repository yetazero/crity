package com.yetazero.crity.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

object CrityExports {
    internal var directory: Path = Path.of("crity_exports")
    private val namePattern = Regex("[A-Za-z0-9_-]{1,32}")

    fun validateName(name: String): String {
        require(name.matches(namePattern)) { "Export name must be 1-32 letters, digits, _ or -." }
        return name
    }

    fun export(name: String, settings: VisualSettings) {
        validateName(name)
        settings.validate()
        Files.createDirectories(directory)
        val target = directory.resolve("$name.json")
        val temporary = Files.createTempFile(directory, "crity_export-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { GsonBuilder().setPrettyPrinting().create().toJson(VisualSettingsCodec.encode(settings), it) }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun import(name: String, defaults: VisualSettings): VisualSettings {
        validateName(name)
        val target = directory.resolve("$name.json")
        require(Files.exists(target)) { "No export named '$name'. See /crity exports for the list, or /crity export $name to create it." }
        val json = try {
            Files.newBufferedReader(target).use { JsonParser.parseReader(it).asJsonObject }
        } catch (e: Exception) {
            throw IllegalArgumentException("Export '$name' is not valid JSON: ${e.message}", e)
        }
        return VisualSettingsCodec.decode(json, defaults)
    }

    fun list(): List<String> {
        if (!Files.isDirectory(directory)) return emptyList()
        Files.newDirectoryStream(directory, "*.json").use { entries ->
            return entries.map { it.fileName.toString().removeSuffix(".json") }.sorted()
        }
    }
}
