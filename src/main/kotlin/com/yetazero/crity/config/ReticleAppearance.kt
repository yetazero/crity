package com.yetazero.crity.config

enum class ReticleShape { DOT, CROSS, PLUS, T_CROSS, X_CROSS, RING, DOUBLE_RING, RING_CROSS, DIAMOND, DIAMOND_CROSS, SQUARE, BRACKETS, CHEVRON, TRIANGLE, HEXAGON, STAR }
enum class ReticleCenter { DOT, SQUARE, DIAMOND, RING }
enum class ReticleMode { AUTO, MELEE, RANGED }

data class ReticleProfile(
    val enabled: Boolean = true,
    val shape: ReticleShape = ReticleShape.CROSS,
    val color: String = "#f2e8d5",
    val outlineColor: String = "#202832",
    val centerColor: String = "#f2e8d5",
    val size: Int = 12,
    val armLength: Int = 9,
    val gap: Int = 5,
    val thickness: Int = 2,
    val outlineWidth: Int = 1,
    val centerSize: Int = 3,
    val showCenter: Boolean = true,
    val centerShape: ReticleCenter = ReticleCenter.DOT,
    val opacity: Float = 1f,
    val outlineOpacity: Float = 0.85f,
    val centerOpacity: Float = 1f,
    val rotation: Int = 0,
    val stretchX: Int = 100,
    val stretchY: Int = 100,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val roundEnds: Boolean = false
) {
    fun validate() {
        require(shape in ReticleShape.entries && centerShape in ReticleCenter.entries)
        listOf(color, outlineColor, centerColor).forEach(::validateColor)
        require(size in 4..40 && armLength in 2..32 && gap in 0..24 && thickness in 1..8 && outlineWidth in 0..4)
        require(centerSize in 1..12 && rotation in 0..359 && stretchX in 50..150 && stretchY in 50..150)
        require(offsetX in -24..24 && offsetY in -24..24)
        require(listOf(opacity, outlineOpacity, centerOpacity).all { it.isFinite() && it in 0f..1f })
    }
}

data class ReticleAppearance(
    val enabled: Boolean = false,
    val mode: ReticleMode = ReticleMode.AUTO,
    val melee: ReticleProfile = ReticleProfile(),
    val ranged: ReticleProfile = ReticleProfile(shape = ReticleShape.RING, size = 10)
) {
    fun validate() { require(mode in ReticleMode.entries); melee.validate(); ranged.validate() }
    fun profile(isRanged: Boolean) = if (mode == ReticleMode.RANGED || mode == ReticleMode.AUTO && isRanged) ranged else melee
}
