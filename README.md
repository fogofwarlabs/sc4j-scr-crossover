# SC:R Crossover

**English** | [한국어](README.ko.md)

A clone of the [SC:R mod](https://github.com/fogofwarlabs/sc4j-scr) as its own SC4J mod (mod id `scr-crossover`, jar `scr-crossover.jar`): its own LAN join version, changed Terran Marine values, and two added units, the Terran Unity-chan (Barracks) and the Zerg Myriarch (a Hive morph). It reads the game data from the player's own StarCraft: Remastered installation.

The [SC:R clone modder guide](docs/scr-crossover.md) explains every step with this mod as the example.

- [Layout](#layout)
- [Clone and overlay](#clone-and-overlay)
- [Build and run](#build-and-run)
- [Art licences](#art-licences)
- [Releases](#releases)
- [Licence](#licence)

## Layout

| Path | Contents |
|---|---|
| `scr/` | the SC:R mod, git submodule of [sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr) |
| `overlay/` | files that replace or add to the cloned sources (`overlay/<module>/src/main/...`) |
| `overlay.lock`, `overlay-base/` | SHA-256 and copy of each SC:R mod file an overlay file is based on |
| `src/clone`, `src/convert` | clone tool and art converter (build time only) |
| `src/mod` | the mod's own classes and resources |
| `src/test` | tests; they read the built jars |
| `third-party/` | art sets of the added units with their licence files |

The clone is a separate mod: own settings, storage and logs, no rooms or LAN games with the SC:R mod. It shares the StarCraft: Remastered folder setting and the launcher art of the SC:R mod, and runs without `scr.jar`.

## Clone and overlay

| Task | What it does |
|---|---|
| `cloneScrMod` | checks `overlay.lock`, copies `src/main` of every SC:R mod module into `build/clone`, then `overlay/` over it |
| `overlayDiff` | shows the SC:R mod's changes to files under the overlay |
| `acceptOverlay` | records the current SC:R mod files in `overlay.lock` and `overlay-base/` |
| `convertArt` | converts the baked frames of the art sets into graphics files |

When the SC:R mod changes a file the overlay replaces, the build stops and names it: run `overlayDiff`, merge the change into the overlay file, run `acceptOverlay`. Keep the overlay small ([guide, section 2](docs/scr-crossover.md#the-overlay)).

## Build and run

Needs JDK 25. Commands run from the repository root (`./gradlew` on Linux).

```powershell
git clone --recurse-submodules https://github.com/fogofwarlabs/sc4j-scr-crossover.git
cd sc4j-scr-crossover
.\gradlew.bat build
```

The engine version comes from `scr/gradle.properties`. An engine checkout at `../sc4j` (or `-Psc4j.engine.dir=<folder>`) is used instead of Maven Central; `-Psc4j.engine.local=false` turns that off.

To update the SC:R mod: `git submodule update --remote scr`, build, merge any stopped overlay file, and commit `scr` with `overlay/`, `overlay.lock` and `overlay-base/`.

| Command | What it does |
|---|---|
| `.\gradlew.bat jar` | `build/libs/scr-crossover.jar` |
| `.\gradlew.bat test` | tests without the installation |
| `.\gradlew.bat integrationTest` | tests with the installation (`sc4j.data`) |
| `..\sc4j\gradlew.bat -p ..\sc4j devInstall` | engine development install `../sc4j/build/dev-install/` |
| `.\gradlew.bat installMod` | copies the jar into `mods/` of that install, or of `-Psc4j.install=<folder>` |
| `.\gradlew.bat run` | runs the jar with the user data of that install |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher` | opens the launcher of the development install |

## Art licences

| Material | Licence | Details |
|---|---|---|
| Unity-chan | Unity-chan License 3.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Unity-chan's rifle (Quaternius) | CC0 1.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Myriarch (DRAGO 3D model by OSCAR CREATIVO) | CC BY 4.0 | [`third-party/myriarch/`](third-party/myriarch/) |

The jar carries each set's `NOTICE.txt` and `license/` under `third-party/<set>/`, and the manifest's `license` line names them. Keep them when you publish a jar with these frames.

## Releases

A pull request from `develop` into `main` sets `version=X.Y.Z` in `gradle.properties` and moves `scr` to a commit with a released `sc4jVersion`. The push to `main` creates the GitHub release `vX.Y.Z` with `scr-crossover.jar` and `scr-crossover.jar.sha256`.

## Licence

[Apache License 2.0](LICENSE); the art sets keep their own licences. Third-party material: [NOTICE](NOTICE).

StarCraft is a trademark of Blizzard Entertainment, Inc. SC4J is not affiliated with or endorsed by Blizzard Entertainment.
