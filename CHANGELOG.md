# Changelog

## 3.4.2 — Custom reticles

Hytale 0.7.0-PRE1 · [Download](https://github.com/yetazero/crity/releases/tag/Crity0.7.0-PRE1-v3)

### Added

- Independent melee and ranged reticle profiles with 16 continuous shapes.
- Separate colors, outlines, center markers, opacity, size, rotation, proportions and offsets for each profile.
- Automatic weapon-based profile selection and manual melee/ranged selection.
- Dedicated melee and ranged previews in the native settings panel.
- Reticle preferences in per-player saves and shared appearance exports.
- Separate compatibility checks for reticle rendering and weapon selection, with diagnostics reporting.

### Improved

- Reticle previews appear as soon as the Reticle tab opens.
- Chevron tips stay at the aim point across size and rotation changes.
- Very low reticle opacity remains visible.
- HUD placement controls appear in the Health HUD tab.
- Static reticle geometry is cached and only sent again when the active design changes.

### Updating

Stop your world or server and replace the previous Crity JAR with `Crity-3.4.2.jar`.
Damage, HUD and highlight preferences are preserved. Open `/crity` → **Reticle**
to enable and customize the new reticles, then select **Save changes**.

## 3.2.4 — Combat appearance settings

Hytale 0.7.0-PRE1 · [Release notes](https://github.com/yetazero/crity/releases/tag/Crity0.7.0-PRE1-v2)

- Native settings panel with previews and HUD placement controls.
- Custom damage rules, labels, colors, conditions and priorities.
- Five HUD styles, nine screen anchors, vertical layouts and inverted fill direction.
- Target glow, diagnostic bounds, appearance sharing and feature diagnostics.
