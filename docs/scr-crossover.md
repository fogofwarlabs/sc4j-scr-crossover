# SC:R clone modder guide

**English** | [한국어](scr-crossover.ko.md)

How to make your own mod from the SC:R mod: clone it, change a few files, build one jar, put it into `mods/` and start it from the launcher. SC:R Crossover (this repository, mod id `scr-crossover`) is the example for every step. Mods made from nothing, the mod API, determinism and network play are in the [mod author guide](https://github.com/fogofwarlabs/sc4j/blob/main/docs/mod-guide.md#18-clone-an-existing-mod) and the [API policy](https://github.com/fogofwarlabs/sc4j/blob/main/docs/api-policy.md).

A clone is:

- the SC:R mod's sources (`src/main` of `scr/scr-data` ... `scr/scr-client`) with a few of its files replaced and your own classes added, built into one jar such as `scr-crossover.jar`;
- a separate mod with its own mod id, settings, storage and logs, which never meets SC:R mod players (section 6);
- a jar without StarCraft: Remastered files: the game data is read from the player's installation. Never put installation files into your jar or repository.

You need this repository with its submodule `scr` (`git clone --recurse-submodules`), JDK 25, and a StarCraft: Remastered installation for playing. Commands run in PowerShell from the repository root (`./gradlew` on Linux or macOS).

1. [Clone](#1-clone)
2. [Change](#2-change)
3. [Build](#3-build)
4. [Put into `mods/`](#4-put-into-mods)
5. [Run from the launcher](#5-run-from-the-launcher)
6. [Identity: who your clone plays with](#6-identity-who-your-clone-plays-with)

## 1. Clone

### The copy step

`cloneScrMod` runs the clone tool `ScrModClone` (`src/clone/java`), which:

1. checks the overlay against its lock ([When the SC:R mod changes](#when-the-scr-mod-changes));
2. copies `scr/<module>/src/main` of every module listed in `modules` of `scr/mod.properties` into `build/clone/<module>/src/main`;
3. copies `overlay/` over it: each file replaces the cloned file of the same path or is added.

```powershell
.\gradlew.bat cloneScrMod
Select-String -Path build\clone\scr-client\src\main\resources\sc4j-mod.json -Pattern '"id"'
```

```text
build\clone\scr-client\src\main\resources\sc4j-mod.json:3:  "id": "scr-crossover",
```

The clone keeps the SC:R package names (`org.fogofwarlabs.sc4j.scr...`); each mod runs in its own class loader. Put your own classes into your own package (SC:R Crossover: `org.fogofwarlabs.sc4j.scrcrossover`).

### The build file

| Part of `build.gradle` | What it does |
|---|---|
| source set `clone` | the clone tool (`ScrModClone`, `CloneOverlay`); build time only |
| task `cloneScrMod` | the copy step; reruns when an SC:R source, an overlay file or the lock changes |
| tasks `overlayDiff`, `acceptOverlay` | show and record SC:R mod changes under the overlay ([section 2](#when-the-scr-mod-changes)) |
| source set `mod` | `src/mod/java`, `src/mod/resources` and every module folder of `build/clone` |
| dependencies of `mod` | `sc4j-base` and `sc4j-engine` (provided by the runtime) and the third-party libraries of the SC:R modules |
| compiler arguments of `mod` | `compilerArgs` of `scr/mod.properties`, so unchanged SC:R classes equal those of `scr.jar` |
| source set `convert` | the art converter `ArtConverter`; build time only |
| task `convertArt` | converts the art sets in `third-party/<set>` into graphics files under `build/converted-art` |
| task `jar` | packs `mod`, the converted art and each art set's `NOTICE.txt` and `license/` into `build/libs/scr-crossover.jar`, reproducibly; a duplicate entry fails |
| tests | `src/test`; they read the built jars |

### Your own mod

1. Create a repository `my-mod`, add the SC:R mod as submodule `scr` (`git submodule add https://github.com/fogofwarlabs/sc4j-scr.git scr`), and copy SC:R Crossover's Gradle wrapper, `settings.gradle`, `gradle.properties`, `build.gradle` and `src/clone/java` into it, and `scr/scr-client/src/main/resources/sc4j-mod.json` into `overlay/scr-client/src/main/resources/`.
2. In `settings.gradle`, set `rootProject.name` to `my-mod`.
3. In `build.gradle`, set `id` of `sc4jMod` to your mod id and change the descriptions; drop or rewrite the art and test blocks.
4. Give your `sc4j-mod.json` your own id, name and description, then run `.\gradlew.bat acceptOverlay` once.

## 2. Change

### The overlay

A file under `overlay/<module>/src/main/...` replaces the cloned file of the same path or is added. Only `src/main` of the cloned modules can be overlaid. Every overlay file stops your build when the SC:R mod changes it, so prefer, in this order:

1. replacing a small resource the SC:R mod reads (the manifest, `lan-lobby.properties`);
2. your own classes and resources in `src/mod`, called through the SC:R mod's extension points;
3. replacing an SC:R mod source.

SC:R Crossover replaces two resources and adds one content extension.

### When the SC:R mod changes

`overlay.lock` holds one `<hash>  <path>` line per overlay file: the SHA-256 of the SC:R mod file it is based on (line endings not counted), or `added`. `overlay-base/` holds copies of those files. `acceptOverlay` writes both. SC:R Crossover's lock:

```text
<SHA-256 of the SC:R mod's sc4j-mod.json>  scr-client/src/main/resources/sc4j-mod.json
<SHA-256 of the SC:R mod's lan-lobby.properties>  scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties
```

When the SC:R mod changes one of these files, the build stops:

```text
> The SC:R mod changed scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties after the overlay file was made
  from it: see :overlayDiff, merge the change into the overlay file by hand, then run :acceptOverlay.
```

1. `.\gradlew.bat overlayDiff` prints the SC:R mod's change (old base against new base) and yours (old base against your overlay file) as unified diffs.
2. Merge the change into your overlay file by hand, or keep yours.
3. `.\gradlew.bat acceptOverlay` records the new base.

The build also stops when an overlay file is not in the lock or the lock names a missing overlay file.

### The mod identity (`overlay/scr-client/src/main/resources/sc4j-mod.json`)

```json
  "id": "scr-crossover",
  "name": {"en": "SC:R Crossover", "ko": "SC:R Crossover"},
```

The mod id names the settings file (`userdata/settings/scr-crossover.json`), the storage folder (`userdata/mods/scr-crossover/`) and the log folder, and is the first part of the online mod identity (section 6). Pick an unused id (lower case letters, digits, hyphens). Keep `entry` and `settings` of the SC:R mod; the mod plugin writes `version` and `engine`. The `license` line and the end of each `description` carry the art notation ([below](#the-license-notation-in-overlayscr-clientsrcmainresourcessc4j-modjson)).

### The LAN join version (`overlay/scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties`)

```properties
joinVersion=0xD005
```

The SC:R mod uses `joinVersion=5`. Native SC:R LAN games carry no mod id; a host, like a native SC:R host, refuses a join command with another lobby version (`LanLobby.JOIN_VERSION`, read from this file). Pick your own 16-bit number; clones with the same number see each other's games. Change it whenever the added units or their values change: replays and LAN games hold their ids and values.

### The content extension (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverContentExtension.java`)

The SC:R mod calls the `ScrContentExtension` implementations (package `org.fogofwarlabs.sc4j.scr.game.extension`) that the jar names:

- `extendInstalledData(GameData installed)`: once after the installation's data is loaded, before any game is made from it. Register added unit types here; after the first game `GameData.getAddedUnitTypes()` refuses `add` with an `IllegalStateException`.
- `extendGameData(GameData game)`: once per game, live or replay, after the game copies the installed tables and before a Use Map Settings map applies its unit settings. Values set here act like changed data files.

SC:R Crossover's extension calls `ScrCrossoverUnitUtils.registerUnit` and `ScrCrossoverValueUtils.applyValues`. The service file `src/mod/resources/META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension` names it:

```text
org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverContentExtension
```

### The values (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverValueUtils.java`)

The Terran Marine costs 45 minerals and trains in 300 frames; the Gauss Rifle deals 7 damage. `extendGameData` writes them into every game's tables.

### Adding a unit with its own graphics

SC:R Crossover adds the Terran **Unity-chan** and the Zerg **Myriarch**, a dragon a Hive morphs into, registered in that order from `extendInstalledData` (ids 233 and 234). Each has a registration class, an art set with its licence files, and uses the shared parts: `ScrCrossoverUnitGraphics` (images, script, sprite, flingy entry and interface art from an art set), `ScrCrossoverArtFiles` (art files), the `convertArt` step and the licence notation in the manifest.

#### The unit (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverUnitUtils.java`)

`registerUnit` uses the extension API (package `org.fogofwarlabs.sc4j.scr.content` of `scr-data`):

- `new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, units)` starts from the Terran Marine; `setValue` sets 50 hit points, 75 minerals, 25 gas, 360 frames and 1 supply. The base type must be a trained unit without a subunit.
- By reference from the installation: the Terran Ghost's weapons and their sound, the Terran Medic's voice and death sound, the Marine's requirement (`setRequirementType`, a Barracks).
- Size of the Terran Goliath (`ScrCrossoverUnitUtils.SIZE_TYPE`): extents 16/16/15/15 and placement box 32 x 32 (`setValue`); on the sprite the 48 pixel selection circle and health bar width 34 (`ScrAddedSprite.Builder.setValue`), the circle 9 pixels below the sprite position (the art set's ground offset). Collision and pathing are a Goliath's; the size class stays the Marine's.
- `setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_BARRACKS, 5, 'u', "Train Unity-chan"))`: Barracks button position 5, hotkey U. A position the producer's own buttons use hides the button.
- `gameData.getAddedUnitTypes().add(unit.build())` returns the SC:R id, 233 for the first added type (after none 228 and the trigger groups 229..232). Replays hold this id, so they play back only with the same jar.
- Graphics, each entry referring to the one before: shadow image (`ScrAddedImage`), script (`ScrAddedScript` with `IscriptCode`), unit image, sprite (`ScrAddedSprite`) and flingy entry (`ScrAddedFlingy`, named by `UnitsDat.FLINGY_TYPE`). The script has an idle loop that creates the shadow with `imgul`, a walk of 4 pixels per frame, ground and air attacks that play the weapon's own attack (`attackmelee` for melee, `attack` for a bullet), and a death that holds its last frame. Frames come from the jar through `ScrModGraphics.Builder(files, grpPath).setHdAnimation(..).setHd2Animation(..)`; frame numbers, play orders and waits come from the converted `animations.json`. Every animation ends in a loop or `end`. The attack's `ignorerest` is followed by the idle loop.
- Interface art from the jar: portrait (`ScrModPortrait.Builder(files, sdIdle, sdTalking).setHd2(..).setHd(..).setIdleChances(85, 5, 5, 5)`, `graphics.addPortrait`, `UnitsDat.PORTRAIT`), command icon (`ScrModSheetFrame`, `graphics.addCommandIcon`, `setCommandIcon`) and wireframe (`ScrModWireframe`, `graphics.addWireframe`, `setWireframe`). Without them a type shows those of its `PORTRAIT` value and wireframe type (`setWireframeType`).
- Computer players never build added units.

Added units cannot be based on a building or subunit, have no per-player limit, and are not addressed by saved games, EUD or map unit settings. They can have their own weapon (`ScrAddedWeapon`), requirement script (`ScrRequirementScript`, `setRequirementScript`) and be a building morph result (the Myriarch).

#### The license notation (in `overlay/scr-client/src/main/resources/sc4j-mod.json`)

```json
  "license": "Unity-chan: © Unity Technologies Japan/UCL, Unity-chan License 3.0. Rifle: Quaternius, CC0 1.0. Myriarch: DRAGO by OSCAR CREATIVO, CC BY 4.0.",
```

Each `description` ends with "Unity-chan: © Unity Technologies Japan/UCL (Unity-chan License 3.0)." (and its Korean form). The Unity-chan License requires the notation to be shown with the work; the launcher's mod details show the description and the `license` line.

#### Art sets (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverArtSet.java`)

An art set is a folder `third-party/<set>/` (baked frames, interface art, `NOTICE.txt`, `license/`) and a `ScrCrossoverArtSet` constant with its name, SD canvas, ground offset, shadow offset, selection circle offset and baked quality. An overlay image (the Myriarch's fire breath) is a set without shadow and interface art inside its unit's folder. `ArtConverter` converts every set, `jar` packs it with its licence files under `third-party/<set>/`, and `ScrCrossoverArtFiles` makes its SD frames. The set's methods (`getMainAnimation(quality)`, `getMainFrames()`, `getCommandIcon(quality)`, ...) give the jar paths for the registration.

| Set | Unit | SD canvas | Ground offset | Shadow offset | Baked at | Frame files |
|---|---|---|---|---|---|---|
| `unity-chan` | Unity-chan | 128 | 9 | 0 | HD (160 pixels per meter) | `main-hd.anim`, `main-hd2.anim` and the shadow's |
| `myriarch` | Myriarch | 252 | 42 | 42 | HD2 (80 pixels per meter) | `main-hd2.anim` and the shadow's; HD draws them too |
| `myriarch/fire` | fire breath (overlay) | 252 | 0 | - | HD2 | `main-hd2.anim` |

- SD canvas: at most 255, a multiple of 4. Ground offset: SD pixels from the image position down to the frames' pivot. Shadow offset: SD pixels from the image position down to the shadow image (42 for flyers, as `imgul` shadow `0 42` of the native air units).
- Baking a large unit at HD2 makes its frame files a quarter of the HD size; the renderer draws them at the same size in HD.
- A frame file has the `emissive` layer only when a baked frame has one.
- A unit set with an overlay has `overlay-offsets.json`: per animation, frame and direction the overlay root (the Myriarch's mouth) in SD pixels from the image position. The registration passes it to `ScrAddedImage.Builder.setOverlayOffsets`; the attack's `imgoluselo` creates the overlay there.
- To add a set: put its folder into `third-party/` with its licence files and `NOTICE.txt`, add a `ScrCrossoverArtSet` constant and its name to the set lists of `ArtConverterTest` and `CloneArtScenario`, and register the unit.

#### The Myriarch (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverMyriarchUtils.java`)

- Base type the Zerg Guardian (flyer, air elevation, voice, Zerg flyer upgrades), trait `UnitTraitFlags.BUILDING_MORPH_RESULT` (`setTrait`): the Hive morphs into it and the completed unit flies where the Hive stood.
- Button `new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HIVE, 9, ScrAddedUnitType.ProductionAction.BUILDING_MORPH, 'm', text, Map.of("koKR", koreanText))`; each `ScrAddedUnitType.TrainButton.Text` holds the enabled tooltip ("Mutate into Myriarch") and the disabled one ("Myriarch Requires:", "   Ultralisk Cavern", "   Defiler Mound").
- Requirement script: `new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HIVE).requireNotBusy().requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN).requireCompleted(ScrUnits.ZERG_DEFILER_MOUND).build()`.
- Values: 400 minerals and 400 gas (charged at the start), 2400 frames, 2000 hit points, armour 3, 8 supply, sight 10, extents 30/25/29/24, placement box 64 x 64, transport space 255.
- Flight: the Guardian's flingy entry (top speed 640, acceleration 27, halt distance 7585, turn rate 20). The Hive's selection circle and health bar width; circle 16 pixels below the image position.
- Fire breath: its own weapon (`gameData.getAddedUnitTypes().addWeapon(new ScrAddedWeapon.Builder(type, flameThrower, weapons)...)`, `weapons.dat` id 131) from the Firebat's Flame Thrower: air and ground targets, range 96, damage 12 + 1 per Zerg flyer attack upgrade, cooldown 30, splash radii 24/32/40. The action frame creates the overlay of the `myriarch/fire` set at the mouth (`imgoluselo`) and launches projectiles 32, 72 and 112 pixels ahead (`attkshiftproj`). The attack continues into the idle loop after `gotorepeatattk`, so the unit keeps hovering.
- On completion the Hive becomes the Myriarch: its Larvae lose their parent, its supply and counts are gone. Cancel restores the Hive with a 3/4 refund.

`ScrCrossoverMyriarchTest` plays the morph, the flight and the fire breath in a live game and the morph and flight in a LAN game of two clones.

#### The Myriarch art and its license (`third-party/myriarch/`)

Rendered from "DRAGO 3D MODEL FREE BY Oscar Creativo" by OSCAR CREATIVO (Sketchfab, CC BY 4.0), re-posed and animated by Fog of War Labs: `idle` 8 frames, `walk` 16, `attack` 10 (action frame 4), `death` 10, flown 1.41 m above the ground, 17 directions, plus portrait clips, wireframe strips and command icon. The fire breath (`fire/`, 6 frames with an emissive layer) plays with the attack frames above the unit.

CC BY 4.0 requires credit, a link to the licence and a note of changes, and passes on to anyone who receives the frames or the jar. `NOTICE.txt` (credit, changes, "not endorsed") and `license/CC-BY-4.0.txt` go into the jar under `third-party/myriarch/`; the manifest's `license` line names the credit. Keep all three when you publish a jar with these frames.

#### The Unity-chan art and its licenses (`third-party/unity-chan/`)

| File | Contents |
|---|---|
| `metadata.json` | description of the baked frames (below); not in the jar |
| `hd/<animation>/dDD_fFF*.png` | baked HD frames of `idle`, `walk`, `attack`, `death`; not in the jar |
| `NOTICE.txt` | notation "(c) Unity Technologies Japan/UCL", licence, derivation, rifle origin, "not official"; in the jar |
| `license/` | Unity-chan License 3.0 (English and Japanese PDF; Japanese prevails), notation rules (`03Indication of License_EN_UCL2.02.pdf`), logos, the rifle's notes (`THIRD-PARTY-GUN.txt`) and CC0 text; in the jar |

- The character art is (c) Unity Technologies Japan/UCL under the Unity-chan License 3.0, which binds everyone who receives the frames or a jar made from them. Keep `NOTICE.txt`, `license/` and the manifest notation, do not present the work as official, and read the terms in `license/` before publishing.
- The rifle is AssaultRifle2_1 of Quaternius's Ultimate Guns Pack, CC0 1.0.
- The repository and jar hold rendered images only: no Unity-chan model, texture or animation, and no StarCraft: Remastered file.
- Other art: check that its licence allows giving away mods and derived images, put it with its licence files and `NOTICE.txt` into `third-party/<name>/`, and show the required notation in the manifest.

#### Baking the frames

The 3D models and bake tools are not in the repository; the build starts from the checked-in frames. Render your own unit into the format `sc4j-bake-intermediate 1`:

- `metadata.json`: `"format": "sc4j-bake-intermediate 1"`, `"quality"` (`"hd"` or `"hd2"`), `"directions": {"count": 17}` and `"animations"` in frame set order, each with `frameCount`, `frameSize` `{"<quality>": [width, height]}`, `pivot` `{"<quality>": [x, y]}` (the ground point from the top-left corner; size and pivot multiples of 4 for HD, 2 for HD2, the same for all frames and layers), `playOrder`, `durationMs` (one per frame), `loop`, `holdLastFrame`, `emissiveFrames` and `actionFrames`. Other fields are ignored. The script expects the animations `idle`, `walk`, `attack` and `death` and attacks at the first action frame.
- PNGs in `<quality>/<animation>/`: 17 directions `DD` (`00` north, `08` east, `16` south, clockwise in 11.25 degree steps; directions 17..31 are mirrored), HD 4 or HD2 2 pixels per SD pixel (SC:R Crossover: 160 or 80 pixels per meter, orthographic camera 42 degrees down). Per frame `FF`: `dDD_fFF.png` color (RGBA, straight alpha), `_mask` (team color, 255 = full), `_shadow` (black with alpha), and for the lit HD mode `_bright` (unlit albedo), `_normal` (x in red, y in alpha, 0..255 for -1..1, +x right, +y up), `_specular` (RGB or gray), `_ao_depth` (green AO, alpha height) and `_emissive` (only for `emissiveFrames`). Unread channels may be 0.
- Keep lighting data within the installed units' ranges: SC:R Crossover halves the normals' tilt, keeps specular at least 41 and depth below about 0.45. A model with a dark glossy base color (the Myriarch) gets `_bright` = color frame divided by the renderer's directional light (lambert of `_normal` times AO, at least 0.5), scaled by 0.88.
- Without `bright`, `normal`, `specular` and `ao_depth` the unit is drawn unlit in the lit HD mode.
- Interface art per quality folder `sd`, `hd2`, `hd`: `portrait/<quality>/Fid00..03.webm` and `Tlk00..02.webm` (WebM VP9, 10 fps; SD 60 x 56, HD2 224 x 240, HD 448 x 480); `wireframe/<quality>/wirefram.png` (square cells: 0..3 damage part masks, 4 and 5 shield outlines; SD 384 x 64, HD2 1024 x 128, HD 2048 x 256), `wireframe/sd/grpwire.png` and `tranwire.png` (192 x 32); `icon/<quality>/cmdicon.png` (gray; SD 32, HD2 64, HD 128). Sides are multiples of 4.

#### Converting the art (`convertArt`)

`ArtConverter` (`src/convert/java`) writes per set into `build/converted-art/third-party/<set>/`; `jar` packs `graphics/`, `interface/` and `portrait/`.

```powershell
.\gradlew.bat convertArt
Get-ChildItem -Name build\converted-art\third-party\unity-chan\graphics
```

```text
animations.json
main-hd.anim
main-hd2.anim
sd-colors.png
sd-masks.png
shadow-hd.anim
shadow-hd2.anim
```

- `main-hd.anim`, `main-hd2.anim`: one frame per direction and frame (Unity-chan 425), layers `diffuse`, `bright`, `teamcolor`, `emissive`, `normal`, `specular`, `ao_depth`, DXT5, each layer an atlas 2048 HD texels wide (1024 at HD2), doubled in width whenever it would exceed 16384 texels high. A set baked at HD2 gets only `-hd2` files. `normal` keeps red and alpha, `ao_depth` green and alpha. The team color area of `diffuse` and `bright` becomes light gray by brightness. Frames are cropped and placed on the set's SD canvas, the ground point the ground offset below the unit position.
- `shadow-hd.anim`, `shadow-hd2.anim`: the shadow image (black, baked alpha doubled).
- `sd-colors.png`, `sd-masks.png`: true color SD frames, one canvas per frame, and their team color (red) and shadow (green) masks.
- `animations.json`: per animation first frame set, frame count, play order, waits in game frames (duration / 42 ms, at least 1), loop, hold and action frames.
- `interface/cmdicon-<quality>.dds.grp`, `wirefram-<quality>.dds.grp`, `grpwire-sd.dds.grp`, `tranwire-sd.dds.grp`: one-frame DXT5 sheets in the format of the installed `cmdicons.dds.grp` and `wirefram.dds.grp`, texture scale (1, 2, 4) in the version word; `portrait/<quality>/` holds the clips unchanged.

```powershell
.\gradlew.bat test --tests org.fogofwarlabs.sc4j.scrcrossover.convert.ArtConverterTest
```

#### SD frames are made when the mod loads

SD frames hold indices of the installation's tileset palettes, which the jar may not hold. `ScrCrossoverArtFiles` makes `main.grp` and `shadow.grp` of each set the first time the mod reads them, from the true color SD images and the 8 installed palettes: pixels with alpha 128 or more are drawn; team mask 128 or more gives a team color index 8..15 by brightness; other pixels get the nearest of the palette indices 16..247 that hold the same color in all 8 palettes; shadow mask 128 or more sets a shadow pixel.

#### Checking the unit

```powershell
.\gradlew.bat integrationTest --tests org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverUnitUtilsTest
```

Needs `sc4j.data`. It checks the registration and frames, a replay recorded and played in two separate runs with equal per-frame hashes, walking, fighting and dying, a group following many commands, and the interface art. `ScrCrossoverArtFilesTest` checks the SD frames of every set. In the game: build a Barracks and press U.

## 3. Build

```powershell
.\gradlew.bat jar
.\gradlew.bat test
```

`jar` builds `build/libs/scr-crossover.jar` (after `cloneScrMod` and `convertArt`). `test` checks the jar against `scr.jar` (only overlay files, own classes, the service file and `third-party/` differ), the overlay and lock, the art conversion, LAN and online refusals on 127.0.0.1, and the launcher listing. Tests that need the installation run with `integrationTest` and `sc4j.data`.

## 4. Put into `mods/`

A mod is installed by putting its jar into `<install>/mods/`; `scr.jar` is not needed there. With the engine checkout at `../sc4j`:

```powershell
..\sc4j\gradlew.bat -p ..\sc4j devInstall
.\gradlew.bat installMod
```

`installMod` copies the jar into `../sc4j/build/dev-install/mods/`, or into `-Psc4j.install=<folder>`, and fails when the install does not exist.

## 5. Run from the launcher

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat --list
```

With the SC:R mod installed too and StarCraft: Remastered found:

```text
Mods in <engine checkout>\build\dev-install\mods
READY   demo                 demo.jar
READY   scr-crossover        scr-crossover.jar
READY   scr                  scr.jar
```

Both mods share the folder setting `gameFolder` (`"shared": "starcraft-remastered"`, stored in `userdata/shared-settings.json`). It is found where Windows records the game; otherwise both mods show `WARNING` with `value.required (gameFolder)` until it is set. The text language is kept per mod (`userdata/settings/scr-crossover.json`).

Open the launcher (`..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat` or `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher`), choose "SC:R Crossover", check the folder and language, and start.

## 6. Identity: who your clone plays with

The runtime computes a mod identity from the engine version, mod id, mod version and jar content; only players with the same identity play together.

- Online (Beacon.Link): the clone never lists SC:R mod rooms; joining one is refused as "another mod".
- LAN: refused through the lobby version in `lan-lobby.properties`.
- Replays and saved games stay in the clone's storage folder.

Every change to the jar is a new identity: players need exactly the same jar.
