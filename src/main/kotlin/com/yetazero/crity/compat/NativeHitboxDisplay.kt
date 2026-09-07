package com.yetazero.crity.compat

import com.hypixel.hytale.math.matrix.Matrix4dUtil
import com.hypixel.hytale.math.shape.Box
import com.hypixel.hytale.protocol.DebugFlags
import com.hypixel.hytale.protocol.DebugShape
import com.hypixel.hytale.protocol.packets.player.DisplayDebug
import com.yetazero.crity.config.HitboxAppearance
import org.joml.Matrix4d
import org.joml.Vector3dc
import org.joml.Vector3f

internal object NativeHitboxDisplay {
    fun packet(position: Vector3dc, bounds: Box, settings: HitboxAppearance, dt: Float): DisplayDebug? {
        val width = bounds.width()
        val height = bounds.height()
        val depth = bounds.depth()
        val x = position.x() + bounds.middleX()
        val y = position.y() + bounds.middleY()
        val z = position.z() + bounds.middleZ()
        if (!listOf(width, height, depth, x, y, z).all { it.isFinite() }) return null
        if (width <= 0 || height <= 0 || depth <= 0) return null
        val matrix = Matrix4dUtil.asFloatData(Matrix4d().translation(x, y, z).scale(width, height, depth))
        if (matrix.any { !it.isFinite() }) return null
        val rgb = settings.color.removePrefix("#").toInt(16)
        val color = Vector3f(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f)
        val lifetime = if (dt.isFinite() && dt > 0f) (dt * 1.05f).coerceIn(0.016f, 0.1f) else 0.05f
        return DisplayDebug(DebugShape.Cube, matrix, color, lifetime, DebugFlags.NoSolid, null, settings.opacity)
    }
}
