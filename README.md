# Crity

Customizable damage numbers, a target health HUD, combat highlights, and custom reticles for Hytale.
Choose your colors, create damage rules, and place the HUD where it suits your playstyle.

[Download](https://github.com/yetazero/crity/releases/latest) · [Configuration guide](CONFIGURATION.md) · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/yetazero/crity/issues)

## New in 3.4.2

Create separate melee and ranged reticles with 16 shapes, independent center markers,
and precise color, outline, size and position controls. Open `/crity` → **Reticle**
to preview both designs and save your settings.

[See all changes](CHANGELOG.md) · [Download for Hytale 0.7.0-PRE1](https://github.com/yetazero/crity/releases/tag/Crity0.7.0-PRE1-v3)

## Features

- **Damage text:** choose any HEX color, customize labels, adjust number precision, and show or hide critical text.
- **Custom rules:** match damage amounts, damage types, and weapons. Set rule priorities to control which color and label appear.
- **Target health HUD:** choose from five styles, position it with nine screen anchors and precise offsets, and customize its size, colors, text, and damage trail. Horizontal and vertical layouts are supported.
- **Custom reticles:** separate melee and ranged profiles, 16 continuous shapes, independent center markers, custom colors, outlines, rotation, proportions and offsets. Preview both designs directly in settings.
- **Target highlight:** add a configurable glow to your last damaged target.
- **In-game settings:** open a native settings panel with palettes, a preview, and HUD placement controls.
- **Personal preferences:** each player has their own saved settings. Export and import appearances to share a look with others on the server.

## Installation

Crity supports **Hytale 0.7.0-PRE1**. Check the game version listed on the release before downloading.

1. Download the Crity JAR for your game version from [Releases](https://github.com/yetazero/crity/releases/latest).
2. Stop your world or server.
3. Place the JAR in its Hytale `Mods` folder. When updating, replace the previous Crity JAR so only one version is installed.
4. Start the world or server and enter `/crity`.

## Customize in game

Enter `/crity` to open your settings. Each player can create and save their own appearance.

| Tab | What you can customize |
| --- | --- |
| **Damage** | Custom, vanilla or hidden damage numbers and target health; critical text, number precision, spread and fallback color/text |
| **Color rules** | Colors and labels for different hits, damage ranges, damage types, weapon filters and rule priority |
| **Health HUD** | Bar style, horizontal or vertical layout, dimensions, colors, text, damage trail and time on screen |
| **Highlight** | Target glow color, brightness and thickness, or diagnostic bounding boxes |
| **Reticle** | Independent melee and ranged designs, with shape, outline, center marker, colors, opacity, size, rotation and offsets |

**Set up your reticles.** Open **Reticle** and enable **Custom reticle**. Select **Melee profile** or **Ranged profile** to edit that design. The preview pane shows both reticles without a target model; scroll to see the second preview. **Profile selection → AUTO** switches profiles with your weapon. Choose **MELEE** or **RANGED** to keep one profile active. Turn off **Use custom profile** to use the game's reticle for that profile.

**Position your health HUD.** In **Health HUD**, choose **Place HUD** to open a full-screen sample. Pick a screen anchor, adjust the offsets or use the direction buttons, then choose **Back to settings** to continue editing.

**Apply your changes.** Previews show your draft. Select **Save changes** to apply and save it. **Reload saved** restores your saved settings; **Defaults** loads the server's default appearance into the draft. Closing the panel without saving discards unsaved edits.

## Share your appearance

Save your settings in the panel, then use `/crity export <name>` to share your damage, HUD, highlight and reticle appearance on the server. Other players can apply it with `/crity import <name>`. Use `/crity exports` to see the available names.

The [configuration guide](CONFIGURATION.md) covers every setting and command, including direct reticle commands. `/crity help` lists commands in game; `/crity diagnostics` reports feature availability for troubleshooting.

## Support

[Report an issue](https://github.com/yetazero/crity/issues) with your Crity and Hytale versions,
steps to reproduce the problem, and a screenshot or relevant log excerpt.
For compatibility problems, include the output of `/crity diagnostics`.

## Development

See [Building Crity](BUILDING.md) for requirements, automated checks, and release publishing.
