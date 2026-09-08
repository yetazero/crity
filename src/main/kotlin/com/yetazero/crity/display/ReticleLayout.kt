package com.yetazero.crity.display

import com.yetazero.crity.config.ReticleCenter
import com.yetazero.crity.config.ReticleProfile
import com.yetazero.crity.config.ReticleShape
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ReticleLayout {
    const val SIZE = 256
    private val cache = object : LinkedHashMap<ReticleProfile, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ReticleProfile, String>) = size > 32
    }
    @Synchronized fun clear() = cache.clear()

    @Synchronized fun build(profile: ReticleProfile): String = cache.getOrPut(profile) { markup(raster(profile), profile) }

    fun raster(p: ReticleProfile): BufferedImage {
        p.validate()
        val body = Area()
        val pen = BasicStroke(p.thickness.toFloat(), if (p.roundEnds) BasicStroke.CAP_ROUND else BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
        fun add(shape: Shape) { body.add(Area(pen.createStrokedShape(shape))) }
        fun line(x1: Double, y1: Double, x2: Double, y2: Double) = add(Path2D.Double().apply { moveTo(x1, y1); lineTo(x2, y2) })
        fun ring(radius: Double) = add(Ellipse2D.Double(-radius, -radius, radius * 2, radius * 2))
        fun polygon(count: Int, radius: Double, angle: Double = -PI / 2, inner: Double? = null) {
            add(Path2D.Double().apply {
                for (i in 0 until count) {
                    val a = angle + 2 * PI * i / count
                    val r = if (inner != null && i % 2 == 1) inner else radius
                    if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
                }
                closePath()
            })
        }
        val r = p.size.toDouble()
        fun cross(diagonal: Boolean = false, top: Boolean = true, joined: Boolean = false) {
            for (i in 0..3) {
                if (!top && i == 3) continue
                val angle = i * PI / 2 + if (diagonal) PI / 4 else 0.0
                val start = if (joined) 0.0 else p.gap.toDouble()
                val end = p.gap + p.armLength.toDouble()
                line(cos(angle) * start, sin(angle) * start, cos(angle) * end, sin(angle) * end)
            }
        }
        when (p.shape) {
            ReticleShape.DOT -> body.add(Area(Ellipse2D.Double(-r / 2, -r / 2, r, r)))
            ReticleShape.CROSS -> cross()
            ReticleShape.PLUS -> cross(joined = true)
            ReticleShape.T_CROSS -> cross(top = false)
            ReticleShape.X_CROSS -> cross(diagonal = true)
            ReticleShape.RING -> ring(r)
            ReticleShape.DOUBLE_RING -> { ring(r); ring(maxOf(2.0, r - p.gap - p.thickness)) }
            ReticleShape.RING_CROSS -> { ring(r); cross() }
            ReticleShape.DIAMOND -> polygon(4, r)
            ReticleShape.DIAMOND_CROSS -> { polygon(4, r); cross() }
            ReticleShape.SQUARE -> add(Rectangle2D.Double(-r, -r, r * 2, r * 2))
            ReticleShape.BRACKETS -> for (x in listOf(-1, 1)) for (y in listOf(-1, 1)) {
                val length = minOf(p.armLength.toDouble(), r)
                add(Path2D.Double().apply {
                    moveTo(x * (r - length), y * r); lineTo(x * r, y * r); lineTo(x * r, y * (r - length))
                })
            }
            ReticleShape.CHEVRON -> add(Path2D.Double().apply { moveTo(-r, r); lineTo(0.0, 0.0); lineTo(r, r) })
            ReticleShape.TRIANGLE -> polygon(3, r)
            ReticleShape.HEXAGON -> polygon(6, r, 0.0)
            ReticleShape.STAR -> polygon(10, r, inner = r * 0.45)
        }
        val transform = AffineTransform().apply {
            translate(SIZE / 2.0 + p.offsetX, SIZE / 2.0 + p.offsetY)
            scale(p.stretchX / 100.0, p.stretchY / 100.0)
            rotate(Math.toRadians(p.rotation.toDouble()))
        }
        val main = body.createTransformedArea(transform)
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            fun draw(shape: Shape, color: String, opacity: Float) {
                g.color = Color((color.removePrefix("#").toInt(16) and 0xffffff) or ((opacity * 255).toInt().coerceIn(0, 255) shl 24), true)
                g.fill(shape)
            }
            fun outlined(shape: Shape, color: String, opacity: Float, occupied: Shape? = null) {
                if (p.outlineWidth > 0 && p.outlineOpacity > 0) {
                    val outline = Area(BasicStroke(p.outlineWidth * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shape))
                    outline.subtract(Area(shape))
                    if (occupied != null) outline.subtract(Area(occupied))
                    draw(outline, p.outlineColor, p.outlineOpacity * opacity)
                }
                draw(shape, color, opacity)
            }
            outlined(main, p.color, p.opacity)
            if (p.showCenter) {
                val size = p.centerSize.toDouble()
                val center: Shape = when (p.centerShape) {
                    ReticleCenter.DOT -> Ellipse2D.Double(-size / 2, -size / 2, size, size)
                    ReticleCenter.SQUARE -> Rectangle2D.Double(-size / 2, -size / 2, size, size)
                    ReticleCenter.DIAMOND -> Path2D.Double().apply { moveTo(0.0, -size / 2); lineTo(size / 2, 0.0); lineTo(0.0, size / 2); lineTo(-size / 2, 0.0); closePath() }
                    ReticleCenter.RING -> BasicStroke(1f).createStrokedShape(Ellipse2D.Double(-size / 2, -size / 2, size, size))
                }
                val atCenter = AffineTransform.getTranslateInstance(SIZE / 2.0 + p.offsetX, SIZE / 2.0 + p.offsetY).createTransformedShape(center)
                outlined(atCenter, p.centerColor, p.centerOpacity, main)
            }
        } finally { g.dispose() }
        return image
    }

    private data class Run(val x: Int, val width: Int, val color: Int)
    private data class Rect(val run: Run, val top: Int, var height: Int = 1)

    private fun markup(image: BufferedImage, profile: ReticleProfile): String {
        val palette = listOf(profile.color, profile.outlineColor, profile.centerColor).map { it.removePrefix("#").toInt(16) }.distinct()
        val alphas = (listOf(profile.opacity, profile.centerOpacity, profile.opacity * profile.outlineOpacity,
            profile.centerOpacity * profile.outlineOpacity).flatMap {
            val alpha = (it * 255).toInt()
            listOf(alpha, alpha / 4, alpha / 2, alpha * 3 / 4)
        } + 1).filter { it > 0 }.distinct()
        val rectangles = mutableListOf<Rect>()
        var previous = emptyMap<Run, Rect>()
        fun pixel(x: Int, y: Int): Int {
            val rgba = image.getRGB(x, y)
            val coverage = rgba ushr 24
            if (coverage == 0) return 0
            val alpha = alphas.minBy { kotlin.math.abs(it - coverage) }
            val rgb = rgba and 0xffffff
            val color = if (rgb in palette) rgb else palette.minBy { color ->
                listOf(0, 8, 16).sumOf { shift -> val delta = ((rgb shr shift) and 255) - ((color shr shift) and 255); delta * delta }
            }
            return color or (alpha shl 24)
        }
        for (y in 0 until SIZE) {
            val current = mutableMapOf<Run, Rect>()
            var x = 0
            while (x < SIZE) {
                val color = pixel(x, y)
                val start = x++
                while (x < SIZE && pixel(x, y) == color) x++
                if (color == 0) continue
                val run = Run(start, x - start, color)
                val rect = previous[run]?.also { it.height++ } ?: Rect(run, y).also { rectangles.add(it) }
                current[run] = rect
            }
            previous = current
        }
        return buildString {
            append("Group { Anchor: (Width: $SIZE, Height: $SIZE); HitTestVisible: false;\n")
            for (rect in rectangles) {
                val run = rect.run
                val color = String.format(Locale.ROOT, "#%06x(%.3f)", run.color and 0xffffff, (run.color ushr 24) / 255.0)
                append("Group { Anchor: (Left: ${run.x}, Top: ${rect.top}, Width: ${run.width}, Height: ${rect.height}); Background: $color; HitTestVisible: false; }\n")
            }
            append('}')
        }
    }
}
