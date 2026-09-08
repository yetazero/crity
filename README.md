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

## Make it yours

Use the settings panel to edit **Damage**, **Color rules**, **Health HUD**, **Highlight**, and **Reticle**.
Choose **Place HUD** to adjust the health bar on screen, then **Save changes** to apply your settings.
Closing the panel without saving discards your edits.

| Command | Action |
| --- | --- |
| `/crity` | Open the settings panel |
| `/crity help` | List available commands |
| `/crity highlight on` | Enable target highlighting |
| `/crity export <name>` | Save an appearance to a shared server slot |
| `/crity import <name>` | Apply a shared appearance |
| `/crity exports` | List shared appearances |
| `/crity reset` | Reset your appearance to server defaults |

See the [configuration guide](CONFIGURATION.md) for every setting, command, and customization example.

## Support

[Report an issue](https://github.com/yetazero/crity/issues) with your Crity and Hytale versions,
steps to reproduce the problem, and a screenshot or relevant log excerpt.
For compatibility problems, include the output of `/crity diagnostics`.

## Development

See [Building Crity](BUILDING.md) for requirements, automated checks, and release publishing.
