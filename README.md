Crity — Dynamic Combat Damage & Target HUD

Current version: **3.2.4**, originally released for **Hytale 0.7.0-pre.1**.

Download JARs from [Releases](https://github.com/yetazero/crity/releases), or open
[Actions](https://github.com/yetazero/crity/actions/workflows/build.yml) to download
an automated build. See [BUILDING.md](BUILDING.md) for local builds and all three
restored version tags.

Crity brings an immersive, modern combat experience to Hytale! It replaces static damage indicators with dynamic tiered floating numbers, critical hit popups, and introduces a clean, layered top-screen Target Health Bar with smooth phantom health drain.

DISCLAIMER & NOTE I am not a professional developer! I create mods purely as a hobby and for fun. Because this mod hooks into internal damage events, custom UI pipelines, and game reflection systems, you might encounter occasional visual quirks or minor glitches. Feel free to experiment, test it out, and leave feedback in the comments!

FEATURES

Dynamic Damage Tiers & Critical Popups Damage numbers dynamically scale in color, size, and animation depending on how hard you hit relative to your weapon's damage range: • Cyan (< 25% weapon range): Light glancing blow. • Green (25% – 55% weapon range): Standard solid strike. • Yellow (55% – 80% weapon range): Heavy hit. • Red + CRIT! (≥ 80% weapon range): Massive critical hit with an additional explosive "CRIT!" popup!

Layered Target Health Bar • Phantom Damage Trail: Instant red health bar drop with a smooth orange phantom trail that gracefully drains down. • Exact HP Numbers: Clear and precise numerical health readout (Current HP / Max HP). • Smart Auto-Hide: Automatically fades away after 5 seconds of combat inactivity. • Keeps entity heads clean by moving target health to an elegant top-centered HUD.

COMMANDS & CUSTOMIZATION

Every player can independently configure their preferences. All settings are automatically saved and persist across server restarts!

• /crity View your current display settings.

• /crity damage [on | default | off] Switch between Custom Tiers, Vanilla White, or Disabled.

• /crity health [on | default | off] Switch between HUD, Vanilla 3D Bar, or Disabled.

---

## What's new in 3.2.4

The description above is the original one from way back - keeping it here as-is, but the mod has grown a lot since then. Here's what actually changed:

**A real settings panel.** Running `/crity` now opens a native in-game GUI instead of just printing text - buttons, sliders, dropdowns, a live preview pane, the works. `/crity help` still lists every text command if you'd rather not touch the GUI at all.

**Custom color rules instead of 4 fixed tiers.** You're no longer stuck with cyan/green/yellow/red at fixed damage percentages. Add as many rules as you want (up to 64), each with its own color, custom text, and conditions (damage amount range, percent-of-weapon-range, damage cause, weapon ID prefix). Rules are checked top to bottom and the first match wins - reorder them however you like.

**The HUD is fully customizable now**, not just top-centered: 9 screen anchors, adjustable size and offset, and five distinct visual styles (`classic`, `slim`, `text`, `bracket`, `segmented`). It can also run **vertically** instead of horizontally, and the fill direction can be **inverted** (drain toward the opposite edge) - all independent of which style you pick. Colors, fonts, opacity, the phantom trail timing/speed, all still configurable per player.

**Target highlight and diagnostic hitboxes.** `/crity highlight on` gives your last hit target a native glow for a configurable duration. There's also a diagnostic bounding-box mode for debugging hit detection.

**Export/import appearances.** `/crity export <name>` saves your current look to a shared, server-wide slot; `/crity import <name>` applies one back (yours or someone else's). `/crity exports` lists what's available.

**It won't take the server down with it.** Every integration point with the base game goes through an isolated failure gate - if a future Hytale update breaks one specific feature (say, the settings panel), only that feature turns itself off; everything else in Crity, every other mod, and the world itself keep running normally. `/crity diagnostics` reports exactly what's active and, for anything that isn't, why - handy for bug reports.

Full command list, every setting and its valid range, and more detail on all of the above: see [CONFIGURATION.md](CONFIGURATION.md) (or just run `/crity help` in game).
