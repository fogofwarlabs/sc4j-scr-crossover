# SC:R Crossover

**English** | [한국어](README.ko.md)

A mod for SC4J: a clone of the [SC:R mod](https://github.com/fogofwarlabs/sc4j-scr) under its own identity, mod id `scr-crossover`, jar `scr-crossover.jar`. It has its own LAN join version, changed values of the Terran Marine and two added units with their own graphics: the Terran Unity-chan, trained at the Barracks, and the Zerg Myriarch, a dragon a Hive morphs into.

Like `scr.jar`, the jar holds no StarCraft: Remastered assets or data files: the game's graphics, sounds and data are read from the player's own installation at run time.

The [SC:R clone modder guide](docs/scr-crossover.md) shows every step with this mod as the example.

- [Relation to the SC:R mod](#relation-to-the-scr-mod)
- [Layout](#layout)
- [Clone and overlay](#clone-and-overlay)
- [Build and run](#build-and-run)
- [Art licences](#art-licences)
- [Releases](#releases)
- [Licence](#licence)

## Relation to the SC:R mod

The clone is not checked in: the build copies `src/main` of every module of the SC:R mod ([`scr/`](https://github.com/fogofwarlabs/sc4j-scr), the list `modules` in its `mod.properties`) and puts the overlay over it. Unchanged SC:R sources compile with the SC:R mod's compiler arguments, so their classes match `scr.jar`.

The clone is another mod: its own settings, storage and log folders. Online it never lists the rooms of the SC:R mod; on LAN its join version refuses SC:R mod players and theirs refuse it. Both mods share one StarCraft: Remastered folder setting. The clone runs without `scr.jar` in the install.

SC:R Crossover has no launcher art of its own: its `meta/icon.png` and `meta/banner.png` are the SC:R mod's, cloned from `scr/scr-client/src/main/resources/meta/` (sources in `scr/docs/branding/`).

## Layout

| Path | Contents |
|---|---|
| `scr/` | the SC:R mod, the git submodule of [sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr) |
| `overlay/` | the files that replace or add to the cloned sources, in the layout `overlay/<module>/src/main/...` |
| `overlay.lock`, `overlay-base/` | the SHA-256 and a copy of each SC:R mod file an overlay file was made from; written by `acceptOverlay` |
| `src/clone` | the clone tool; build time only |
| `src/convert` | the art converter; build time only |
| `src/mod` | the mod's own classes and resources: the content extension, the values, the added units |
| `src/test` | the tests; they read the built jars |
| `third-party/` | the art sets of the added units with their licence files |
| `docs/` | the SC:R clone modder guide |

## Clone and overlay

| Task | What it does |
|---|---|
| `cloneScrMod` | checks `overlay.lock`, copies the SC:R mod's sources into `build/clone` and copies `overlay/` over them |
| `overlayDiff` | shows the SC:R mod's changes under your overlay files |
| `acceptOverlay` | records the current SC:R mod files in `overlay.lock` and `overlay-base/` |
| `convertArt` | converts the baked frames of every art set into the units' graphics files |
| `jar` | builds `build/libs/scr-crossover.jar` |

When the SC:R mod changes a file the overlay replaces, the build stops and names the file. Run `overlayDiff`, merge the change into the overlay file by hand, then run `acceptOverlay`.

Keep the overlay small: prefer replacing a small resource, then own classes behind the SC:R mod's extension points, and only then an SC:R mod source. SC:R Crossover replaces two resources (`sc4j-mod.json`, `lan-lobby.properties`) and adds one content extension.

## Build and run

You need JDK 25; playing needs a StarCraft: Remastered 1.23.10 installation, the build does not. Commands run in PowerShell from the repository root; on Linux use `./gradlew`.

```powershell
git clone --recurse-submodules https://github.com/fogofwarlabs/sc4j-scr-crossover.git
cd sc4j-scr-crossover
.\gradlew.bat build
```

The SC:R mod is the git submodule `scr`. The build includes it and takes the engine version from `scr/gradle.properties`; the engine modules and the mod plugin come from Maven Central or its snapshots.

To move to a newer SC:R mod, run `git submodule update --remote scr` and build. When the build stops at an overlay file, merge it as in [Clone and overlay](#clone-and-overlay). Commit `scr` together with `overlay/`, `overlay.lock` and `overlay-base/`.

With the engine checkout [sc4j](https://github.com/fogofwarlabs/sc4j) next to this one (`../sc4j`), the build uses that checkout instead. `-Psc4j.engine.dir=<folder>` names another engine checkout; `-Psc4j.engine.local=false` turns it off.

| Command | What it does |
|---|---|
| `.\gradlew.bat jar` | builds `build/libs/scr-crossover.jar` |
| `.\gradlew.bat test` | the tests that need nothing outside the repository |
| `.\gradlew.bat integrationTest` | the tests that need the installation (Gradle property `sc4j.data`) |
| `.\gradlew.bat installMod` | copies `scr-crossover.jar` into `mods/` of the install: with the engine checkout, its development install `../sc4j/build/dev-install/`; without it, the install `-Psc4j.install=<folder>` |
| `.\gradlew.bat run` | runs `scr-crossover.jar` in the engine runtime with the user data of that install (`build/sc4j-run/` without one) |
| `..\sc4j\gradlew.bat -p ..\sc4j devInstall` | makes the development install of the engine checkout; run it before `installMod` |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher` | opens the launcher of that development install |

`installMod` fails when the install does not exist.

## Art licences

| Material | Licence | Details |
|---|---|---|
| Unit art: Unity-chan | Unity-chan License 3.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Unit art: the rifle Unity-chan holds (Quaternius) | CC0 1.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Unit art: the Myriarch (DRAGO 3D model by OSCAR CREATIVO) | CC BY 4.0 | [`third-party/myriarch/`](third-party/myriarch/) |

The build packs each art set's `NOTICE.txt` and `license/` into the jar under `third-party/<set>/`, and the manifest's `license` line names the notation. Keep them when you publish a jar with these frames.

## Releases

A release is a pull request from `develop` into `main` that sets `version=X.Y.Z` in `gradle.properties` and moves the submodule `scr` to a commit whose `sc4jVersion` is a released engine version; the push to `main` creates the GitHub release `vX.Y.Z` with `scr-crossover.jar` and its checksum `scr-crossover.jar.sha256`.

## Licence

SC:R Crossover is licensed under the [Apache License 2.0](LICENSE); the art sets keep their own licences above. [NOTICE](NOTICE) lists the third-party material.

StarCraft is a trademark of Blizzard Entertainment, Inc. SC4J is not affiliated with or endorsed by Blizzard Entertainment. SC:R Crossover reads the game data of the player's own StarCraft: Remastered installation; the repository and its distributions carry no such data.
