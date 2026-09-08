# Crity configuration reference

Settings, commands, and customization examples for Crity 3.4.2 on Hytale 0.7.0-PRE1.

## Settings panel

Run `/crity` to open the native Hytale settings window. `/crity help` lists the existing text commands. The panel uses the game's decorated window, buttons, hex text fields with a live color swatch, number fields, sliders and dropdowns.

- **Damage:** display modes, fallback color/text, decimal places, random spread and animation asset. **Show critical text** hides the `critical` rule's text without deleting its custom format or color.
- **Color rules:** select a rule, type any `#RRGGBB` color into its field (a swatch next to it previews the color live), edit its text, enable/disable it, set damage/cause/weapon conditions, and move it up/down. Add a named rule or leave the ID empty for an automatic name. Minimum and maximum damage use text inputs with server-side numeric validation and support scientific notation. An empty minimum means zero; an empty maximum means unlimited. An empty cause/weapon matches everything.
- **Health HUD:** all positions, sizes, fonts, formats, colors, opacity and trail timings.
- **Reticle:** edit separate melee and ranged profiles with two dedicated reticle previews. Choose a shape and customize its geometry, center marker, colors, outline and position.
- **Highlight:** enable glow or diagnostic bounds, change color/brightness/thickness and bounds limits.

The right pane shows a static game portrait, a damage sample, the health HUD sample, rule colors and current modes. Change the sample amount, ratio, cause or weapon to try your rules. Floating-text motion and target glow are visible during combat.

**Place HUD**, in the **Health HUD** tab, opens a full-screen sample at its actual size. Choose one of nine screen anchors, adjust horizontal/vertical sliders or type precise offsets, and use direction buttons for 1/8/32-unit nudges. **Move controls** switches the control panel between the top and bottom. **Back to settings** keeps the draft. Placement uses native controls, not mouse dragging of the bar.

**Save changes** applies and persists the draft. **Defaults** resets its appearance to server defaults. **Reload saved** replaces the draft with current active settings. **Close** or Escape discards unsaved edits. Opening the panel and moving sliders do not write configuration files. If settings changed elsewhere while the panel was open, saving asks you to reload instead of silently overwriting them.

## Custom reticles

Open **Reticle**, enable **Custom reticle**, then choose **Melee profile** or **Ranged profile**. Each profile has its own complete set of controls. The right pane contains only the two reticle previews; scroll to see the full second preview. They show your draft without requiring a weapon or target. Choose **Save changes** to apply your designs.

**Profile selection** offers `AUTO`, `MELEE` and `RANGED`. AUTO uses the game's primary attack `Ranged` tag to select the ranged profile; other items and empty hands use melee. The other choices lock a profile, which is useful with weapons from other mods. Turning off **Use custom profile** restores the game reticle for that profile. Turning off **Custom reticle** restores the game reticle completely.

Choose from 16 shapes: dot, cross, plus, T cross, diagonal cross, ring, double ring, ring with cross, diamond, diamond with cross, square, brackets, chevron, triangle, hexagon and star. Outlines are continuous and antialiased. The optional center marker has its own color, opacity, size and shape: dot, square, diamond or ring.

The paths below begin with `reticle.melee.` or `reticle.ranged.`. Both profiles support every setting independently.

| Setting | Purpose | Range / choices |
| --- | --- | --- |
| `enabled` | Use this custom profile | true / false |
| `shape` | Main shape | 16 shapes listed above |
| `color` | Main shape color | Any HEX color |
| `outlineColor` | Outline color | Any HEX color |
| `centerColor` | Center marker color | Any HEX color |
| `size` | Radius for closed shapes and chevrons; diameter for dot | 4–40 |
| `armLength` | Cross arm length or bracket corner length | 2–32 |
| `gap` | Cross center gap or spacing between double rings | 0–24 |
| `thickness` | Main line thickness | 1–8 |
| `outlineWidth` | Outline thickness; zero removes it | 0–4 |
| `roundEnds` | Rounded ends on open lines | true / false |
| `showCenter` | Show the independent center marker | true / false |
| `centerShape` | Center marker shape | DOT / SQUARE / DIAMOND / RING |
| `centerSize` | Center marker diameter | 1–12 |
| `opacity` | Main shape opacity | 0–1 |
| `outlineOpacity` | Outline opacity, multiplied by its shape opacity | 0–1 |
| `centerOpacity` | Center marker opacity | 0–1 |
| `rotation` | Main shape rotation in degrees | 0–359 |
| `stretchX` / `stretchY` | Main shape proportions, in percent | 50–150 |
| `offsetX` / `offsetY` | Move the entire reticle from screen center | −24–24 |

Geometry controls apply to the shapes they describe: arm length affects crosses and brackets, while size affects closed shapes, dots and chevrons. The independent center marker stays aligned with the aim point unless you change the reticle offsets. Use zero offsets for a centered aim marker.

Examples:

```text
/crity set reticle.enabled true
/crity set reticle.melee.shape chevron
/crity set reticle.melee.color ffd080
/crity set reticle.ranged.shape ring_cross
/crity set reticle.ranged.thickness 2
/crity set reticle.ranged.centerColor 80ffff
```

Profiles are saved per player and included in exported appearances. Existing reticle colors and center preferences are migrated automatically. Other combat and HUD settings are preserved. Static geometry is cached, and the HUD only updates when the active design changes. Compatibility checks for weapon selection and the HUD are separate; `/crity diagnostics` reports integration failures.

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
| `/crity highlight on\|off` | Enable/disable target glow |
| `/crity hitboxes on\|off` | Enable/disable the selected glow or diagnostic bounds mode |
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

`/crity export <name>` writes your current saved appearance (colors, rules, HUD, highlight and reticles - not your damage/health display modes) to a server-wide `crity_exports/<name>.json` file; names are 1-32 letters, digits, `_` or `-` and are shared across all players, so pick something distinctive if you don't want to overwrite someone else's. `/crity import <name>` reads it back through the exact same strict validation as the main config file, applies it, and saves - a corrupt or hand-edited export is rejected with an error and changes nothing. `/crity exports` lists what is available. This is the way to carry a look between characters or servers, or to hand someone else your color scheme.

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

The highlight is visible only to the attacking player. It uses Hytale's native colored glow effect and does not change the target's stats.

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

## Compatibility and troubleshooting

Crity attempts to start on newer Hytale versions and handles integration failures per feature. When a guarded feature encounters an incompatible API, it is disabled and its error is logged while other compatible features remain available. A game update may still require a new Crity release.

Run `/crity diagnostics` to see which features are active and why any have been disabled. If the settings panel is unavailable, use `/crity help` for text commands.

When [reporting a problem](https://github.com/yetazero/crity/issues), include your Crity and Hytale versions, steps to reproduce it, and the diagnostics output or relevant `[Crity][diagnostic]` server log lines. For display problems, include a screenshot and your UI scale.
