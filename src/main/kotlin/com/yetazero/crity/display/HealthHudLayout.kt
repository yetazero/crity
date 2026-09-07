package com.yetazero.crity.display

import com.yetazero.crity.config.HudAppearance
import com.yetazero.crity.config.HudPosition
import com.yetazero.crity.config.HudStyle
import java.util.Locale
import kotlin.math.ceil

object HealthHudLayout {
    fun healthText(current: Float, max: Float, settings: HudAppearance): String = settings.format
        .replace("{current}", ceil(current.coerceIn(0f, max)).toLong().toString())
        .replace("{max}", ceil(max).toLong().toString())
        .replace("{percent}", if (max > 0f) "${(current / max * 100f).toInt().coerceIn(0, 100)}%" else "0%")

    fun build(settings: HudAppearance): String {
        settings.validate()
        val p = settings.position
        val boxW = settings.boxWidth
        val boxH = settings.boxHeight
        val horizontal = when (p) {
            HudPosition.TOP_LEFT, HudPosition.LEFT, HudPosition.BOTTOM_LEFT -> "Left: ${settings.x}, Width: $boxW"
            HudPosition.TOP_RIGHT, HudPosition.RIGHT, HudPosition.BOTTOM_RIGHT -> "Right: ${settings.x}, Width: $boxW"
            else -> "Left: ${settings.x}, Right: ${-settings.x}"
        }
        val vertical = when (p) {
            HudPosition.TOP_LEFT, HudPosition.TOP, HudPosition.TOP_RIGHT -> "Top: ${settings.y}, Height: $boxH"
            HudPosition.BOTTOM_LEFT, HudPosition.BOTTOM, HudPosition.BOTTOM_RIGHT -> "Bottom: ${settings.y}, Height: $boxH"
            else -> "Top: ${settings.y}, Bottom: ${-settings.y}"
        }
        val background = if (settings.style == HudStyle.CLASSIC)
            "Background: ${settings.backgroundColor}(${String.format(Locale.ROOT, "%.3f", settings.opacity)});" else ""
        val textVisible = settings.showText || settings.style == HudStyle.TEXT
        val textHeight = if (settings.stackedTextSpace > 0) settings.stackedTextSpace - 2 else settings.height

        val barW = if (settings.isVertical) settings.barThickness else settings.barLength
        val barH = if (settings.isVertical) settings.barLength else settings.barThickness
        val barLeft = if (settings.isVertical) (boxW - barW) / 2 else settings.inset
        val barAnchor = "Left: $barLeft, Top: ${settings.barTop}, Width: $barW, Height: $barH"

        val bar = if (settings.style == HudStyle.SEGMENTED) segmentedBar(settings, barAnchor, barW, barH)
            else continuousBar(settings, barAnchor, barW, barH)

        return """
            Group {
              Anchor: ($horizontal, $vertical);
              LayoutMode: CenterMiddle;
              Group #TargetHud {
                Anchor: (Width: $boxW, Height: $boxH);
                $background
                Group #Frame {
                  Anchor: (Left: ${barLeft - 2}, Top: ${settings.barTop - 2}, Width: ${barW + 4}, Height: ${barH + 4});
                  Visible: ${settings.style == HudStyle.BRACKET};
                  Background: ${settings.color};
                }
                $bar
                Label #HealthText {
                  Anchor: (Left: 0, Right: 0, Top: 0, Height: $textHeight);
                  Visible: $textVisible;
                  Text: "";
                  Style: (FontSize: ${settings.fontSize}, TextColor: ${settings.textColor}, Alignment: Center, Wrap: true);
                }
              }
            }
        """.trimIndent()
    }

    private fun continuousBar(settings: HudAppearance, barAnchor: String, barW: Int, barH: Int) = """
              Group #BarTrack {
                Anchor: ($barAnchor);
                Visible: ${settings.style != HudStyle.TEXT};
                Background: ${settings.trackColor};
                Group #PhantomFill {
                  Anchor: (Left: 0, Top: 0, Width: $barW, Height: $barH);
                  Visible: ${settings.showTrail};
                  Background: ${settings.trailColor};
                }
                Group #HealthFill {
                  Anchor: (Left: 0, Top: 0, Width: $barW, Height: $barH);
                  Background: ${settings.color};
                }
              }
    """.trimIndent()

    private fun segmentedBar(settings: HudAppearance, barAnchor: String, barW: Int, barH: Int): String {
        val gap = 3
        val layoutMode = if (settings.isVertical) "Bottom" else "Left"
        val gapAnchor = if (settings.isVertical) "Top: $gap" else "Right: $gap"
        val segments = (0 until settings.segments).joinToString("\n") { i ->
            "      Group #Segment$i { FlexWeight: 1; Anchor: ($gapAnchor); Background: ${settings.trackColor}; }"
        }
        return """
              Group #SegmentTrack {
                Anchor: ($barAnchor);
                LayoutMode: $layoutMode;
$segments
              }
        """.trimIndent()
    }
}
