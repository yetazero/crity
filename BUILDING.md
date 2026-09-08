# Building Crity

Developer instructions for compiling, checking, and publishing Crity.
To install the mod, download a JAR from [Releases](https://github.com/yetazero/crity/releases).

## Requirements

- JDK 25 or newer.
- Network access to download Gradle, Kotlin, and the Hytale server API.

The Gradle wrapper pins Gradle 9.7.1 and verifies its distribution checksum.
The build uses Hytale's official Maven repository with API version `0.7.0-pre.1`.

## Build and check

```bash
./gradlew --no-daemon clean check jar
```

On Windows, use `gradlew.bat`. The output is `build/libs/Crity-<version>.jar`;
the version is read from `src/main/resources/manifest.json`.
Kotlin's runtime is bundled in the mod JAR. The Hytale server is a compile-time
and test dependency and is not bundled.

`check` runs the `checkCombat` regression suite, covering damage display,
configuration validation and migration, HUD layouts, settings events, target
highlighting, and integration failure handling. Client rendering, animation,
and performance require in-game testing.

Reticle checks cover profile independence, configuration migration, all 16 shapes,
continuous contours, geometry bounds and preview updates. Set
`CRITY_RETICLE_PREVIEW_DIR` to export PNG samples and native UI layouts while
running `checkCombat`. No game assets are needed for reticle generation.

## Use a local server API

```bash
./gradlew clean check jar -PhytaleServerJar=/path/to/HytaleServer.jar
```

`HYTALE_SERVER_JAR` is also supported. The Gradle property takes precedence over
the environment variable. Changing the dependency alone does not establish
compatibility with another game version.

## GitHub Actions

**Build Crity** runs checks and builds the JAR on pushes to `main`, version tags,
and pull requests. It can also be started with **Run workflow**. Successful runs
provide a `Crity-<version>` artifact containing the JAR.

## Publish a release

1. Update the mod version in `src/main/resources/manifest.json` and commit the release changes.
2. Create and push a version tag.
3. Create a draft GitHub Release for that tag and write its release notes.
4. Open **Actions → Publish release JAR → Run workflow**. Use workflow branch `main` and enter the release tag.

The workflow checks out the tag, runs its checks, builds the JAR, attaches it to
the release, and publishes the draft. Re-running replaces the attached JAR.
The tagged source must include the Gradle wrapper and build configuration.

## Version reference

| Git tag | Crity | Hytale |
| --- | --- | --- |
| `Crity0.6.0` | 2.9.1 | 0.6.0 |
| `Crity0.7.0-PRE1` | 2.9.8 | 0.7.0-pre.1 |
| `Crity0.7.0-PRE1-v2` | 3.2.4 | 0.7.0-pre.1 |

The legacy 2.9.1 source does not include the Gradle wrapper and requires its
matching Hytale 0.6.0 API. It cannot use the current publishing workflow.
