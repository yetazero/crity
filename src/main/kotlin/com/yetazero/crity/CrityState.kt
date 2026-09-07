package com.yetazero.crity

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yetazero.crity.config.VisualSettings
import com.yetazero.crity.config.VisualSettingsCodec
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
    @Volatile var defaults = VisualSettings()
        private set
    @Volatile private var writable = true

    enum class DamageMode { ON, DEFAULT, OFF }
    enum class HealthMode { ON, DEFAULT, OFF }

    data class PlayerSettings(
        @Volatile var damageMode: DamageMode = DamageMode.ON,
        @Volatile var healthMode: HealthMode = HealthMode.ON,
        @Volatile var debugDamage: Boolean = false,
        @Volatile var visual: VisualSettings = VisualSettings()
    )

    fun getSettings(uuid: UUID): PlayerSettings = playerSettings.computeIfAbsent(uuid) { PlayerSettings(visual = defaults) }

    @Synchronized
    fun updateSettings(uuid: UUID, persist: Boolean = true, action: (PlayerSettings) -> Unit): Boolean {
        action(getSettings(uuid))
        return !persist || saveConfig()
    }

    @Synchronized
    fun saveConfig(): Boolean {
        if (!writable) return false
        try {
            config.save(playerSettings, defaults)
            return true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to save Crity config", e)
            return false
        }
    }

    @Synchronized
    fun loadConfig(): Boolean {
        try {
            val loaded = config.loadDocument()
            defaults = loaded.defaults
            playerSettings.clear()
            playerSettings.putAll(loaded.players)
            writable = true
            return true
        } catch (e: Exception) {
            writable = false
            logger.log(Level.WARNING, "Failed to load Crity config; existing settings retained and saving disabled to protect the file. Fix it and use /crity reload.", e)
            return false
        }
    }
}

internal class CrityConfigStore(private val path: Path) {
    data class Document(val defaults: VisualSettings, val players: Map<UUID, CrityState.PlayerSettings>)

    fun load(): Map<UUID, CrityState.PlayerSettings> = loadDocument().players

    fun loadDocument(): Document {
        if (!Files.exists(path)) return Document(VisualSettings(), emptyMap())
        val root = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        val version = root.get("schemaVersion")
        if (version != null) require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asDouble == 2.0) { "Unsupported Crity config schemaVersion; expected 2." }
        val defaults = root.get("defaults")?.let { VisualSettingsCodec.decode(it.asJsonObject) } ?: VisualSettings()
        val entries = if (version == null) root else root.getAsJsonObject("players") ?: JsonObject()
        val players = buildMap {
            for ((key, value) in entries.entrySet()) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                if (!value.isJsonObject) continue
                val entry = value.asJsonObject
                val damage = runCatching { CrityState.DamageMode.valueOf(entry["damage"].asString) }
                    .getOrDefault(CrityState.DamageMode.ON)
                val health = runCatching { CrityState.HealthMode.valueOf(entry["health"].asString) }
                    .getOrDefault(CrityState.HealthMode.ON)
                val visual = entry.get("visual")?.let { VisualSettingsCodec.decode(it.asJsonObject, defaults) } ?: defaults
                put(uuid, CrityState.PlayerSettings(damage, health, visual = visual))
            }
        }
        return Document(defaults, players)
    }

    fun save(settings: Map<UUID, CrityState.PlayerSettings>, defaults: VisualSettings = VisualSettings()) {
        defaults.validate()
        val root = JsonObject()
        root.addProperty("schemaVersion", 2)
        root.add("defaults", VisualSettingsCodec.encode(defaults))
        val players = JsonObject()
        for ((uuid, value) in settings.entries.sortedBy { it.key.toString() }) {
            value.visual.validate()
            val entry = JsonObject()
            entry.addProperty("damage", value.damageMode.name)
            entry.addProperty("health", value.healthMode.name)
            entry.add("visual", VisualSettingsCodec.encode(value.visual))
            players.add(uuid.toString(), entry)
        }
        root.add("players", players)
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val legacyBackup = target.resolveSibling("${target.fileName}.v1.bak")
        if (Files.exists(target) && !Files.exists(legacyBackup)) {
            val old = Files.newBufferedReader(target).use { JsonParser.parseReader(it).asJsonObject }
            if (!old.has("schemaVersion")) Files.copy(target, legacyBackup)
        }
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
