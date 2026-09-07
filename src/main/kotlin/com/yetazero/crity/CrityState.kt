package com.yetazero.crity

import java.io.File
import java.util.Scanner
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object CrityState {

    private val LOGGER = Logger.getLogger("Crity")

    enum class DamageMode { ON, DEFAULT, OFF }
    enum class HealthMode { ON, DEFAULT, OFF }

    data class PlayerSettings(
        var damageMode: DamageMode = DamageMode.ON,
        var healthMode: HealthMode = HealthMode.ON
    )

    private val playerSettings = ConcurrentHashMap<UUID, PlayerSettings>()
    private val configFile = File("crity_players.json")

    fun getSettings(uuid: UUID?): PlayerSettings {
        if (uuid == null) return PlayerSettings()
        return playerSettings.computeIfAbsent(uuid) { PlayerSettings() }
    }

    fun removeSettings(uuid: UUID?) {
        if (uuid != null) playerSettings.remove(uuid)
    }

    fun trackedPlayerCount(): Int = playerSettings.size

    @Synchronized
    fun saveConfig() {
        try {
            configFile.bufferedWriter().use { writer ->
                writer.append("{\n")
                val entries = playerSettings.entries.toList()
                entries.forEachIndexed { i, entry ->
                    val (uuid, settings) = entry
                    writer.append("  \"").append(uuid.toString()).append("\": {")
                        .append("\"damage\": \"").append(settings.damageMode.name).append("\", ")
                        .append("\"health\": \"").append(settings.healthMode.name).append("\"}")
                    if (i != entries.lastIndex) writer.append(",")
                    writer.append("\n")
                }
                writer.append("}\n")
            }
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Failed to save Crity config", e)
        }
    }

    @Synchronized
    fun loadConfig() {
        if (!configFile.exists()) return
        try {
            Scanner(configFile).use { scanner ->
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine().trim()
                    if (!line.startsWith("\"") || !line.contains(": {")) continue

                    val uuidEnd = line.indexOf("\"", 1)
                    if (uuidEnd <= 1) continue
                    val uuidStr = line.substring(1, uuidEnd)
                    val uuid = try {
                        UUID.fromString(uuidStr)
                    } catch (e: IllegalArgumentException) {
                        continue
                    }
                    val settings = getSettings(uuid)

                    extractField(line, "damage")?.let { value ->
                        try {
                            settings.damageMode = DamageMode.valueOf(value)
                        } catch (e: IllegalArgumentException) {}
                    }
                    extractField(line, "health")?.let { value ->
                        try {
                            settings.healthMode = HealthMode.valueOf(value)
                        } catch (e: IllegalArgumentException) {}
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Failed to load Crity config", e)
        }
    }

    private fun extractField(line: String, field: String): String? {
        val marker = "\"$field\": \""
        val start = line.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val valueEnd = line.indexOf("\"", valueStart)
        if (valueEnd <= valueStart) return null
        return line.substring(valueStart, valueEnd)
    }

    const val UI_CYAN = "CrityCyan"
    const val UI_GREEN = "CrityGreen"
    const val UI_YELLOW = "CrityYellow"
    const val UI_RED = "CrityRed"
    const val UI_HEALTHBAR = "Healthbar"
    const val UI_COMBAT_TEXT = "CombatText"
}
