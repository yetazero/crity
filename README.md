# Crity

Colored damage numbers and a target health HUD for **Hytale 0.7.0-PRE-1**.

Mod version: **2.9.8**. Server version identifier: `0.7.0-pre.1`.

## Features

- Cyan, green, yellow and red damage tiers.
- One floating label per damage event, including `CRIT! 42` for the highest tier.
- Random horizontal spread with signed angles.
- Target health HUD with a trailing damage indicator.
- Per-player display settings and optional hit diagnostics.

The CRIT label is based on a weapon damage-range heuristic. It does not represent
an authoritative engine critical-hit flag. Separate damage events remain separate.

## Install

This repository contains source code only. Build `Crity-2.9.8.jar` using the
instructions below and place it in your Hytale mods directory while the world/server
is stopped. Replace the previous
Crity JAR instead of keeping multiple versions, then restart the world/server.

The default Linux pre-release mods directory is:

```text
~/.local/share/Hytale/data/pre-release/Mods/
```

## Commands

| Command | Effect |
| --- | --- |
| `/crity` | Show current settings |
| `/crity damage on` | Colored damage numbers |
| `/crity damage default` | Standard white damage numbers |
| `/crity damage off` | Hide damage numbers |
| `/crity health on` | Target health HUD |
| `/crity health default` | Standard entity health bar |
| `/crity health off` | Hide health display |
| `/crity debug on` | Log your damage events and outgoing text |
| `/crity debug off` | Disable diagnostics |

Settings are saved in `crity_players.json` in the server working directory.
Diagnostics are disabled after a restart. Entity UI changes apply on the next hit;
the custom target HUD is removed immediately when it is disabled.

## Build

Requirements: JDK 25+. The Gradle wrapper downloads the pinned Hytale server API
from the official Maven repository.

```bash
./gradlew --no-daemon clean check jar
```

Output: `build/libs/Crity-2.9.8.jar`. The Gradle `checkCombat` task runs the standalone
regression checks. Kotlin stdlib is bundled; the Hytale server JAR is not bundled.

GitHub Actions also builds downloadable JAR artifacts. See [BUILDING.md](BUILDING.md)
for details and the restored version tags. The older shell scripts and
[Russian build notes](README-BUILD.md) remain available for the local CLI workflow.
