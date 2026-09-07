# Crity configuration reference

Detailed settings panel guide, full command list, every configurable path and its valid range, HUD styles, target highlight, config file format and compatibility notes. Mod version 3.2.4, tested on Hytale 0.7.0-PRE-1.

## Settings panel

Run `/crity` to open the native Hytale settings window. `/crity help` lists the existing text commands. The panel uses the game's decorated window, buttons, hex text fields with a live color swatch, number fields, sliders and dropdowns.

- **Damage:** display modes, fallback color/text, decimal places, random spread and animation asset. **Show critical text** hides the `critical` rule's text without deleting its custom format or color.
- **Color rules:** select a rule, type any `#RRGGBB` color into its field (a swatch next to it previews the color live), edit its text, enable/disable it, set damage/cause/weapon conditions, and move it up/down. Add a named rule or leave the ID empty for an automatic name. Minimum and maximum damage use text inputs with server-side numeric validation and support scientific notation. An empty minimum means zero; an empty maximum means unlimited. An empty cause/weapon matches everything.
- **Health HUD:** all positions, sizes, fonts, formats, colors, opacity and trail timings.
- **Highlight:** enable glow or diagnostic bounds, change color/brightness/thickness and bounds limits.

The right pane shows a game portrait, a damage sample, the health HUD sample, rule colors and current modes. Change the sample amount, ratio, cause or weapon to try your rules. The portrait is a static game image; this release does not embed an interactive 3D viewport or animate a spawned entity. Combat text animation and native glow need a combat check in the client. The portrait and preview never create world entities or change damage.

**Place HUD** opens a full-screen sample at its actual size. Choose one of nine screen anchors, adjust horizontal/vertical sliders or type precise offsets, and use direction buttons for 1/8/32-unit nudges. **Move controls** switches the control panel between the top and bottom. **Back to settings** keeps the draft. Placement uses native controls, not mouse dragging of the bar.

**Save changes** applies and persists the draft. **Defaults** resets its appearance to server defaults. **Reload saved** replaces the draft with current active settings. **Close** or Escape discards unsaved edits. Opening the panel and moving sliders do not write configuration files. If settings changed elsewhere while the panel was open, saving asks you to reload instead of silently overwriting them.

Preview refreshes are coalesced to at most once every 80 ms while editing. Unchanged layout and palette entries are retained. An idle panel has no recurring animation task or update stream. Closing it cancels the pending refresh. Active panel/HUD/highlight registries use weak references so they do not keep unloaded worlds or disconnected players alive. Client rendering and FPS still need an in-game check; headless tests do not measure them.

## Quick start

```text
/crity color critical ff55aa
/crity color all 33ccff
/crity set hud.position right
/crity set hud.x 24
/crity set hud.y 0
/crity set hud.style slim
/crity set hud.color 33ccff
/crity preview
```

Colors accept `RRGGBB` or `#RRGGBB` in commands; use `#RRGGBB` in JSON. A floating label has one color, as supported by the game protocol. Its prefix and number cannot have separate colors within one label.

## Commands

| Command | Effect |
| --- | --- |
| `/crity` | Open your settings panel |
| `/crity help` | Modes and command help |
| `/crity damage on\|default\|off` | Custom text, game text/colors, or hidden text |
| `/crity health on\|default\|off` | Custom HUD, entity health bar, or hidden health |
| `/crity settings` | List all appearance values and rules in priority order |
| `/crity set <path> <value>` | Change an appearance setting |
| `/crity color <rule-id\|all> <hex>` | Recolor one rule or all damage |
| `/crity rule <id> <field> <value>` | Edit a rule |
| `/crity rule-add <id>` | Insert a new catch-all rule at highest priority |
| `/crity rule-remove <id>` | Remove a rule |
| `/crity rule-move <id> <position>` | Move a rule; position 1 has highest priority |
| `/crity preview` | Show a sample HUD without combat; requires health mode on |
| `/crity reset` | Reset your appearance to server defaults; keep display modes |
| `/crity export <name>` | Save your current appearance under a shared name |
| `/crity import <name>` | Replace your appearance with a previously exported one |
| `/crity exports` | List available exported appearances |
| `/crity debug on\|off` | Enable/disable damage diagnostics for this session |
| `/crity reload` | Reload config; requires `crity.admin` permission or console |
| `/crity diagnostics` | Report which Crity features are active and why any are not, for bug reports |

Text values can contain spaces. Appearance changes update a visible HUD immediately; damage changes apply on the next hit. Preferences belong to the player running the command.

## Sharing an appearance

`/crity export <name>` writes your current saved appearance (colors, rules, HUD, highlight - not your damage/health display modes) to a server-wide `crity_exports/<name>.json` file; names are 1-32 letters, digits, `_` or `-` and are shared across all players, so pick something distinctive if you don't want to overwrite someone else's. `/crity import <name>` reads it back through the exact same strict validation as the main config file, applies it, and saves - a corrupt or hand-edited export is rejected with an error and changes nothing. `/crity exports` lists what is available. This is the way to carry a look between characters or servers, or to hand someone else your color scheme.

## Damage settings

| Path | Values |
| --- | --- |
| `damage.showCriticalLabel` | true/false; hide the critical rule label while preserving its format |
| `damage.color` | Fallback color when no rule matches |
| `damage.format` | Fallback text, supports `{amount}` and `{cause}` |
| `damage.decimals` | 0, 1, or 2; zero decimals floors the number |
| `damage.randomAngle` | true/false; false preserves the hit angle |
| `damage.minAngle`, `damage.maxAngle` | Ordered signed angles within -180..180; default -75..75 |
| `damage.template` | `CrityCombat`, `CrityCombatSmall`, `CrityCombatRise`, or a custom CombatText asset id |

Rules are evaluated from top to bottom. Disabled rules are skipped. The first match supplies **both** color and format. Each rule has `id`, `enabled`, `color`, `minPercent`, `minAmount`, `maxAmount`, `cause`, `weaponPrefix`, and `format`. Filters must all match. Damage bounds are inclusive. Cause matches an exact asset id, ignoring case; weaponPrefix matches the item held by the attacker, ignoring case. An empty filter matches everything; use `-` in commands to clear a filter. Up to 64 unique rules are supported.

The default rules are `critical` (>= 0.80), `high` (>= 0.55), `medium` (>= 0.25), and `low` (catch-all). The default catch-all means the fallback color/format only takes effect if that rule is removed or restricted. Use `/crity color all ...` to recolor everything.

```text
/crity rule-add fire
/crity rule fire cause Fire
/crity color fire ff8800
/crity rule fire format FIRE {amount}
/crity rule critical minPercent 0.9
/crity rule critical format BIG HIT {amount}
/crity set damage.template CrityCombatSmall
```

`minPercent` uses Crity's weapon damage-range estimate, not an authoritative engine critical-hit flag. The current held item may differ from a projectile's launch weapon. Cause-specific rules are preferable for abilities. Separate real damage events remain separate labels.

Font size, duration and motion curves of floating text live in CombatText asset templates. Add a uniquely named template through an asset pack, then select its id. Missing or non-CombatText templates fall back to CrityCombat and then CombatText. CrityCombatRise removes horizontal scatter. Template assets are shared server assets; colors and template selection are per player.

## HUD settings

| Path | Values |
| --- | --- |
| `hud.position` | top-left, top, top-right, left, center, right, bottom-left, bottom, bottom-right |
| `hud.x`, `hud.y` | -4096..4096 UI units |
| `hud.style` | classic, slim, text, bracket, segmented |
| `hud.orientation` | horizontal, vertical |
| `hud.invert` | true/false; fills toward the opposite edge (right instead of left; top instead of bottom) |
| `hud.width` | 80..1200; the bar's length along its growth axis, in both orientations |
| `hud.barHeight` | 2..100; slim limits the bar to 8; the bar's thickness across its growth axis |
| `hud.padding` | 0..32; classic only |
| `hud.fontSize` | 8..48 |
| `hud.segments` | 2..40; segmented only |
| `hud.color`, `hud.trailColor` | Health and trailing damage colors |
| `hud.backgroundColor`, `hud.trackColor`, `hud.textColor` | Panel, empty track and text colors |
| `hud.opacity` | 0..1; classic panel opacity |
| `hud.showText`, `hud.showTrail` | true/false; text style always shows text |
| `hud.format` | `{current}`, `{max}`, `{percent}` (includes the % sign) |
| `hud.durationMs` | 500..60000 |
| `hud.trailDelayMs` | 0..10000 |
| `hud.trailSmoothing` | 0.01..1; larger values drain the trail faster |

At screen edges, positive offsets move inward. At horizontal/vertical center, positive offsets move right/down. Positions remain anchored when resolution changes. Offsets and dimensions use the game's UI coordinate scale. Large offsets can put the HUD offscreen; `/crity reset` restores it. Position changes keep existing offsets; for a vertically centered HUD, set `hud.y` to 0. Text and slim styles omit the outer panel.

**Bar styles.** `classic` shows a full background panel behind the bar, with text overlaid. `slim` and `bracket` drop the panel and stack the text above a bare bar; `bracket` additionally draws a thin accent frame in the health color around the bar. `segmented` replaces the smooth bar with `hud.segments` fixed blocks that light up one at a time as health rises, with damage trail (if enabled) shown as a transitional block color. `text` shows only the health text, no bar at all.

**Orientation and invert.** `hud.orientation vertical` turns any style into a vertical bar that fills/drains top-to-bottom instead of left-to-right; `hud.width`/`hud.barHeight` keep the same meaning (bar length and thickness) in both orientations, so switching orientation does not require re-tuning size. By default a horizontal bar drains toward the right (stays pinned to the left edge) and a vertical bar drains downward (stays pinned to the bottom edge, like a thermometer); `hud.invert true` flips that to drain toward the left, or upward, instead. Use **Place HUD** to preview any combination at its actual on-screen size before saving.

```text
/crity set hud.style bracket
/crity set hud.orientation vertical
/crity set hud.invert true
/crity set hud.style segmented
/crity set hud.segments 8
```

## Target highlight and diagnostic bounds

`/crity highlight on` enables a native model glow on your last successfully damaged target. Each hit refreshes it for `hud.durationMs`, even if the HUD is disabled. Switching targets removes your previous highlight. Turning it off, losing visibility, disconnecting or reaching the deadline removes it. The client also receives a finite effect lifetime. This is visual feedback for a confirmed hit; it does not promise that the next attack will land or change collision/damage rules.

The effect is sent only to the attacker, with a dedicated Crity asset id. It does not add an effect component to the entity, change stats, clear other effects, replace item icons or save anything on the target. The native ModelVFX renderer provides a colored glow/sweep with a subtle tint; it is not a guarantee of Minecraft's exact silhouette or through-wall rendering. Client appearance still needs in-game validation.

```text
/crity highlight on
/crity set hitboxes.color 66ddff
/crity set hitboxes.thickness 0.3
/crity set hitboxes.opacity 0.7
/crity set hud.durationMs 5000
/crity highlight off
```

The existing `hitboxes` configuration section is retained for compatibility. Old enabled configs select `GLOW` automatically when no mode is present. `/crity hitboxes on|off` toggles the currently selected mode; `/crity highlight on|off` selects `GLOW` explicitly. Color and thickness changes apply on the next hit.

| Path | Values |
| --- | --- |
| `hitboxes.enabled` | true/false; off by default |
| `hitboxes.mode` | `GLOW` (default), `BOUNDS` |
| `hitboxes.color` | #RRGGBB, per player |
| `hitboxes.opacity` | 0.1..1; glow brightness or bounds opacity; never entity transparency |
| `hitboxes.thickness` | 0.05..2; native glow highlight thickness |
| `hitboxes.range` | 4..128 blocks; BOUNDS only |
| `hitboxes.maxEntities` | 1..512; BOUNDS only |
| `hitboxes.showSelf` | true/false; BOUNDS only |

For diagnostic wireframes, use `/crity set hitboxes.mode bounds` then `/crity hitboxes on`. This displays the engine's current broad bounding boxes for nearby tracked entities. A box is not a guarantee of a successful hit: attack shape, range, timing and client/server interpolation still apply. Each wireframe expires within 100 ms. The mod never clears other debug overlays. Use `/crity highlight on` to return to glow mode.

## Configuration and migration

`crity_players.json` remains in the server working directory. Schema 2 has `schemaVersion`, `defaults` (damage, hud and hitbox appearance), and `players` keyed by UUID. Each player entry has `damage`, `health`, and `visual`. Enum values in JSON use uppercase names, such as `TOP_RIGHT`.

Existing 2.9.x player modes are retained. On the first save, the old file is copied to `crity_players.json.v1.bak`, then replaced atomically where supported. The backup is never overwritten. Invalid configs and unsupported schema versions leave the current settings active and disable saving until a successful `/crity reload`; the original file is preserved. Debug mode is not persisted.

Missing appearance fields inherit server defaults. New players start with server defaults. Saved players have a complete appearance snapshot; use `/crity reset` to adopt changed server defaults. Use `/crity reload` after editing the file and before changing settings in game. Unknown visual keys are rejected to catch typos.

## Compatibility and development

The `config` and `display` packages separate validation, rule matching and layout generation from Hytale calls. `compat` contains the registry replacement, optional feature guards and weapon metadata adapter. The HUD and command packages handle their respective Hytale APIs. No private-field reflection is used.

Crity does not reject new version numbers or revisions. The server manifest is logged for diagnostics. Each integration is checked where it is used: the settings panel checks its public API and shared UI styles; assets, commands, glow, diagnostic bounds and combat replacement each have separate failure guards. A missing feature logs an English explanation and leaves other compatible features available. `/crity help` remains the fallback if the graphical panel is unavailable and commands still work.

The replacement system is registered before removing stock combat text. Failed installation rolls back registration; shutdown restores stock text when the registry is alive. Missing stock registration leaves the registry alone. A custom combat display failure falls back to the stock handler on subsequent hits. HUD failures select the entity bar on subsequent hits. Native glow is visual only and has a finite lifetime. No custom components are serialized into world entities.

`ServerVersion: "*"` allows compatible future builds to try starting the plugin. `IncludesAssetPack: false` keeps asset registration under the guarded runtime. Assets use Crity-specific IDs and do not replace item icons or the game's common UI files. Public-method checks and runtime guards cannot predict every client/protocol behavior change, but nothing Crity's own bootstrap does can prevent the world or any other mod from starting: plugin setup and start are wrapped in a catch-all boundary, so even a total, unanticipated failure there disables Crity's own integration and nothing else. An update can still be needed; there is no universal future-compatibility guarantee.

Every Hytale call that could break on a future server update goes through a named `FeatureGate` (`compat/FeatureGate.kt`): a failure there disables only that one feature, logs its name and the exact exception, and leaves the rest of Crity, other mods and the world unaffected. `/crity diagnostics` reports the live status of every feature and, for any that are disabled, the exception that took it down - run it any time (not just at startup) and paste its output, or the matching `[Crity][diagnostic]` server log lines, when reporting a compatibility issue after a game update.

Build with Gradle and a JDK capable of reading your server JAR (JDK 25+ for this build):

```bash
gradle --no-daemon build
```

Set `HYTALE_SERVER_JAR` or `-PhytaleServerJar=/path/to/HytaleServer.jar` to override the default Linux pre-release path. Output: `build/libs/Crity-3.2.4.jar`. Kotlin stdlib is bundled; the Hytale server JAR is not.

`gradle check` includes the `checkCombat` executable checks: protocol serialization, one-label queuing, signed scatter, custom rules/priority, strict validation, configuration migration/backup, player isolation, layout command generation, native hitbox packet serialization and bounds, nearest-entity limits, registration rollback under injected failures, informational server build detection, finite additive glow packets, deadline refresh glow config migration, complete frontend field coverage, isolated settings drafts, UI event/packet serialization, critical-label and rule toggles, incremental preview updates and weak-session lifecycle. Layout command checks do not render the client UI. Check positions and styles in game at your usual UI scale before publishing.

Install by replacing the old Crity JAR while the server/world is stopped. Keep only one Crity version in the mods directory. GitHub contains source code; JAR distribution is separate.
