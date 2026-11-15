package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsGrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scr.data.format.replay.*;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModGraphics;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModSheetFrame;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModWireframe;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerScores;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.AddedGraphicsScenario;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Runs the game checks of the added unit of SC:R Crossover inside the clone: {@link CloneRunScenario} loads this class, the SC:R game test fixtures
 * and the crossover jar in one class loader, so the SC:R types here are the clone's classes from the jar and the installation is loaded by the
 * clone's {@link GameData#load}, which registers the unit. Each method is a whole check with its own assertions; it takes and returns JDK types only.
 */
public final class CloneGameScenario {
    /**
     * The values of the mod class {@code ScrCrossoverUnitUtils}, which the test class path does not hold.
     */
    private static final @NotNull String NAME = "Unity-chan";
    private static final int HIT_POINTS = 50 << 8;
    private static final int MINERAL_COST = 75;
    private static final int GAS_COST = 25;
    private static final int BUILD_TIME = 360;
    private static final int SUPPLY_REQUIRED = 2;
    private static final int TRAIN_BUTTON_POSITION = 5;
    private static final char TRAIN_BUTTON_HOTKEY = 'u';
    /**
     * The distance of the unit's selection circle and ground point below its sprite position: the Terran Goliath's.
     */
    private static final int SELECTION_CIRCLE_OFFSET = 9;
    /**
     * The frame sets of the baked animations (idle 4, walk 9, attack 4, death 8 frames) and the frames of the frame files.
     */
    private static final int DIRECTIONS = 17;
    private static final int FIRST_WALK_SET = 4;
    private static final int FIRST_ATTACK_SET = 13;
    private static final int FIRST_DEATH_SET = 17;
    private static final int FRAME_SET_COUNT = 25;
    private static final int FRAME_COUNT = FRAME_SET_COUNT * DIRECTIONS;
    private static final int SD_CANVAS_SIZE = 128;
    private static final int FIRST_TEAM_INDEX = 8;
    private static final int LAST_TEAM_INDEX = 15;
    private static final int FIRST_UNIT_INDEX = 16;
    /**
     * The interface art: the portrait clips, the SD command icon side and the SD wireframe sizes, as the installed sheets have them.
     */
    private static final int IDLE_CLIP_COUNT = 4;
    private static final int TALKING_CLIP_COUNT = 3;
    private static final int COMMAND_ICON_SIZE = 32;
    private static final int WIREFRAME_SIZE = 64;
    private static final int GROUP_WIREFRAME_SIZE = 32;
    private static final int WIREFRAME_SD_CELLS = 6;

    /**
     * The frame of the training commands (select the human player's Barracks, train the unit): the player has a completed Barracks and the cost.
     */
    private static final int TRAIN_FRAME = 4000;
    /**
     * The last frame played: before the replay's end at frame {@code 7054}, after the Barracks' queue and the unit's build time.
     */
    private static final int TRAINED_FRAME = 7000;
    private static final int FIGHT_OFFSET_Y = 96;
    private static final int FIGHT_DISTANCE = 64;
    private static final int WALK_DISTANCE = 160;
    private static final int STEP_FRAMES = 200;
    private static final int FIGHT_FRAMES = 1600;
    /**
     * The command check: a group of the unit and Terran Marines, a fixed sequence of commands from a seeded random generator (kinds in turn,
     * targets in an area around the group, frames between commands) and the last move's arrival.
     */
    private static final int COMMANDED_UNIT_COUNT = 4;
    private static final int COMMANDED_MARINE_COUNT = 2;
    private static final int GROUP_SPACING = 20;
    private static final long COMMAND_SEED = 7L;
    private static final int COMMAND_COUNT = 140;
    private static final int COMMAND_KIND_COUNT = 7;
    private static final int COMMAND_INTERRUPTED_MOVE = 1;
    private static final int COMMAND_MOVE_ONTO_BUILDING = 2;
    private static final int COMMAND_ATTACK_MOVE = 3;
    private static final int COMMAND_STOP = 4;
    private static final int COMMAND_ATTACK_MOVE_TO_ENEMY = 5;
    private static final int COMMAND_AREA_LEFT = 240;
    private static final int COMMAND_AREA_UP = 120;
    private static final int COMMAND_AREA_WIDTH = 480;
    private static final int COMMAND_AREA_HEIGHT = 320;
    private static final int COMMAND_FRAMES = 40;
    private static final int INTERRUPT_FRAMES = 4;
    private static final int ARRIVAL_FRAMES = 600;
    private static final int ARRIVAL_DISTANCE = 96;
    /**
     * The computer game: units of the computer below its town center, and the human player's waves of the unit and Marines that attack-move to
     * the computer's town center, with Stim Packs researched by both players.
     */
    private static final int ARMY_SIZE = 6;
    private static final int ARMY_SPACING = 24;
    private static final int WAVE_COUNT = 4;
    private static final int WAVE_FRAMES = 900;
    /**
     * The long computer game: waves of the unit, Marines and Medics on both sides that meet halfway between the town centers, with the Marine's
     * upgrades and the Medic's energy upgrade researched by both players.
     */
    private static final int LONG_WAVE_COUNT = 8;
    private static final int LONG_MEDIC_INTERVAL = 3;
    /**
     * The animations in which a native unit script runs a part that orders must not break: the attack's start and repeat, ground and air.
     */
    private static final @NotNull Set<@NotNull Integer> ATTACK_ANIMATIONS = Set.of(IscriptExecutor.ANIMATION_GROUND_ATTACK_INIT,
                                                                                   IscriptExecutor.ANIMATION_AIR_ATTACK_INIT,
                                                                                   IscriptExecutor.ANIMATION_GROUND_ATTACK_REPEAT,
                                                                                   IscriptExecutor.ANIMATION_AIR_ATTACK_REPEAT);

    /**
     * Checks the registration of Unity-chan, the first of the two added unit types: the crossover's name and values, the Goliath's size (extents,
     * placement box, selection circle and health bar), the Ghost's weapons, the Medic's voice, the Marine's requirement, its own portrait,
     * wireframe and command icon, a Barracks train button; its two added images (unit and shadow) whose SD frames the clone makes from the jar
     * with the installation's palettes (team color indices {@code 8..15} and shared unit colors only) and whose HD frames come from the jar; one
     * added sprite, flingy entry and script per unit.
     *
     * @param installation the SC:R installation
     * @throws IOException if the installation or the crossover's graphics cannot be read
     */
    public static void checkRegistration(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            ScrAddedUnitTypes types = gameData.getAddedUnitTypes();
            DatTable units = gameData.getUnits();

            // Unity-chan first, then the Myriarch (CloneMorphScenario checks it).
            Assertions.assertEquals(2, types.getCount());

            ScrAddedUnitType type = types.getTypes().getFirst();
            ScrAddedUnitType.TrainButton button = type.getTrainButton();

            Assertions.assertEquals(ScrAddedUnitTypes.FIRST_ID, types.getId(type.getType()));
            Assertions.assertEquals(NAME, type.getName());
            Assertions.assertEquals(ScrUnits.TERRAN_MARINE, type.getBaseType());
            Assertions.assertEquals(HIT_POINTS, type.getValue(UnitsDat.HIT_POINTS));
            Assertions.assertEquals(MINERAL_COST, type.getValue(UnitsDat.MINERAL_COST));
            Assertions.assertEquals(GAS_COST, type.getValue(UnitsDat.GAS_COST));
            Assertions.assertEquals(BUILD_TIME, type.getValue(UnitsDat.BUILD_TIME));
            Assertions.assertEquals(SUPPLY_REQUIRED, type.getValue(UnitsDat.SUPPLY_REQUIRED));

            for (String field : List.of(UnitsDat.DIMENSION_LEFT, UnitsDat.DIMENSION_UP, UnitsDat.DIMENSION_RIGHT, UnitsDat.DIMENSION_DOWN,
                                        UnitsDat.PLACEMENT_WIDTH, UnitsDat.PLACEMENT_HEIGHT)) {
                Assertions.assertEquals(units.get(field, ScrUnitIds.TERRAN_GOLIATH), type.getValue(field), field);
            }

            for (String field : List.of(UnitsDat.GROUND_WEAPON, UnitsDat.AIR_WEAPON)) {
                Assertions.assertEquals(units.get(field, ScrUnitIds.TERRAN_GHOST), type.getValue(field), field);
            }

            for (String field : List.of(UnitsDat.READY_SOUND, UnitsDat.WHAT_SOUND_START, UnitsDat.YES_SOUND_START)) {
                Assertions.assertEquals(units.get(field, ScrUnitIds.TERRAN_MEDIC), type.getValue(field), field);
            }

            Assertions.assertEquals(ScrUnits.TERRAN_MARINE, type.getRequirementType());
            Assertions.assertEquals(ScrAddedGraphics.FIRST_PORTRAIT_ID, type.getValue(UnitsDat.PORTRAIT));
            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, type.getWireframe());
            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, type.getCommandIcon());
            Assertions.assertNotNull(button);
            Assertions.assertEquals(ScrUnits.TERRAN_BARRACKS, button.getProducerType());
            Assertions.assertEquals(TRAIN_BUTTON_POSITION, button.getPosition());
            Assertions.assertEquals(TRAIN_BUTTON_HOTKEY, button.getHotkey());

            ScrAddedGraphics graphics = gameData.getAddedGraphics();

            // Unity-chan's shadow and image, the Myriarch's fire breath, shadow and image.
            Assertions.assertEquals(5, graphics.getImages().size());
            Assertions.assertEquals(2, graphics.getSprites().size());

            ScrAddedSprite sprite = graphics.getSprites().getFirst();
            int goliathSprite = gameData.getFlingyTypes().getSprite(gameData.getUnitTypes().getFlingyType(ScrUnitIds.TERRAN_GOLIATH));

            Assertions.assertEquals(SELECTION_CIRCLE_OFFSET, sprite.getValue(SpritesDat.SELECTION_CIRCLE_OFFSET));
            Assertions.assertEquals(SELECTION_CIRCLE_OFFSET, gameData.getSpriteTypes().getSelectionCircleOffset(goliathSprite));
            Assertions.assertEquals(gameData.getSpriteTypes().getSelectionCircle(goliathSprite), sprite.getValue(SpritesDat.SELECTION_CIRCLE));
            Assertions.assertEquals(gameData.getSpriteTypes().getHealthBarWidth(goliathSprite), sprite.getValue(SpritesDat.HEALTH_BAR_WIDTH));
            Assertions.assertEquals(2, graphics.getFlingies().size());
            // Unity-chan's and the Myriarch's unit scripts and the fire breath's script.
            Assertions.assertEquals(3, graphics.getScripts().size());
            checkInterfaceArt(graphics);

            for (ScrAddedImage image : graphics.getImages()) {
                ScrModGraphics files = image.getGraphics();

                if (!image.getType().getDebugName().startsWith(NAME)) {
                    continue;
                }

                Assertions.assertNotNull(files);
                checkFrames(files.loadFrames(), image.getType().getDebugName().equals(NAME));

                for (AssetResolver.AssetSet quality : List.of(AssetResolver.AssetSet.HD, AssetResolver.AssetSet.HD2)) {
                    AnimFile animation = files.readAnimation(quality);

                    Assertions.assertNotNull(animation);
                    Assertions.assertEquals(FRAME_COUNT, animation.getEntry(0).getFrameCount());
                }
            }
        }
    }

    /**
     * Checks the interface art: one portrait, command icon and wireframe per unit; of Unity-chan's, the idle and talking clips of every quality
     * are in the jar, and the command icon and wireframe sheets of every quality hold one frame of the installed sheets' size at their texture
     * scale.
     */
    private static void checkInterfaceArt(@NotNull ScrAddedGraphics graphics) throws IOException {
        Assertions.assertEquals(2, graphics.getPortraits().size());
        Assertions.assertEquals(2, graphics.getCommandIcons().size());
        Assertions.assertEquals(2, graphics.getWireframes().size());

        ScrModPortrait portrait = graphics.getPortraits().getFirst();
        ScrModSheetFrame icon = graphics.getCommandIcons().getFirst();
        ScrModWireframe wireframe = graphics.getWireframes().getFirst();

        for (AssetResolver.AssetSet quality : AssetResolver.AssetSet.values()) {
            int scale = quality == AssetResolver.AssetSet.SD ? 1 : quality == AssetResolver.AssetSet.HD2 ? 2 : 4;
            DdsGrpFile iconSheet = icon.readSheet(quality);
            DdsGrpFile wireframeSheet = wireframe.getSingle().readSheet(quality);

            for (int clip = 0; clip < IDLE_CLIP_COUNT; clip++) {
                Assertions.assertTrue(portrait.readClip(portrait.getIdlePrefix(quality) + clip + ScrModPortrait.CLIP_EXTENSION).length > 0);
            }

            for (int clip = 0; clip < TALKING_CLIP_COUNT; clip++) {
                Assertions.assertTrue(portrait.readClip(portrait.getTalkingPrefix(quality) + clip + ScrModPortrait.CLIP_EXTENSION).length > 0);
            }

            Assertions.assertTrue(portrait.getIdlePrefix(quality).contains("/" + quality.name().toLowerCase(Locale.ROOT) + "/"), quality.name());
            Assertions.assertEquals(ImageFileWriterUtils.DDS_GRP_SCALED_VERSION | scale, iconSheet.getVersion());
            Assertions.assertEquals(COMMAND_ICON_SIZE * scale, iconSheet.getFrameWidth(0));
            Assertions.assertEquals(COMMAND_ICON_SIZE * scale, iconSheet.getFrameHeight(0));
            Assertions.assertEquals(ImageFileWriterUtils.DDS_GRP_SCALED_VERSION | scale, wireframeSheet.getVersion());
            Assertions.assertEquals(WIREFRAME_SIZE * scale, wireframeSheet.getFrameHeight(0));
        }

        Assertions.assertEquals(WIREFRAME_SIZE * WIREFRAME_SD_CELLS, wireframe.getSingle().readSheet(AssetResolver.AssetSet.SD).getFrameWidth(0));

        for (ScrModSheetFrame view : List.of(wireframe.getGroup(), wireframe.getTransport())) {
            DdsGrpFile sheet = view.readSheet(AssetResolver.AssetSet.SD);

            Assertions.assertEquals(GROUP_WIREFRAME_SIZE * WIREFRAME_SD_CELLS, sheet.getFrameWidth(0));
            Assertions.assertEquals(GROUP_WIREFRAME_SIZE, sheet.getFrameHeight(0));
        }
    }

    /**
     * Checks the SD frames of an added image: one canvas per frame, and for the unit (not its shadow) only team color and shared unit colors,
     * with the team color in the frames.
     */
    private static void checkFrames(@NotNull GrpFile frames, boolean unit) throws IOException {
        Set<Integer> indices = new TreeSet<>();

        Assertions.assertEquals(FRAME_COUNT, frames.getFrameCount());
        Assertions.assertEquals(SD_CANVAS_SIZE, frames.getWidth());
        Assertions.assertEquals(SD_CANVAS_SIZE, frames.getHeight());

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (byte pixel : frames.decodeFrame(frame).getPixels()) {
                indices.add(pixel & 0xFF);
            }
        }

        if (unit) {
            Assertions.assertTrue(indices.stream().anyMatch(index -> index >= FIRST_TEAM_INDEX && index <= LAST_TEAM_INDEX), indices::toString);
            Assertions.assertTrue(indices.stream().allMatch(index -> index == 0 || index >= FIRST_TEAM_INDEX), indices::toString);
            Assertions.assertTrue(indices.stream().anyMatch(index -> index >= FIRST_UNIT_INDEX), indices::toString);
        }
    }

    /**
     * Writes the replay of {@link GameTestResources#REPLAY_TERRAN_VS_COMPUTER} with two commands of its player at {@link #TRAIN_FRAME} (select
     * the player's Barracks, train the unit), parses it and plays it; checks that it plays as the original replay before those commands, that
     * the Barracks trains the unit and that the unit shows its added image.
     *
     * @param installation the SC:R installation
     * @return the state hash of every frame of the written replay's playback
     * @throws IOException if the installation or the replay cannot be read, written or played
     */
    public static long @NotNull [] playTrainingReplay(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            int unitType = ScrAddedUnitTypes.FIRST_ID;
            ReplayFile original = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER)));
            List<ReplayRuntime> runtimes = new ArrayList<>();
            List<Long> originalHashes = play(original, gameData, TRAIN_FRAME, runtimes);
            Unit barracks = AddedGraphicsScenario.findUnit(runtimes.getFirst().getGame(), RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_BARRACKS);

            Assertions.assertNotNull(barracks, "The player has a Barracks at the training frame.");

            ReplayFile written = writeTrainingReplay(original, runtimes.getFirst().getGame(), barracks, unitType);
            List<Long> hashes = play(written, gameData, TRAINED_FRAME, runtimes);
            Game game = runtimes.getLast().getGame();
            Unit trained = AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, unitType);

            Assertions.assertEquals(originalHashes, hashes.subList(0, originalHashes.size()),
                                    "The written replay plays as the original before its commands.");
            Assertions.assertNull(runtimes.getLast().getFailure());
            Assertions.assertNotNull(trained, "The Barracks trains the unit.");
            Assertions.assertEquals(HIT_POINTS, trained.getHitPoints());

            Sprite sprite = trained.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertNotNull(sprite.getMainImage());
            Assertions.assertTrue(gameData.getAddedGraphics().containsImage(sprite.getMainImage().getImageTypeId()),
                                  "The unit shows its added image.");

            return hashes.stream().mapToLong(Long::longValue).toArray();
        }
    }

    private static @NotNull List<@NotNull Long> play(@NotNull ReplayFile replay, @NotNull GameData gameData, int lastFrame,
                                                     @NotNull List<@NotNull ReplayRuntime> runtimes) throws IOException {
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);
        List<Long> hashes = new ArrayList<>();

        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < lastFrame) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));

            hashes.add(game.computeStateHash());
        }

        runtimes.add(runtime);

        return hashes;
    }

    private static @NotNull ReplayFile writeTrainingReplay(@NotNull ReplayFile original, @NotNull Game game, @NotNull Unit barracks,
                                                           int unitType) throws IOException {
        ReplayCommand firstSelection = original.getCommands().stream().filter(command -> ReplaySelectionCommand.supports(command.getTypeId()))
                                               .findFirst().orElseThrow();
        int selectType = firstSelection.getTypeId() >= ReplayCommandIds.SELECT_121 ? ReplayCommandIds.SELECT_121 : ReplayCommandIds.SELECT;
        int handleWidth = selectType == ReplayCommandIds.SELECT_121 ? Integer.BYTES : Short.BYTES;
        long handle = game.getUnitSystem().requireUnitPool().getHandle(barracks.getIndex());
        ReplaySelectionCommand select = ReplaySelectionCommand.create(ReplaySelectionCommand.Operation.REPLACE, handleWidth, new long[]{handle});
        List<ReplayCommand> commands = new ArrayList<>();
        boolean injected = false;

        for (ReplayCommand command : original.getCommands()) {
            if (!injected && command.getFrame() > TRAIN_FRAME) {
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), selectType,
                                               ReplayCommandCodecs.encode(ReplaySelectionCommand.createCodec(selectType), select)));
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), ReplayCommandIds.TRAIN,
                                               ReplayCommandCodecs.encodeValue(ReplayCommandIds.TRAIN, unitType)));
                injected = true;
            }

            commands.add(command);
        }

        Assertions.assertTrue(injected);

        byte[] header = original.getHeaderSection();
        byte[] mapData = original.getMapData();

        Assertions.assertNotNull(header);
        Assertions.assertNotNull(mapData);

        byte[] written = new ReplayWriter(header, ReplayWriter.encodeCommands(commands), mapData,
                                          ReplayWriter.encodeNames(original.getPlayerNameBytes()), new byte[ReplayWriter.SKIN_SIZE],
                                          original.getLimits().getRequestedValues(), original.getGameVersion().getFlags(),
                                          new byte[ReplayWriter.CUSTOM_COLORS_SIZE],
                                          ReplayWriter.encodeVersionBlock(original.getGameVersion().getBuild(), 0)).write();

        return ReplayFile.parse(written);
    }

    /**
     * Creates the unit below the human player's Command Center, walks it east and back west, then puts an enemy Goliath beside it: the unit
     * shows its walk frame sets (mirrored when facing west), fights with its attack frame sets, damages the Goliath and dies with its death frame
     * sets.
     *
     * @param installation the SC:R installation
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static void fight(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            int unitType = ScrAddedUnitTypes.FIRST_ID;
            ReplayRuntime runtime = RunningGameScenario.start(gameData);
            Game game = runtime.getGame();
            Unit commandCenter = AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);
            Unit[] units = new Unit[2];

            Assertions.assertNotNull(commandCenter);
            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                units[0] = RunningGameScenario.createUnit(game, unitType, commandCenter.getPositionX(), commandCenter.getPositionY() + FIGHT_OFFSET_Y,
                                                          RunningGameScenario.HUMAN);
                move(game, units[0], WALK_DISTANCE);
            });

            Set<Integer> walking = new TreeSet<>();
            Set<Boolean> eastMirrored = new TreeSet<>();
            Set<Boolean> westMirrored = new TreeSet<>();

            collectFrameSets(runtime, units[0], STEP_FRAMES, walking, eastMirrored);
            RunningGameScenario.runInCommandDispatch(runtime, () -> move(game, units[0], -WALK_DISTANCE));
            collectFrameSets(runtime, units[0], STEP_FRAMES, walking, westMirrored);
            RunningGameScenario.runInCommandDispatch(runtime, () -> units[1] = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GOLIATH,
                                                                                                              units[0].getPositionX() + FIGHT_DISTANCE,
                                                                                                              units[0].getPositionY(),
                                                                                                              RunningGameScenario.COMPUTER));

            Unit enemy = units[1];
            int enemyHitPoints = enemy.getHitPoints();
            Set<Integer> fighting = new TreeSet<>();

            collectFrameSets(runtime, units[0], FIGHT_FRAMES, fighting, new TreeSet<>());

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertTrue(walking.stream().anyMatch(set -> set >= FIRST_WALK_SET && set < FIRST_ATTACK_SET), "walk frame sets: " + walking);
            Assertions.assertTrue(eastMirrored.contains(false), "Facing east shows unmirrored frames.");
            Assertions.assertTrue(westMirrored.contains(true), "Facing west shows the mirrored frames.");
            Assertions.assertTrue(fighting.stream().anyMatch(set -> set >= FIRST_ATTACK_SET && set < FIRST_DEATH_SET),
                                  "attack frame sets: " + fighting);
            Assertions.assertTrue(fighting.stream().anyMatch(set -> set >= FIRST_DEATH_SET), "death frame sets: " + fighting);
            Assertions.assertTrue(enemy.isDestroyed() || enemy.getHitPoints() < enemyHitPoints, "The unit damages the Goliath.");
            Assertions.assertNull(AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, unitType), "The unit dies.");
        }
    }

    /**
     * Gives a group of the unit and Terran Marines below the human player's Command Center a fixed sequence of commands, many while they walk:
     * moves, moves interrupted after a few frames by the next one, moves onto the Command Center (blocked ground), attack-moves, attack-moves
     * against a computer Zergling made at the target, and stops; then one last move. Every frame, a unit whose script runs a part that orders must
     * not break ({@code nobrkcodestart}) shows an attack animation; at the end every unit that lives has done its last move: it stands near the
     * target, idle, with no such part running.
     *
     * @param installation the SC:R installation
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static void command(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            ReplayRuntime runtime = RunningGameScenario.start(gameData);
            Game game = runtime.getGame();
            Unit commandCenter = AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);
            List<Unit> units = new ArrayList<>();
            Random random = new Random(COMMAND_SEED);

            Assertions.assertNotNull(commandCenter);

            int homeX = commandCenter.getPositionX();
            int homeY = commandCenter.getPositionY() + FIGHT_OFFSET_Y;

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                for (int index = 0; index < COMMANDED_UNIT_COUNT; index++) {
                    units.add(RunningGameScenario.createUnit(game, ScrAddedUnitTypes.FIRST_ID, homeX + index * GROUP_SPACING, homeY,
                                                             RunningGameScenario.HUMAN));
                }

                for (int index = 0; index < COMMANDED_MARINE_COUNT; index++) {
                    units.add(RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, homeX + index * GROUP_SPACING, homeY + GROUP_SPACING,
                                                             RunningGameScenario.HUMAN));
                }
            });

            for (int command = 0; command < COMMAND_COUNT; command++) {
                int kind = command % COMMAND_KIND_COUNT;
                boolean blocked = kind == COMMAND_MOVE_ONTO_BUILDING;
                int targetX = blocked ? commandCenter.getPositionX() : homeX - COMMAND_AREA_LEFT + random.nextInt(COMMAND_AREA_WIDTH);
                int targetY = blocked ? commandCenter.getPositionY() : homeY - COMMAND_AREA_UP + random.nextInt(COMMAND_AREA_HEIGHT);
                int order = switch (kind) {
                    case COMMAND_ATTACK_MOVE, COMMAND_ATTACK_MOVE_TO_ENEMY -> ScrOrderIds.ATTACK_MOVE;
                    case COMMAND_STOP -> ScrOrderIds.STOP;
                    default -> ScrOrderIds.MOVE;
                };
                int frames = kind == COMMAND_INTERRUPTED_MOVE ? 1 + random.nextInt(INTERRUPT_FRAMES) : 1 + random.nextInt(COMMAND_FRAMES);

                RunningGameScenario.runInCommandDispatch(runtime, () -> {
                    if (kind == COMMAND_ATTACK_MOVE_TO_ENEMY) {
                        RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, targetX, targetY, RunningGameScenario.COMPUTER);
                    }

                    issue(game, units, order, targetX, targetY);
                });
                stepCommanded(runtime, units, frames);
            }

            int finalX = homeX + WALK_DISTANCE;

            RunningGameScenario.runInCommandDispatch(runtime, () -> issue(game, units, ScrOrderIds.MOVE, finalX, homeY));
            stepCommanded(runtime, units, ARRIVAL_FRAMES);

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertTrue(units.stream().anyMatch(unit -> !unit.isDestroyed() && unit.getUnitId() == ScrAddedUnitTypes.FIRST_ID),
                                  "One of the units lives to the last move.");

            for (Unit unit : units) {
                if (!unit.isDestroyed()) {
                    String name = "unit " + unit.getIndex() + " of type " + unit.getUnitId();

                    Assertions.assertNotEquals(ScrOrderIds.MOVE, unit.getPrimaryOrder(), name + " has done its last move.");
                    Assertions.assertEquals(0, unit.getStatus() & UnitStatusFlags.ISCRIPT_NO_BREAK, name + " runs no unbreakable script part.");
                    Assertions.assertTrue(Math.abs(unit.getPositionX() - finalX) <= ARRIVAL_DISTANCE &&
                                          Math.abs(unit.getPositionY() - homeY) <= ARRIVAL_DISTANCE,
                                          name + " stands near the target, at " + unit.getPositionX() + ", " + unit.getPositionY() + ".");
                }
            }
        }
    }

    /**
     * Plays a game against the computer player with the unit on both sides: the computer's units below its town center, and waves of the unit and
     * Terran Marines of the human player that attack-move to it, with Stim Packs researched by both players; checks that the game runs without a
     * failure and that one of the units dies in the fights.
     *
     * @param installation the SC:R installation
     * @return the state hash of every frame of the game
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static long @NotNull [] playComputerGame(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            int unitType = ScrAddedUnitTypes.FIRST_ID;
            ReplayRuntime runtime = RunningGameScenario.start(gameData);
            Game game = runtime.getGame();
            Unit humanCenter = AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);
            Unit computerCenter = findTownCenter(game, RunningGameScenario.COMPUTER);
            PlayerTechnology technology = game.getPlayerTable().getPlayerTechnology();
            List<Long> hashes = new ArrayList<>();
            List<Unit> units = new ArrayList<>();

            Assertions.assertNotNull(humanCenter);
            Assertions.assertNotNull(computerCenter);
            Assertions.assertNotNull(technology);
            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                technology.setResearched(RunningGameScenario.HUMAN, ScrTechIds.STIM_PACKS, true);
                technology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.STIM_PACKS, true);

                for (int index = 0; index < ARMY_SIZE; index++) {
                    units.add(RunningGameScenario.createUnit(game, unitType, computerCenter.getPositionX() + index * ARMY_SPACING,
                                                             computerCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.COMPUTER));
                }
            });

            for (int wave = 0; wave < WAVE_COUNT; wave++) {
                RunningGameScenario.runInCommandDispatch(runtime, () -> {
                    for (int index = 0; index < ARMY_SIZE; index++) {
                        Unit unit = RunningGameScenario.createUnit(game, index % 2 == 0 ? unitType : ScrUnitIds.TERRAN_MARINE,
                                                                   humanCenter.getPositionX() + index * ARMY_SPACING,
                                                                   humanCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);

                        units.add(unit);
                        game.getOrders().issue(unit, ScrOrderIds.ATTACK_MOVE, null, computerCenter.getPositionX(), computerCenter.getPositionY(),
                                               ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
                    }
                });

                for (int frame = 0; frame < WAVE_FRAMES; frame++) {
                    Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
                    Assertions.assertNull(runtime.getFailure());

                    hashes.add(game.computeStateHash());
                }
            }

            Assertions.assertTrue(units.stream().anyMatch(unit -> unit.getUnitId() == unitType && unit.isDestroyed()),
                                  "One of the units dies in the fights.");

            return hashes.stream().mapToLong(Long::longValue).toArray();
        }
    }

    /**
     * Plays a long live game against the computer player with the unit on both sides, past the end of the scenario's replay: every wave creates
     * the unit, Marines and Medics for both players below their town centers, with U-238 Shells, Stim Packs and the Caduceus Reactor researched by
     * both players, so the unit takes the Marine's upgrades and the Medics heal it; both waves attack-move to the point halfway between the town
     * centers, so the armies meet in the middle. The game runs until the last wave's frames are over or it ends; checks that it runs without a
     * failure for longer than the short computer game and that units of both players die in the fights.
     *
     * @param installation the SC:R installation
     * @return the state hash of every frame of the game
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static long @NotNull [] playLongComputerGame(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            int unitType = ScrAddedUnitTypes.FIRST_ID;
            ReplayRuntime runtime = RunningGameScenario.startLiveGame(gameData);
            Game game = runtime.getGame();
            Unit humanCenter = AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);
            Unit computerCenter = findTownCenter(game, RunningGameScenario.COMPUTER);
            List<Long> hashes = new ArrayList<>();

            Assertions.assertNotNull(humanCenter);
            Assertions.assertNotNull(computerCenter);

            int battleX = (humanCenter.getPositionX() + computerCenter.getPositionX()) / 2;
            int battleY = (humanCenter.getPositionY() + computerCenter.getPositionY()) / 2;

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                for (int player : new int[]{RunningGameScenario.HUMAN, RunningGameScenario.COMPUTER}) {
                    game.getPlayerTable().requirePlayerTechnology().setResearched(player, ScrTechIds.STIM_PACKS, true);
                    game.getPlayerTable().requirePlayerUpgrades().setLevel(player, ScrUpgradeIds.U_238_SHELLS, 1);
                    game.getPlayerTable().requirePlayerUpgrades().setLevel(player, ScrUpgradeIds.CADUCEUS_REACTOR, 1);
                }
            });

            waves:
            for (int wave = 0; wave < LONG_WAVE_COUNT; wave++) {
                RunningGameScenario.runInCommandDispatch(runtime, () -> {
                    for (int index = 0; index < ARMY_SIZE; index++) {
                        int type = index % LONG_MEDIC_INTERVAL == LONG_MEDIC_INTERVAL - 1 ? ScrUnitIds.TERRAN_MEDIC
                                                                                          : index % 2 == 0 ? unitType : ScrUnitIds.TERRAN_MARINE;
                        Unit human = RunningGameScenario.createUnit(game, type, humanCenter.getPositionX() + index * ARMY_SPACING,
                                                                    humanCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);
                        Unit computer = RunningGameScenario.createUnit(game, type, computerCenter.getPositionX() + index * ARMY_SPACING,
                                                                       computerCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.COMPUTER);

                        game.getOrders().issue(human, ScrOrderIds.ATTACK_MOVE, null, battleX, battleY, ScrUnitIds.NONE, false,
                                               RunningGameScenario.INSTRUCTION_LIMIT);
                        game.getOrders().issue(computer, ScrOrderIds.ATTACK_MOVE, null, battleX, battleY, ScrUnitIds.NONE, false,
                                               RunningGameScenario.INSTRUCTION_LIMIT);
                    }
                });

                for (int frame = 0; frame < WAVE_FRAMES; frame++) {
                    boolean running = runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration());

                    Assertions.assertNull(runtime.getFailure());

                    if (!running) {
                        break waves;
                    }

                    hashes.add(game.computeStateHash());
                }
            }

            Assertions.assertTrue(hashes.size() > WAVE_COUNT * WAVE_FRAMES, "The long game runs " + hashes.size() + " frames.");

            PlayerScores scores = game.getPlayerTable().requireUnitAccounting().getScores();

            Assertions.assertTrue(scores.getDeaths(RunningGameScenario.HUMAN, TriggerUnitGroups.MEN) > 0,
                                  "Units of the human player die in the fights.");
            Assertions.assertTrue(scores.getDeaths(RunningGameScenario.COMPUTER, TriggerUnitGroups.MEN) > 0,
                                  "Units of the computer player die in the fights.");

            return hashes.stream().mapToLong(Long::longValue).toArray();
        }
    }

    private static @Nullable Unit findTownCenter(@NotNull Game game, int owner) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (!unit.isDestroyed() && (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT) != 0) {
                return unit;
            }
        }

        return null;
    }

    private static void issue(@NotNull Game game, @NotNull List<@NotNull Unit> units, int order, int targetX, int targetY) {
        for (Unit unit : units) {
            if (!unit.isDestroyed()) {
                game.getOrders().issue(unit, order, null, targetX, targetY, ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
            }
        }
    }

    /**
     * Steps a game and checks every frame that a living unit runs an unbreakable script part only inside an attack animation.
     */
    private static void stepCommanded(@NotNull ReplayRuntime runtime, @NotNull List<@NotNull Unit> units, int frames) {
        Game game = runtime.getGame();

        for (int frame = 0; frame < frames; frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));

            for (Unit unit : units) {
                Sprite sprite = unit.getSpriteOrNull();
                SpriteImage image = sprite == null ? null : sprite.getMainImage();

                if (!unit.isDestroyed() && image != null && (unit.getStatus() & UnitStatusFlags.ISCRIPT_NO_BREAK) != 0) {
                    Assertions.assertTrue(ATTACK_ANIMATIONS.contains(image.getAnimationType()),
                                          "Frame " + game.getCurrentFrame() + ": unit " + unit.getIndex() + " of type " + unit.getUnitId() +
                                          " runs an unbreakable script part in animation " + image.getAnimationType() + ".");
                }
            }
        }
    }

    private static void move(@NotNull Game game, @NotNull Unit unit, int distance) {
        game.getOrders().issue(unit, ScrOrderIds.MOVE, null, unit.getPositionX() + distance, unit.getPositionY(), ScrUnitIds.NONE, false,
                               RunningGameScenario.INSTRUCTION_LIMIT);
    }

    /**
     * Steps a game and collects the frame set and the mirroring of every frame the unit's main image shows, until the frame limit or the end of
     * the sprite's images.
     */
    private static void collectFrameSets(@NotNull ReplayRuntime runtime, @NotNull Unit unit, int frames, @NotNull Set<Integer> sets,
                                         @NotNull Set<Boolean> mirrored) {
        Game game = runtime.getGame();
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        for (int frame = 0; frame < frames && sprite.getMainImage() != null; frame++) {
            SpriteImage image = sprite.getMainImage();

            if (image.getImageTypeId() >= ScrAddedGraphics.FIRST_IMAGE_ID) {
                sets.add(image.getFrameIndex() / DIRECTIONS);
                mirrored.add((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0);
            }

            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }
    }

    private CloneGameScenario() {
    }
}
