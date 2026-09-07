# Building Crity

## GitHub Actions

Open **Actions → Build Crity** and select a successful run. Download the
`Crity-<version>` artifact and extract the installable JAR from its ZIP.
Pushes to `main`, version tags, and pull requests run the existing regression
checks and build the JAR. **Run workflow** also starts a build manually.

## Publishing a release JAR

Create and push a version tag, then create a **draft Release** for that tag.
Open **Actions → Publish release JAR → Run workflow**, leave the workflow branch
as `main`, and enter the release tag. The workflow checks out that exact tag,
runs its checks, builds its JAR, uploads it to the release, and publishes the
draft. Re-running replaces the JAR attached to the same release. This requires
a tagged version with the Gradle wrapper and portable build configuration;
the restored source-only `Crity0.6.0` tag cannot use this workflow.

## Local build

Install JDK 25 or newer, then run:

```bash
./gradlew --no-daemon clean check jar
```

On Windows, use `gradlew.bat`. The result is `build/libs/Crity-<version>.jar`.
The mod version comes from `src/main/resources/manifest.json`.

The build downloads the pinned `0.7.0-pre.1` server API from Hytale's official
Maven repository. No game installation, server authentication, or repository
secret is required. Kotlin's runtime is bundled; the Hytale server is not.

To compile against your own server JAR instead:

```bash
./gradlew clean check jar -PhytaleServerJar=/path/to/HytaleServer.jar
```

`HYTALE_SERVER_JAR` is also supported. Changing the compile dependency alone
does not establish compatibility with another game version.

## Restored versions

| Git tag | Crity version | Original Hytale target |
| --- | --- | --- |
| `Crity0.6.0` | 2.9.1 | 0.6.0 |
| `Crity0.7.0-PRE1` | 2.9.8 | 0.7.0-pre.1 |
| `Crity0.7.0-PRE1-v2` | 3.2.4 | 0.7.0-pre.1 |

These snapshots were restored from the original source archives. Runtime code,
tests, and game assets are unchanged. The 0.7 snapshots add portable build
infrastructure. The `Crity0.6.0` tag preserves the original source only: its
exact 0.6.0 server API is unavailable in the public Maven repository, so no
replacement JAR has been built against a different API.

Build checks are automated regression checks, not an in-game playtest.
