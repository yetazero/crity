# Crity 2.9.8 — Hytale 0.7.0-PRE-1

Target server version: `0.7.0-pre.1`.

- Updated for the pre-release combat text API.
- Fixed negative angles being converted to positive angles; custom numbers now use a random angle from -75° to +75°.
- Critical damage uses a single `CRIT! <amount>` label.
- Replaced stock combat text registration through the supported registry API, with verification.
- Moved target HUD timers onto the world scheduler and added task cleanup.
- Improved command handling and JSON settings persistence.
- Removed unused legacy code, resources and source comments.

Validation: Kotlin CLI and Gradle builds, regression checks for display modes,
packet serialization, repeated hits, signed random angles and configuration files;
isolated server startup and shutdown.

The CRIT label remains a damage-range heuristic. Client rendering requires in-game
verification, and compatibility with other Hytale versions is not claimed.

Source code release; no JAR is attached.
