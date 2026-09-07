package com.yetazero.crity

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object CrityState {
    private val logger = Logger.getLogger("Crity")
    private val playerSettings = ConcurrentHashMap<UUID, PlayerSettings>()
    private val config = CrityConfigStore(Path.of("crity_players.json"))

    enum class DamageMode { ON, DEFAULT, OFF }
    enum class HealthMode { ON, DEFAULT, OFF }

    data class PlayerSettings(
        @Volatile var damageMode: DamageMode = DamageMode.ON,
        @Volatile var healthMode: HealthMode = HealthMode.ON,
        @Volatile var debugDamage: Boolean = false
    )

    fun getSettings(uuid: UUID): PlayerSettings = playerSettings.computeIfAbsent(uuid) { PlayerSettings() }

    @Synchronized
    fun saveConfig() {
        try {
            config.save(playerSettings)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to save Crity config", e)
        }
    }

    @Synchronized
    fun loadConfig() {
        try {
            val loaded = config.load()
            playerSettings.clear()
            playerSettings.putAll(loaded)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load Crity config", e)
        }
    }
}

internal class CrityConfigStore(private val path: Path) {
    fun load(): Map<UUID, CrityState.PlayerSettings> {
        if (!Files.exists(path)) return emptyMap()
        val root = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        return buildMap {
            for ((key, value) in root.entrySet()) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                if (!value.isJsonObject) continue
                val entry = value.asJsonObject
                val damage = runCatching { CrityState.DamageMode.valueOf(entry["damage"].asString) }
                    .getOrDefault(CrityState.DamageMode.ON)
                val health = runCatching { CrityState.HealthMode.valueOf(entry["health"].asString) }
                    .getOrDefault(CrityState.HealthMode.ON)
                put(uuid, CrityState.PlayerSettings(damage, health))
            }
        }
    }

    fun save(settings: Map<UUID, CrityState.PlayerSettings>) {
        val root = JsonObject()
        for ((uuid, value) in settings.entries.sortedBy { it.key.toString() }) {
            val entry = JsonObject()
            entry.addProperty("damage", value.damageMode.name)
            entry.addProperty("health", value.healthMode.name)
            root.add(uuid.toString(), entry)
        }
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "crity_players-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { GsonBuilder().setPrettyPrinting().create().toJson(root, it) }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
