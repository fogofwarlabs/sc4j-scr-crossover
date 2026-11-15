# SC:R clone modder guide (draft)

**English** | [한국어](scr-crossover.ko.md)

This guide shows how to make your own mod from the SC:R mod of SC4J: you clone it, change a few files, build one mod jar, put it into `mods/` and start it from the launcher. SC:R Crossover (this repository, mod id `scr-crossover`) is made this way and is the example for every step; its build runs the same steps as the guide.

This guide is the clone chapter of the [mod author guide](https://github.com/fogofwarlabs/sc4j/blob/main/docs/mod-guide.md#18-clone-an-existing-mod), which covers mods made from nothing, the mod API, determinism, network play and the [API policy](https://github.com/fogofwarlabs/sc4j/blob/main/docs/api-policy.md).

**What a clone is**

- a copy of the SC:R mod's sources (`scr/scr-data` ... `scr/scr-client`, folder `src/main` of each) with your own versions of a few of its files and your own classes, built into one jar such as `scr-crossover.jar`;
- a separate mod with its own identity: its own mod id, so its own settings, storage and log folders, and it never meets SC:R mod players (section 6);
- like `scr.jar`, a jar without StarCraft: Remastered assets or data files. The game's graphics, sounds and data are read from the player's own StarCraft: Remastered installation at run time. Do not put files of the installation into your jar or your repository.

**What you need**: a checkout of this repository with its submodule `scr` (`git clone --recurse-submodules`), JDK 25, and for playing a StarCraft: Remastered 1.23.10 installation. Commands below run in PowerShell from the repository root; on Linux or macOS use `./gradlew` instead of `.\gradlew.bat`.

**Contents**

1. [Clone](#1-clone): the copy step, the build file, your own mod
2. [Change](#2-change): the overlay and its lock, the mod identity, the LAN join version, the content extension, the values, an added unit with its own graphics
3. [Build](#3-build)
4. [Put into `mods/`](#4-put-into-mods)
5. [Run from the launcher](#5-run-from-the-launcher)
6. [Identity: who your clone plays with](#6-identity-who-your-clone-plays-with)

## 1. Clone

### The copy step

The clone is not checked in. The build makes it: the task `cloneScrMod` runs the clone tool `ScrModClone` (`src/clone/java`), which

1. checks the overlay against its lock (section 2, "When the SC:R mod changes");
2. copies `scr/<module>/src/main` of every module of the SC:R mod (`scr-data`, `scr-game`, `scr-render`, `scr-net`, `scr-client`; the list is `modules` in `scr/mod.properties`) into `build/clone/<module>/src/main`, the folder layout a copy by hand has;
3. copies the overlay `overlay/` over it: every file there replaces the cloned file of the same path, or adds a new one.

```powershell
.\gradlew.bat cloneScrMod
```

Result: `build/clone/` holds the folders `scr-client`, `scr-data`, `scr-game`, `scr-net`, `scr-render`, and the cloned manifest is SC:R Crossover's:

```powershell
Select-String -Path build\clone\scr-client\src\main\resources\sc4j-mod.json -Pattern '"id"'
```

prints the line with SC:R Crossover's id:

```text
build\clone\scr-client\src\main\resources\sc4j-mod.json:3:  "id": "scr-crossover",
```

Why an overlay and not an edited copy: the overlay holds only the files you changed, as normal files you edit. When the SC:R mod changes, your next build clones the new sources; when it changes a file you replaced, the build stops and names the file instead of silently keeping the old SC:R code (section 2).

The clone keeps the SC:R mod's package names (`org.fogofwarlabs.sc4j.scr...`). One mod runs per session, in its own class loader, so two mods with the same package names never meet. Put your own classes into your own package (SC:R Crossover uses `org.fogofwarlabs.sc4j.scrcrossover`).

### The build file

`build.gradle` is the build file of a clone. Its parts:

| Part | What it does |
|---|---|
| source set `clone` | the clone tool (`ScrModClone`, `CloneOverlay`); it runs at build time only and is not in the jar |
| task `cloneScrMod` | checks `overlay.lock`, copies the SC:R mod's sources into `build/clone` and copies `overlay/` over them; reruns when a SC:R source, an overlay file or the lock changes |
| tasks `overlayDiff`, `acceptOverlay` | show the SC:R mod's changes under your overlay files, and record the SC:R mod files after you merged them (section 2) |
| source set `mod` | your mod: `src/mod/java` and `src/mod/resources` (your own classes and resources) plus every module folder of `build/clone` |
| dependencies of `mod` | `sc4j-base` and `sc4j-engine` (the runtime provides them, they are not in the jar) and the third-party libraries of the SC:R mod modules, resolved from those modules (no second list) |
| compiler arguments of `mod` | the SC:R mod's own (`-parameters`), `compilerArgs` in `scr/mod.properties`, so that the classes of unchanged SC:R sources match `scr.jar` byte for byte |
| source set `convert` | the art converter (`ArtConverter`, section 2); it compiles against the clone, runs at build time only and is not in the jar |
| task `convertArt` | converts the baked frames of every art set in `third-party/<set>` (section 2, "Art sets") into the units' graphics files under `build/converted-art`; reruns when a file under `third-party` changes |
| task `jar` | packages the `mod` source set, the output of `convertArt` and each art set's `NOTICE.txt` and `license/` into `build/libs/scr-crossover.jar`: reproducible order, no timestamps, a duplicate entry fails the build |
| tests | `src/test`: they read the built jars, never the clone's classes directly |

### Your own mod

To start your own clone, make a repository of its own as SC:R Crossover is: a Gradle build that includes the SC:R build, the submodule `scr`:

1. Create the repository `my-mod`, add the SC:R mod as its submodule `scr` (`git submodule add https://github.com/fogofwarlabs/sc4j-scr.git scr`), and copy SC:R Crossover's Gradle wrapper, `settings.gradle`, `gradle.properties`, `build.gradle` and clone tool `src/clone/java` into it, and `scr/scr-client/src/main/resources/sc4j-mod.json` into `overlay/scr-client/src/main/resources/`.
2. In `settings.gradle`, set `rootProject.name` to `my-mod`.
3. In `build.gradle`: set `id` of `sc4jMod` to your mod id (the jar becomes `my-mod.jar`) and change the descriptions; drop the art and test blocks or write your own.
4. Edit your `sc4j-mod.json` to your own id, name and description (every clone needs its own mod id), then run `.\gradlew.bat acceptOverlay` once to write `overlay.lock`.

Your commands are the same as below, run in your repository.

## 2. Change

### The overlay

`overlay/` has the folder layout of the clone: `overlay/<module>/src/main/...`. A file there replaces the cloned file of the same path wholesale; a file the SC:R mod does not have is added. The overlay files are normal sources and resources: edit them in your editor and rebuild. Only `src/main` of the cloned modules can be overlaid.

Keep the overlay small. Every overlay file is a copy of an SC:R mod file that you now own, and each change of the SC:R mod to that file stops your build until you merged it. So prefer, in this order:

1. data: replace a small resource the SC:R mod reads (the manifest, `lan-lobby.properties`);
2. your own classes and resources under `src/mod/java` and `src/mod/resources`, which the SC:R mod calls through its extension points (the content extension below);
3. only when neither works, replace an SC:R mod source in the overlay.

SC:R Crossover replaces two resources and adds one extension; it replaces no SC:R mod source.

### When the SC:R mod changes

`overlay.lock` records, for every overlay file, the SHA-256 of the SC:R mod file it was made from (`added` for a file the SC:R mod does not have), one `<hash>  <path>` line per file; line endings do not count. `overlay-base/` keeps a copy of each of those SC:R mod files. Both are written by `acceptOverlay`; do not edit them by hand. SC:R Crossover's lock has two lines:

```text
<SHA-256 of the SC:R mod's sc4j-mod.json>  scr-client/src/main/resources/sc4j-mod.json
<SHA-256 of the SC:R mod's lan-lobby.properties>  scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties
```

When the SC:R mod changes one of these files, the build stops with one line:

```text
> The SC:R mod changed scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties after the overlay file was made
  from it: see :overlayDiff, merge the change into the overlay file by hand, then run :acceptOverlay.
```

Then:

1. `.\gradlew.bat overlayDiff` prints, for each changed file, the SC:R mod's change (base then against base now) and your change (base then against your overlay file) as unified differences.
2. Merge the SC:R mod's change into your overlay file by hand (or decide it does not matter to you).
3. `.\gradlew.bat acceptOverlay` records the current SC:R mod files; the next build goes on.

The build also stops when an overlay file is not in the lock (a new overlay file: run `acceptOverlay`) or the lock names a file the overlay no longer has.

### The mod identity (`overlay/scr-client/src/main/resources/sc4j-mod.json`)

SC:R Crossover's manifest is the SC:R mod's with its own `id`, `name`, `description` and a `license` line:

```json
  "id": "scr-crossover",
  "name": {"en": "SC:R Crossover", "ko": "SC:R Crossover"},
```

Why: the mod id is what makes the clone another mod. It names the settings file (`userdata/settings/scr-crossover.json`), the storage folder (`userdata/mods/scr-crossover/`, replays, saves, options) and the log folder, so the clone never reads or changes the SC:R mod's files; the launcher lists the two side by side; and it is the first part of the mod identity that online rooms compare (section 6). `entry`, `engine`, `version` and the `settings` schema stay the SC:R mod's: the clone starts the same way and asks for the same installation folder. Pick an id nobody else uses (lower case letters, digits and hyphens). The `license` line and the end of each `description` carry the notation of the added unit's art (section "The license notation").

### The LAN join version (`overlay/scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties`)

```properties
joinVersion=0xD005
```

The SC:R mod's file says `joinVersion=5`. Why: native SC:R LAN games carry no mod id, so a LAN host could not tell the clone from the SC:R mod. The host already checks the lobby version in a player's join command and refuses a different one (`LanLobby.JOIN_VERSION` is read from this file); with its own version the clone's players are refused by SC:R mod hosts (native SC:R hosts make the same check), and the clone's host refuses theirs, without any change to the LAN protocol. The version is a 16-bit number: pick your own. Two clones that pick the same number see each other's LAN games. Change it whenever the clone's added units or their numbers change: replays and LAN games hold the added ids and values, so jars with other units would desync.

### The content extension (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverContentExtension.java`)

The SC:R mod calls the content extensions that a mod jar names, through `ScrContentExtension` (package `org.fogofwarlabs.sc4j.scr.game.extension` of `scr-game`):

- `extendInstalledData(GameData installed)`: once the installation's data is loaded (`GameData.load`), before any game is made from it. Every way the clone loads the installation (client sessions, replays, tests) passes here, and the added unit types and graphics of the loaded data stay open only until the first game is made from it (`GameData.getAddedUnitTypes()` refuses an `add` after that with an `IllegalStateException`). Added unit types belong here.
- `extendGameData(GameData game)`: once per game, live or replay, right after the game is created over its own copy of the installed tables and before a Use Map Settings map applies its unit settings. Values written here act like changed data files: every game uses them, and a map's own unit settings still replace them.

SC:R Crossover's extension calls `ScrCrossoverUnitUtils.registerUnit` and `ScrCrossoverValueUtils.applyValues`. The jar names it in the service file `src/mod/resources/META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension`, one class name per line:

```text
org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverContentExtension
```

### The values (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverValueUtils.java`)

The Terran Marine costs 45 minerals and trains in 300 frames, the Gauss Rifle deals 7 damage. The content extension writes them into every game's own tables (`extendGameData`); the values live in SC:R Crossover's package, where the next SC:R update cannot conflict with them.

### Adding a unit with its own graphics

SC:R Crossover adds two units with their own graphics: the Terran **Unity-chan**, drawn from the character Unity-chan, and the Zerg **Myriarch**, a giant dragon a Hive morphs into (below). Each takes a class of SC:R Crossover's own package called from the content extension (`extendInstalledData`; Unity-chan first, so the ids are 233 and 234), an art set with its license files, and the shared parts: the graphics registration (`ScrCrossoverUnitGraphics`: images, script, sprite, flingy entry and interface art from an art set), the art files (`ScrCrossoverArtFiles`), one build step that converts the art and the license notation in the manifest.

#### The unit (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverUnitUtils.java`)

`registerUnit` builds the unit with the SC:R mod's extension API (package `org.fogofwarlabs.sc4j.scr.content` of `scr-data`):

- `new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, units)` starts from every value of the Terran Marine; `setValue` then gives it its own hit points (50), cost (75 minerals, 25 gas), build time (360 frames) and supply (1). The base type must be a trained unit without a subunit (no building, no turret).
- Parts of the installation by reference, never copied: the Terran Ghost's weapons (`setValue` of the weapon fields with the Ghost's values) and the sound of their shot, the Terran Medic's voice and death sound, the Marine's requirement, a Barracks (`setRequirementType`).
- Size: the Terran Goliath's, read from the installation (`ScrCrossoverUnitUtils.SIZE_TYPE`): its extents (16 left, 16 up, 15 right, 15 down) and placement box (32 x 32) with `setValue`, and on the unit's sprite its selection circle (the 48 pixel one) and health bar width (34) with `ScrAddedSprite.Builder.setValue`; the selection circle lies 9 pixels below the sprite position (the Goliath's `SpritesDat.SELECTION_CIRCLE_OFFSET`), where the art puts her feet (the art set's ground offset). Collision and pathing are a Goliath's; the size class (damage taken) stays the Marine's.
- `setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_BARRACKS, 5, 'u', "Train Unity-chan"))`: a button at position 5 of the Barracks, hotkey U (free at the Barracks: Marine M, Firebat F, Ghost G, Medic C, Lift Off L). A position the producer's own buttons already use would be hidden by them (the first visible button wins).
- `gameData.getAddedUnitTypes().add(unit.build())` returns the unit's SC:R id: 233 for the first added type (after the none value 228 and the trigger groups 229..232). Replays and commands hold this id, so a replay plays back only with the same registration, that is the same jar.
- Graphics, each entry referring to the one before: the shadow image (`ScrAddedImage`), the script (`ScrAddedScript` with `IscriptCode`: idle loop that first creates the shadow with `imgul`, walk moving 4 pixels per game frame like the Marine, ground and air attack whose action frame plays the attack of the weapon type's own script (its sound and its attack opcode: `attackmelee` for a melee weapon, which damages at once, `attack` for a weapon with a bullet), death that holds its last frame), the unit image (`ScrAddedImage` with that script), the sprite (`ScrAddedSprite`) and the flingy entry (`ScrAddedFlingy`), which the unit type names (`UnitsDat.FLINGY_TYPE`). Each image's frames come from the jar through `ScrModGraphics.Builder(files, grpPath).setHdAnimation(..).setHd2Animation(..)`. The script's frame numbers, play orders and waits are read from the converted `animations.json`, so the art and the script cannot disagree. Every animation ends in a loop or `end`: the code blocks follow each other, so a block that ran past its end would run the next one. As in the native scripts, the attack's `ignorerest` is followed by the idle loop: without a target it starts the way to idle and goes on with the next instruction.
- Interface art of its own, from the jar: the portrait (`ScrModPortrait.Builder(files, sdIdle, sdTalking).setHd2(..).setHd(..) .setIdleChances(85, 5, 5, 5)`, registered with `graphics.addPortrait` and named by the unit's `UnitsDat.PORTRAIT` value), the command icon (`ScrModSheetFrame`, `graphics.addCommandIcon`, `setCommandIcon`) and the wireframe (`ScrModWireframe` of the single, group and cargo strips, `graphics.addWireframe`, `setWireframe`). A type that names none shows the portrait of its `PORTRAIT` value and the icon and wireframe of its wireframe type (`setWireframeType`).
- The computer players never build the unit: their scripts know the native unit types only.

Limits of added units: no building or subunit as base type, no limit per player, and saved games, EUD and the map's unit settings address the native unit table only. An added unit can have its own weapon (`ScrAddedWeapon`), its own requirement script (`ScrRequirementScript`, `setRequirementScript`) and can be the result of a building morph (the Myriarch, below).

#### The license notation (in `overlay/scr-client/src/main/resources/sc4j-mod.json`)

```json
  "license": "Unity-chan: © Unity Technologies Japan/UCL, Unity-chan License 3.0. Rifle: Quaternius, CC0 1.0. Myriarch: DRAGO by OSCAR CREATIVO, CC BY 4.0.",
```

and one sentence at the end of each `description` line ("Unity-chan: © Unity Technologies Japan/UCL (Unity-chan License 3.0)." and the Korean one). Why: the Unity-chan License asks that the license notation of the character is shown with the work; the launcher's mod details show the description and the `license` line, so every player sees it before starting the mod.

#### Art sets (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverArtSet.java`)

Each added unit's art is one art set: a folder `third-party/<set>/` with its baked frames, interface art, `NOTICE.txt` and `license/`, and one constant of `ScrCrossoverArtSet` that gives its name (the folder), its SD canvas, its ground offset, its shadow offset, its selection circle offset and the quality its frames are baked at. An overlay image of a unit (the Myriarch's fire breath) is a set of its own without a shadow and interface art, in a folder inside its unit's (`third-party/myriarch/fire/`). `ArtConverter` converts every set, `jar` packs every set's converted files, `NOTICE.txt` and `license/` under `third-party/<set>/` of the jar, and `ScrCrossoverArtFiles` makes every set's SD frames. The set's methods give the jar paths of its files (`getMainAnimation(quality)`, `getMainFrames()`, `getCommandIcon(quality)`, ...), which the unit's registration passes to the extension API.

| Set | Unit | SD canvas | Ground offset | Shadow offset | Baked at | Frame files |
|---|---|---|---|---|---|---|
| `unity-chan` | Unity-chan (registered, above) | 128 | 9 | 0 | HD (160 pixels per meter) | `main-hd.anim`, `main-hd2.anim` and the shadow's |
| `myriarch` | the flying dragon Myriarch (below) | 252 | 42 | 42 | HD2 (80 pixels per meter) | `main-hd2.anim` and the shadow's; the HD quality draws them too |
| `myriarch/fire` | the Myriarch's fire breath (an overlay) | 252 | 0 | - | HD2 | `main-hd2.anim` |

- The SD canvas is at most 255 (GRP frame sizes are bytes); keep it a multiple of 4. The ground offset is where the baked frames' pivot (the ground point under the unit) lies below the image position, in SD pixels. The shadow offset is where the shadow image lies below the image position: a ground unit's shadow is drawn at its own position, a flying unit's 42 pixels lower as the native air units' (`imgul` shadow `0 42`), and the converter moves its shadow frames so that their ground point is the shadow image's position.
- A large unit fills a large canvas: baking it at HD2 keeps its frame files at a quarter of the HD size (the Myriarch's `main-hd2.anim` is about 433 MB, the whole Myriarch art about 103 MB packed in the jar) and its baked frames at a quarter of the PNG size. The renderer draws a frame file by its own texture scale, so the HD quality shows the HD2 frames at the same size.
- A frame file holds the layer `emissive` only when a baked frame has one (the Myriarch has none, its fire breath has one in every frame).
- A unit set with an overlay has `overlay-offsets.json`: per animation, frame and direction the overlay's root (the Myriarch's mouth) in SD pixels from the image position. The converter writes it in frame order into the jar, and the registration gives the unit's image these offsets as its attack overlay table (`ScrAddedImage.Builder.setOverlayOffsets`), where the attack's `imgoluselo` creates the overlay.
- To add a set: put its folder into `third-party/` (format below, with its license files and a `NOTICE.txt`), add its constant to `ScrCrossoverArtSet` and its name to the art set list of `ArtConverterTest` and `CloneArtScenario`, and register the unit with the set's paths.

#### The Myriarch (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverMyriarchUtils.java`)

`registerUnit` adds the Myriarch after Unity-chan, with the SC:R mod's building-morph, weapon and overlay seams:

- Base type the Zerg Guardian (a large flyer: the flyer flag and the air units' elevation, the Guardian's voice, the Zerg flyer upgrades), trait `UnitTraitFlags.BUILDING_MORPH_RESULT` (`setTrait`): a building morph into it completes as a unit, and as its type flies, the Hive takes off: the completed unit flies (no ground collision, the air units' elevation) where the Hive stood.
- Button: `new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HIVE, 9, ScrAddedUnitType.ProductionAction.BUILDING_MORPH, 'm', text, Map.of("koKR", koreanText))`, at position 9 of the Hive; each `ScrAddedUnitType.TrainButton.Text` holds the enabled tooltip ("Mutate into Myriarch") and the disabled one, in the words of the installed disabled strings ("Myriarch Requires:", then "   Ultralisk Cavern" and "   Defiler Mound"); the client shows the text of its locale and the default one otherwise.
- Own requirement script (`setRequirementScript`): `new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HIVE).requireNotBusy() .requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN).requireCompleted(ScrUnits.ZERG_DEFILER_MOUND).build()`.
- Values: 400 minerals and 400 gas (charged when the morph starts), 2400 frames, 2000 hit points, armour 3, 8 supply (the morph starts only with 8 free), sight 10, extents 30/25/29/24, placement box 64 x 64, transport space 255 (no transport loads it).
- Flight: the Guardian's flingy entry (top speed 640, acceleration 27, halt distance 7585, turn rate 20, the large air units' flight); the flap does not tie the speed. The Hive's selection circle and health bar width; the circle 16 pixels below the image position.
- Fire breath, as the native Firebat's Flame Thrower on the ground, against ground and air units: its own weapon (`gameData.getAddedUnitTypes().addWeapon(new ScrAddedWeapon.Builder(type, flameThrower, weapons)...)`, `weapons.dat` id 131, the first id after the none id), starting from the Flame Thrower (an invisible projectile that appears at the attacker, enemy splash) with target flags air and ground, range 96, damage 12 + 1 per Zerg flyer attack upgrade, normal damage, cooldown 30, splash radii 24/32/40. The attack's action frame creates the breath (`imgoluselo`, an overlay image of the `myriarch/fire` set with the Firebat flame's sound, at the mouth by the unit image's attack overlay offsets) and launches three projectiles at 32, 72 and 112 pixels in front (`attkshiftproj`, as the Firebat's 24, 52 and 80), each splashing the enemies around it. No projectile outlives its splash. As the native air attackers, it keeps hovering through the cooldown: its attack goes on into the idle loop after `gotorepeatattk` (a ground unit holds its aim frame with `ignorerest`), and the repeated attack plays the whole attack again.
- When the morph completes the Hive becomes the Myriarch at its place: the Larvae lose their parent and die when the creep recedes, the Hive's supply, depot and counts are gone. Cancel restores the Hive with a 3/4 refund.

`ScrCrossoverMyriarchTest` plays the morph in a live game of the clone (button, disabled tooltip, requirements, cost, completion as a flying unit, Larvae and creep), the flight (over terrain no ground unit stands on) and the fire breath on a building and on two air units (damage by splash share, the splash on the second unit, no projectile left), and the morph and flight in a LAN game of two clients of the clone, which stay in sync.

#### The Myriarch art and its license (`third-party/myriarch/`)

The frames are rendered from the dragon "DRAGO 3D MODEL FREE BY Oscar Creativo" by OSCAR CREATIVO (Sketchfab), licensed under CC BY 4.0, re-posed, animated and rendered as sprite frames by Fog of War Labs. The animations: `idle` 8 frames (hovering), `walk` 16 (flight), `attack` 10 (action frame 4, the fire breath), `death` 10 (falling to the ground, last frame held), flown 1.41 m above the ground (the 42 SD pixels of a native air unit's shadow); 17 directions, layers color, mask, shadow, `bright`, `normal`, `specular` and `ao_depth`; the portrait clips, wireframe strips and command icon of the same model. The fire breath (`fire/`, 6 frames with an emissive layer) is not part of the model: each of its frames plays with an attack frame (the same durations), and the overlay is drawn above the unit.

CC BY 4.0 asks for the credit, a link to the license, and a note of the changes; anyone who receives the frames or the jar receives them under the same terms. `NOTICE.txt` holds the author's credit line, the changes and "not endorsed"; `license/CC-BY-4.0.txt` holds the license text; the build packs both into the jar under `third-party/myriarch/`, and the manifest's `license` line names the credit. Keep all three when you publish a jar with these frames.

#### The art folder and its licenses (`third-party/unity-chan/`)

| File | What |
|---|---|
| `metadata.json` | the description of the baked frames (below); input of the conversion, not in the jar |
| `hd/<animation>/dDD_fFF*.png` | the baked HD frames of the animations `idle`, `walk`, `attack`, `death`; input of the conversion, not in the jar |
| `NOTICE.txt` | the notation "(c) Unity Technologies Japan/UCL", the license, what was derived, the rifle's origin, "not official"; in the jar |
| `license/` | the Unity-chan License 3.0 terms (English and Japanese PDF, the Japanese text prevails), the license notation rules (`03Indication of License_EN_UCL2.02.pdf`), the license logos, the rifle's notes (`THIRD-PARTY-GUN.txt`) and its CC0 text; in the jar |

What the licenses ask of you and of the players you give the jar to:

- The character art is (c) Unity Technologies Japan/UCL under the Unity-chan License 3.0 (UCL). Anyone who receives the frames, or a mod jar made from them, receives them under the same license: the players of your jar and anyone they pass it on to are bound by the UCL terms for the art. Keep `NOTICE.txt` and `license/` in the jar (the build packs them under `third-party/unity-chan/`), keep the notation in the manifest, and do not present the work as official or endorsed by Unity Technologies Japan. Read the terms in `license/` before you publish a jar with these frames; the rules of the notation are in the indication PDF.
- The rifle the character holds is the model AssaultRifle2_1 of the Ultimate Guns Pack by Quaternius under CC0 1.0 (public domain dedication): no condition, the credit in `NOTICE.txt` and the manifest is a courtesy.
- Only rendered images are in the repository and the jar, no model, texture or animation file of Unity-chan, and no StarCraft: Remastered file (the voice and the weapon sounds come from the player's installation by reference).
- Using other art: check that its license allows mods and derived images to be given away, put it into its own `third-party/<name>/` folder with its license files and a `NOTICE.txt`, pack those into the jar as `jar` does for Unity-chan, and show the notation the license asks for in the manifest.

#### Baking the frames (not in the repository)

The 3D models and the bake tools are not in the repository, and the build does not need them: it starts from the checked-in frames. To use your own character, render it with any tool into the same format, `sc4j-bake-intermediate 1`, the input of `convertArt`:

- `metadata.json`: `"format": "sc4j-bake-intermediate 1"`, `"quality"` (`"hd"` or `"hd2"`, the quality the frames are baked at, the set's baked quality), `"directions": {"count": 17}` and `"animations"`, an object of animations in frame set order, each with `frameCount`, `frameSize` `{"<quality>": [width, height]}` and `pivot` `{"<quality>": [x, y]}` (the ground point under the unit from the frame's top-left corner; size and pivot multiples of the texture scale, 4 for HD and 2 for HD2, the same for every frame and layer of the animation), `playOrder` (frame indices), `durationMs` (one per frame), `loop`, `holdLastFrame`, for frames with an emissive layer `emissiveFrames`, and for the frames at which the unit acts `actionFrames`. The converter reads these fields only; the others record how the frames were made. SC:R Crossover's animations are named `idle`, `walk`, `attack` and `death` (the unit's script asks for these names and attacks at the attack's first action frame).
- Per animation, direction `DD` and frame `FF`, PNG files in `<quality>/<animation>/`: 17 directions, `00` north, `08` east, `16` south, clockwise in steps of 11.25 degrees (the renderer mirrors them into directions 17..31, so light and shapes should be symmetric enough to mirror); HD 4 or HD2 2 pixels per SD pixel (SC:R Crossover: 160 or 80 pixels per meter, Unity-chan about the native Goliath's height on screen, camera 42 degrees down, orthographic). Files: `dDD_fFF.png` the color (RGBA, straight alpha), `_mask` (team color area, 255 = full), `_shadow` (black with the shadow's alpha), and the lighting layers of the HD lit renderer: `_bright` (the unlit albedo), `_normal` (screen-space normal, x in red and y in alpha, 0..255 for -1..1, +x right, +y up), `_specular` (RGB intensity, or one gray channel), `_ao_depth` (green: ambient occlusion, alpha: height above the ground), and `_emissive` (RGB glow, only for the frames in `emissiveFrames`). The converter clears the channels the renderer does not read (normal green and blue, ao_depth red and blue), so a bake may store them as 0 to keep the files small.
- Keep the lighting data in the ranges of the installed units, or the lit HD mode shades the unit harder than the native units: the renderer has no ambient term, so a strong normal map gives a hard, dark side. SC:R Crossover's bake halves the tilt of every normal towards the viewer (lit brightness and spread then match the unlit color frame), keeps the specular at least 41 (the native Marine's lowest value) and the depth (`_ao_depth` alpha) below about 0.45 (the native Goliath reaches 0.37..0.5).
- `_bright` is the color the renderer lights with its lambert term. A model whose look comes from glossy or metallic surfaces over a dark base color (the Myriarch: base color about 0.1) turns dark and blotchy lit if `_bright` is its base color; its bake divides the color frame by the renderer's directional light (lambert of `_normal` times AO, at least 0.5) and scales it by 0.88, so lit it looks like the unlit frame (lit about 1.07 times as bright, the native Ultralisk 1.01).
- Without the four layers `bright`, `normal`, `specular` and `ao_depth` the unit is drawn without lighting in the lit HD mode (it keeps its team color); the SD and plain HD modes use the color, the mask and the shadow only.
- Interface art, per quality folder `sd`, `hd2` and `hd`: `portrait/<quality>/Fid00..03.webm` (idle clips) and `Tlk00..02.webm` (talking clips) in the format of the installed portrait clips (WebM VP9, 10 frames per second; SD 60 x 56, HD2 224 x 240, HD 448 x 480); `wireframe/<quality>/wirefram.png`, a strip of square cells (cells 0..3 the white masks of the damage parts, 4 and 5 the shield outlines; SD 384 x 64, HD2 1024 x 128, HD 2048 x 256) and `wireframe/sd/grpwire.png` and `tranwire.png` (192 x 32); and `icon/<quality>/cmdicon.png`, the gray command icon (SD 32, HD2 64, HD 128 square). Sides are multiples of 4.

#### Converting the art (`convertArt`)

The build converts the baked frames of every art set into the files the mod reads, with the art converter `ArtConverter` (`src/convert/java`); `jar` runs it and packs its output under `third-party/<set>/graphics/`, `interface/` and `portrait/`. For Unity-chan:

```powershell
.\gradlew.bat convertArt
```

writes `build/converted-art/third-party/unity-chan/graphics/`:

```powershell
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

- `main-hd.anim` and `main-hd2.anim`: the unit's HD frames (scale 4) and HD2 frames (scale 2, half the resolution), one frame per direction and frame (425 = 25 frames x 17 directions, animations in metadata order), with the layers `diffuse` (the color), `bright`, `teamcolor` (the mask), `emissive`, `normal`, `specular` and `ao_depth`, compressed DXT5. A set baked at HD2 gets only the `-hd2` files (the Myriarch: 748 frames, no `emissive` layer; its fire breath 102 frames with `emissive`). Each layer is one atlas of the frames in rows, 2048 HD texels wide (1024 at HD2) and twice as wide as often as it would grow higher than 16384 texels. `normal` and `ao_depth` get the channel layout of the installed frames: only red and alpha (normal x and y) and green and alpha (AO and depth) are kept, the other channels black (a DXT5 color block shares one step index between red, green and blue, so other values next to the normal's x would show as blocks in the lit mode). The team color area of `diffuse` and `bright` is turned light gray by its brightness, so the renderer tints it with the player's color. Each frame is cut to its visible pixels and placed on one canvas around the unit's position (the set's SD canvas), the ground point the set's ground offset below it (Unity-chan: 9 SD pixels, at the selection circle).
- `shadow-hd.anim` and `shadow-hd2.anim`: the shadow image (black, the baked shadow's alpha doubled).
- `sd-colors.png` and `sd-masks.png`: the SD frames in true color (the baked frames scaled down to SD), one canvas per frame (Unity-chan 128 x 128, Myriarch 252 x 252), and their team color (red) and shadow (green) masks.
- `animations.json`: per animation its first frame set, frame count, play order, waits in game frames (the duration divided by 42 ms, the fastest game speed, at least 1), loop, hold and action frames.
- `interface/cmdicon-<quality>.dds.grp`, `interface/wirefram-<quality>.dds.grp`, `interface/grpwire-sd.dds.grp` and `interface/tranwire-sd.dds.grp`: the command icon and wireframe strips as sheets of one DDS frame (DXT5), the format of the installed `cmdicons.dds.grp` and `wirefram.dds.grp`, with the texture scale (1, 2, 4) in the sheet's version word; `portrait/<quality>/` holds the clips as they are.

The baked PNGs and `metadata.json` stay out of the jar. The converter's test checks the output against the metadata and the jar's contents (license files in, baked frames out):

```powershell
.\gradlew.bat test --tests org.fogofwarlabs.sc4j.scrcrossover.convert.ArtConverterTest
```

#### SD frames are made when the mod loads

SD frames (GRP files) hold palette indices of the installation's tileset palettes. The palettes are installation data, which the repository and the jar may not hold, so the jar holds the true color SD images and `ScrCrossoverArtFiles` makes the two GRP files `main.grp` and `shadow.grp` of each art set the first time the mod reads them, from those images and the 8 installed tileset palettes: a pixel with alpha 128 or more is drawn; where the team mask is 128 or more it becomes a team color index 8..15 by its brightness, elsewhere the nearest color among the palette indices 16..247 that hold the same color in all 8 palettes (so the unit looks the same on every tileset). A shadow pixel is set where the shadow mask is 128 or more. The same installation always gives the same frames; the work is done once per load of the installation data.

#### Checking the unit

The tests that play the unit need the installation (Gradle property `sc4j.data`, section 3):

```powershell
.\gradlew.bat integrationTest --tests org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverUnitUtilsTest
```

They run the clone's own classes from `scr-crossover.jar`: the registration (values, references, train button, images, sprite, flingy entry, script, the SD frames made from the installed palettes, the HD and HD2 frames), a replay in which a Barracks trains the unit, written, read and played in two separate clone runs with equal per-frame hashes, and the unit walking in both mirrored directions, fighting and dying through its own animations, a group with Marines following a long sequence of commands while it walks and fights, and the interface art in the jar. To see it, start the clone from the launcher (section 5), build a Barracks and press U.

`ScrCrossoverArtFilesTest` checks that the SD frames of every art set, registered as a unit or not, are made from the installed palettes (one canvas of the set's size per frame, team color and shared unit colors only, a shadow).

## 3. Build

```powershell
.\gradlew.bat jar
```

builds `build/libs/scr-crossover.jar` (it runs `cloneScrMod` and `convertArt` first). The tests of SC:R Crossover check the jar against `scr.jar` (only the overlay's files differ, and the extra entries are SC:R Crossover's own classes, its service file and the art under `third-party/`; the manifest has its own id), the overlay and its lock, the art conversion, the LAN and online refusals on 127.0.0.1, and the launcher listing of both mods:

```powershell
.\gradlew.bat test
```

The tests that need a StarCraft: Remastered installation run with `integrationTest` and the Gradle property `sc4j.data` naming the installation.

## 4. Put into `mods/`

A mod is installed by putting its jar into the `mods/` folder of an SC4J install (`<install>/mods/`); the clone does not need `scr.jar` there. With the engine checkout next to this one (`../sc4j`), its development install is `../sc4j/build/dev-install/`:

```powershell
..\sc4j\gradlew.bat -p ..\sc4j devInstall
.\gradlew.bat installMod
```

The engine's `devInstall` makes the development install (launcher, runtime, `mods/demo.jar`); `installMod` copies `scr-crossover.jar` into its `mods/` and fails when the install does not exist. For another install, add `-Psc4j.install=<install folder>`. Nothing under `userdata/` is written; the settings of the clone are saved by the launcher (section 5).

## 5. Run from the launcher

The launcher lists every jar in `mods/` with its status. Without a window:

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat --list
```

prints, for a development install that also has the SC:R mod (`installMod` of [sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr)) on a computer where StarCraft: Remastered is installed:

```text
Mods in <engine checkout>\build\dev-install\mods
READY   demo                 demo.jar
READY   scr-crossover        scr-crossover.jar
READY   scr                  scr.jar
```

Both mods declare their folder setting `gameFolder` with the same shared name (`"shared": "starcraft-remastered"`), so they share one StarCraft: Remastered folder, saved in `userdata/shared-settings.json`. The first start finds it where Windows records the game (its uninstall entry `StarCraft`, then `StarCraft` below the program folders); where it is not found, both mods are `WARNING` with `value.required (gameFolder): A value is required.` until the folder is set once. Each mod keeps its own settings file (`userdata/settings/scr-crossover.json` beside the SC:R mod's `scr.json`) for the text language. To play, open the launcher:

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat
```

(or `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher`), choose "SC:R Crossover", check the StarCraft: Remastered folder (the folder that holds `.build.info` and `x86_64/StarCraft.exe`) and the text language, and start. The launcher saves the language to `scr-crossover.json`, the folder to `shared-settings.json`, and starts the runtime with `scr-crossover.jar`, `userdata/settings/scr-crossover.json`, `userdata/mods/scr-crossover/` and `userdata/logs/scr-crossover/`.

## 6. Identity: who your clone plays with

The runtime computes a mod identity from the engine version, the mod id, the mod version and the jar's content. Players can play together only with the same identity, so:

- online (Beacon.Link): a clone never lists the rooms of the SC:R mod, and joining one is refused with the reason "another mod" (the mod id differs first);
- LAN: refused through the lobby version of the overlay's `lan-lobby.properties` (native SC:R LAN carries no mod id);
- replays and saved games of the clone belong to the clone (its own storage folder); give other players the same jar.

Every change to your jar is a new identity: players need exactly the same jar to play together.
