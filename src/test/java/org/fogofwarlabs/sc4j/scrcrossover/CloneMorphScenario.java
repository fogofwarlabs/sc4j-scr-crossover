package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponTargetFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.map.ScrTerrainGrid;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test code that runs inside the clone of SC:R Crossover ({@link CloneRunScenario}) and plays the Myriarch's morph in a live melee game of the clone:
 * the human Zerg player's start Hatchery morphs into a Lair and a Hive, then, with the Ultralisk Cavern and the Defiler Mound, into the Myriarch,
 * which then flies and breathes fire.
 */
public final class CloneMorphScenario {
    /**
     * The id of the Myriarch: the second added type, after Unity-chan.
     */
    public static final int MYRIARCH_ID = ScrAddedUnitTypes.FIRST_ID + 1;
    /**
     * The installed melee map, its scenario file and the seed of the live game.
     */
    public static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    /**
     * The scenario file in the archive of {@link #MAP_NAME}.
     */
    public static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String MAPS_FOLDER = "Maps";
    private static final int GAME_SEED = 0x2F6A1C35;
    private static final @NotNull String PLAYER_NAME = "tester";
    /**
     * The Myriarch's values the scenario checks: its hit points (in 1/256) and the morph's cost.
     */
    private static final int HIT_POINTS = 2000 << 8;
    private static final int MINERAL_COST = 400;
    private static final int GAS_COST = 400;
    /**
     * The tooltip of the disabled morph button in English and in Korean: the buildings the morph needs besides the Hive.
     */
    private static final @NotNull String MORPH_DISABLED_TEXT = "Myriarch Requires:\n   Ultralisk Cavern\n   Defiler Mound";
    private static final @NotNull String KOREAN_LOCALE = "koKR";
    private static final @NotNull String KOREAN_MORPH_DISABLED_TEXT = "미리아크 요구 사항:\n   울트라리스크 캐번\n   디파일러 마운드";
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    /**
     * The frame the scenario prepares the morphs, the frames a command needs, the frame limit of each morph and the frames the creep and Larvae
     * get to vanish after the Myriarch completes.
     */
    public static final int PREPARE_FRAME = 8;
    private static final int COMMAND_FRAMES = 2;
    private static final int MORPH_FRAME_LIMIT = 1200;
    private static final int AFTERMATH_FRAMES = 2400;
    /**
     * The tiles around the Hive the creep test counts.
     */
    private static final int CREEP_TILE_RADIUS = 6;
    /**
     * The pixels the Myriarch flies and attack-moves from the Hive's place, the pixels it may stop short of the clicked position, and the frame
     * limit of a flight.
     */
    private static final int MOVE_DISTANCE = 256;
    private static final int ARRIVAL_DISTANCE = 32;
    private static final int MOVE_FRAME_LIMIT = 900;
    /**
     * The walk cells around the Hive searched for terrain no ground unit stands on, the walk cells of the side of such a block, the least
     * distance of the block from the Hive in pixels, and the pixels of a walk cell.
     */
    private static final int SEARCH_CELLS = 120;
    private static final int UNWALKABLE_BLOCK_CELLS = 6;
    private static final int UNWALKABLE_DISTANCE = 160;
    private static final int WALK_CELL_SIZE = 8;
    /**
     * The pixels beyond the flight's end the enemy Supply Depot is created at, the pixels above the Myriarch the enemy Science Vessels are created
     * at and between them, the breaths the fight scenario counts on a target, the frame limit of those breaths and the frames after the attack
     * stops.
     */
    private static final int DEPOT_DISTANCE = 128;
    private static final int VESSEL_OFFSET = 64;
    private static final int VESSEL_SPACING = 4;
    private static final int HIT_COUNT = 3;
    private static final int FIGHT_FRAME_LIMIT = 900;
    private static final int SETTLE_FRAMES = 60;
    /**
     * The splash shares of the fire breath's enemy splash: full damage inside the inner radius, half inside the medium one, a quarter inside the
     * outer one; a hit deals at least half a hit point.
     */
    private static final int @NotNull [] SPLASH_DIVISORS = {1, 2, 4};
    private static final int MINIMUM_HIT_DAMAGE = 1 << 7;
    /**
     * The projectiles of one breath, each splashing the units around it.
     */
    private static final int BREATH_HITS = 3;
    /**
     * The most game frames the Myriarch's picture may stay one frame while it attacks: it hovers through the cooldown, no frame of its
     * animations waits longer than three game frames.
     */
    private static final int MAXIMUM_STILL_FRAMES = 4;
    /**
     * The fraction bits of hit points.
     */
    private static final int HIT_POINT_FRACTION_BITS = 8;

    /**
     * Plays the morph and checks every step: the Myriarch is a building morph result with its own requirement script and a morph button on the
     * Hive; the Hive accepts the morph only with both tech buildings; the morph is charged once when it starts, shows the Myriarch while the
     * building stays a Hive, completes as the unit (its values, at the Hive's position, not a building, depot or producer) with the Hive's counts
     * and supply gone and the unit's added; the Larvae lose their parent and die, and the creep recedes.
     *
     * @param installation the SC:R installation
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static void morphHive(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            ScrAddedUnitType type = gameData.getAddedUnitTypes().getTypes().get(1);
            ScrAddedUnitType.TrainButton button = type.getTrainButton();

            Assertions.assertEquals(MYRIARCH_ID, gameData.getAddedUnitTypes().getId(type.getType()));
            Assertions.assertEquals(ScrUnits.ZERG_GUARDIAN, type.getBaseType());
            Assertions.assertEquals(UnitTraitFlags.BUILDING_MORPH_RESULT,
                                    type.getTrait(ScrUnitTypeView.TraitColumn.TRAITS) & UnitTraitFlags.BUILDING_MORPH_RESULT);
            Assertions.assertNotNull(type.getRequirementScript());
            Assertions.assertNotNull(button);
            Assertions.assertEquals(ScrUnits.ZERG_HIVE, button.getProducerType());
            Assertions.assertEquals(ScrAddedUnitType.ProductionAction.BUILDING_MORPH, button.getAction());
            Assertions.assertEquals(MORPH_DISABLED_TEXT, button.getText(null).getDisabledText());
            Assertions.assertEquals(KOREAN_MORPH_DISABLED_TEXT, button.getText(KOREAN_LOCALE).getDisabledText());

            CloneZergPlayer zerg = start(gameData, installation);
            ScrUnitTypeView units = zerg.getGame().requireGameData().getUnitTypes();
            UnitAccounting accounting = zerg.getGame().getPlayerTable().requireUnitAccounting();
            ScrRequirementSystem requirements = zerg.getGame().getRequirements();
            Unit hive = morphToHive(zerg);

            Assertions.assertEquals(HIT_POINTS, units.getHitPoints(MYRIARCH_ID));
            Assertions.assertTrue(ScrRequirementSystem.isAccepted(requirements.checkBuild(hive, MYRIARCH_ID, zerg.getPlayer())));
            Assertions.assertFalse(ScrRequirementSystem.isAccepted(requirements.checkBuild(zerg.findUnits(ScrUnitIds.ZERG_DRONE).getFirst(), MYRIARCH_ID,
                                                                                           zerg.getPlayer())));

            int hiveX = hive.getPositionX();
            int hiveY = hive.getPositionY();
            int creepTiles = zerg.countCreepTiles(hiveX, hiveY, CREEP_TILE_RADIUS);
            int minerals = zerg.requirePlayer().getMinerals();
            int gas = zerg.requirePlayer().getGas();
            int supplyProvided = accounting.getSupplyProvided(zerg.getPlayer());
            int supplyUsed = accounting.getSupplyUsed(zerg.getPlayer());

            Assertions.assertTrue(creepTiles > 0);
            Assertions.assertFalse(zerg.findUnits(ScrUnitIds.ZERG_LARVA).isEmpty());

            zerg.select(hive);
            zerg.morphBuilding(MYRIARCH_ID);
            runFrames(zerg, COMMAND_FRAMES);

            // Charged once when the morph starts; the building stays a Hive and shows the Myriarch.
            Assertions.assertEquals(minerals - MINERAL_COST, zerg.requirePlayer().getMinerals());
            Assertions.assertEquals(gas - GAS_COST, zerg.requirePlayer().getGas());
            Assertions.assertEquals(ScrUnitIds.ZERG_HIVE, hive.getUnitId());
            Assertions.assertTrue(UnitQueries.isBuildingMorph(units, hive));
            Assertions.assertEquals(MYRIARCH_ID, UnitQueries.getDisplayedType(units, hive));

            runUntilType(zerg, hive, MYRIARCH_ID);

            Assertions.assertEquals(ScrUnitIds.ZERG_HIVE, hive.getPreviousUnitType());
            Assertions.assertNotEquals(0, hive.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(0, hive.getStatus() & UnitStatusFlags.GROUNDED_BUILDING);
            Assertions.assertFalse(UnitQueries.isProducing(hive));
            Assertions.assertTrue(hive.getHitPoints() > 0 && hive.getHitPoints() <= units.getHitPoints(MYRIARCH_ID));
            Assertions.assertEquals(hiveX, hive.getPositionX());
            Assertions.assertEquals(hiveY, hive.getPositionY());
            // It takes off: a flyer with the air units' elevation and no ground collision.
            Assertions.assertNotEquals(0, hive.getStatus() & UnitStatusFlags.FLYING);
            Assertions.assertEquals(0, hive.getStatus() & UnitStatusFlags.GROUND_UNIT);
            Assertions.assertEquals(units.getElevationLevel(MYRIARCH_ID), hive.requireSprite().getElevationLevel());
            Assertions.assertEquals(units.getElevationLevel(ScrUnitIds.ZERG_GUARDIAN), units.getElevationLevel(MYRIARCH_ID));
            Assertions.assertEquals(0, units.getFlags(MYRIARCH_ID) & UnitTypeFlags.RESOURCE_DEPOT);
            Assertions.assertEquals(0, accounting.getTypeCount(ScrUnitIds.ZERG_HIVE, zerg.getPlayer()));
            Assertions.assertEquals(1, accounting.getCompletedTypeCount(MYRIARCH_ID, zerg.getPlayer()));
            Assertions.assertEquals(supplyProvided - units.getSupplyProvided(ScrUnitIds.ZERG_HIVE), accounting.getSupplyProvided(zerg.getPlayer()));
            Assertions.assertEquals(supplyUsed + units.getSupplyRequired(MYRIARCH_ID), accounting.getSupplyUsed(zerg.getPlayer()));

            for (Unit larva : zerg.findUnits(ScrUnitIds.ZERG_LARVA)) {
                Assertions.assertNull(larva.getParentUnit());
            }

            runFrames(zerg, AFTERMATH_FRAMES);

            Assertions.assertTrue(zerg.findUnits(ScrUnitIds.ZERG_LARVA).isEmpty(), "the Larvae die off creep");
            Assertions.assertTrue(zerg.countCreepTiles(hiveX, hiveY, CREEP_TILE_RADIUS) < creepTiles, "the creep recedes");
            Assertions.assertSame(hive, zerg.requireUnit(MYRIARCH_ID));
            Assertions.assertNull(zerg.getRuntime().getFailure());
        }
    }

    /**
     * Plays the Myriarch after the morph: it flies away from the Hive's place to a right-clicked position, then onto terrain no ground unit stands
     * on (a cliff or a gap of the map), attack-moves into an enemy building and breathes fire on it (each breath's projectiles splash it for the
     * weapon's damage, in full, half or a quarter by their distance, less the armour), and breathes fire on two enemy Science Vessels at one
     * place, the target and the one beside it (the splash); its projectiles are gone once the attack stops.
     *
     * @param installation the SC:R installation
     * @throws IOException if the installation cannot be read or the game fails
     */
    public static void flyAndFight(@NotNull Path installation) throws IOException {
        try (GameData gameData = GameData.loadFrom(installation)) {
            CloneZergPlayer zerg = start(gameData, installation);
            ScrUnitTypeView units = zerg.getGame().requireGameData().getUnitTypes();
            ScrWeaponTypeView weapons = zerg.getGame().requireGameData().getWeaponTypes();
            Unit myriarch = morphToHive(zerg);
            int hiveX = myriarch.getPositionX();
            int hiveY = myriarch.getPositionY();

            zerg.select(myriarch);
            zerg.morphBuilding(MYRIARCH_ID);
            runUntilType(zerg, myriarch, MYRIARCH_ID);

            // The Myriarch flies from the Hive's place to the clicked position.
            int targetX = hiveX - MOVE_DISTANCE;

            flyTo(zerg, myriarch, targetX, hiveY);

            // It flies onto terrain no ground unit stands on, and back.
            int[] unwalkable = findUnwalkableBlock(zerg, hiveX, hiveY);

            flyTo(zerg, myriarch, unwalkable[0], unwalkable[1]);
            flyTo(zerg, myriarch, targetX, hiveY);

            // It attack-moves into an enemy Supply Depot on its way, away from the workers, and breathes fire on it.
            int enemy = zerg.findEnemyPlayer();
            int weapon = units.getGroundWeapon(MYRIARCH_ID);
            Unit depot = zerg.createUnit(ScrUnitIds.TERRAN_SUPPLY_DEPOT, targetX - DEPOT_DISTANCE, hiveY, enemy);

            Assertions.assertEquals(weapon, units.getAirWeapon(MYRIARCH_ID));
            Assertions.assertTrue(weapon > ScrWeaponIds.NONE, "the fire breath is an added weapon");
            Assertions.assertNotEquals(0, weapons.getTargetFlags(weapon) & WeaponTargetFlags.AIR);
            Assertions.assertNotEquals(0, weapons.getTargetFlags(weapon) & WeaponTargetFlags.GROUND);

            zerg.order(ScrOrderIds.ATTACK_MOVE, targetX - MOVE_DISTANCE, hiveY, null);
            assertBreaths(zerg, myriarch, depot, null, weapons.getDamageAmount(weapon), units.getArmor(ScrUnitIds.TERRAN_SUPPLY_DEPOT));

            // It breathes fire on an enemy Science Vessel; the splash burns the one beside it.
            Unit vessel = zerg.createUnit(ScrUnitIds.TERRAN_SCIENCE_VESSEL, myriarch.getPositionX(), myriarch.getPositionY() - VESSEL_OFFSET, enemy);
            Unit beside = zerg.createUnit(ScrUnitIds.TERRAN_SCIENCE_VESSEL, vessel.getPositionX() + VESSEL_SPACING, vessel.getPositionY(), enemy);

            zerg.select(myriarch);
            zerg.rightClick(vessel.getPositionX(), vessel.getPositionY(), vessel);
            assertBreaths(zerg, myriarch, vessel, beside, weapons.getDamageAmount(weapon), units.getArmor(ScrUnitIds.TERRAN_SCIENCE_VESSEL));
            zerg.stop();
            runFrames(zerg, SETTLE_FRAMES);

            Assertions.assertEquals(0, zerg.getActiveBulletCount(), "the breath's projectiles are gone");
            Assertions.assertNull(zerg.getRuntime().getFailure());
        }
    }

    /**
     * Right-clicks a position for a unit and runs the game until it is idle there, flying the whole way.
     */
    private static void flyTo(@NotNull CloneZergPlayer zerg, @NotNull Unit unit, int positionX, int positionY) {
        zerg.select(unit);
        zerg.rightClick(positionX, positionY, null);
        runUntilIdle(zerg, unit);

        Assertions.assertNotEquals(0, unit.getStatus() & UnitStatusFlags.FLYING);
        Assertions.assertTrue(Math.abs(unit.getPositionX() - positionX) <= ARRIVAL_DISTANCE &&
                              Math.abs(unit.getPositionY() - positionY) <= ARRIVAL_DISTANCE,
                              () -> "the unit reaches " + positionX + "," + positionY + ", is at " + unit.getPositionX() + "," + unit.getPositionY());
    }

    /**
     * Finds the center of the block of walk cells no ground unit stands on nearest to a position, at least {@link #UNWALKABLE_DISTANCE} away.
     */
    private static int @NotNull [] findUnwalkableBlock(@NotNull CloneZergPlayer zerg, int positionX, int positionY) {
        ScrTerrainGrid terrain = zerg.getGame().getTerrain();
        int width = zerg.getGame().getMap().getWidth() * TerrainTileset.MINI_TILES_PER_ROW;
        int height = zerg.getGame().getMap().getHeight() * TerrainTileset.MINI_TILES_PER_ROW;
        int centerX = positionX / WALK_CELL_SIZE;
        int centerY = positionY / WALK_CELL_SIZE;
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;

        for (int top = Math.max(0, centerY - SEARCH_CELLS); top + UNWALKABLE_BLOCK_CELLS <= Math.min(height, centerY + SEARCH_CELLS); top++) {
            for (int left = Math.max(0, centerX - SEARCH_CELLS); left + UNWALKABLE_BLOCK_CELLS <= Math.min(width, centerX + SEARCH_CELLS); left++) {
                int x = (left * 2 + UNWALKABLE_BLOCK_CELLS) * WALK_CELL_SIZE / 2;
                int y = (top * 2 + UNWALKABLE_BLOCK_CELLS) * WALK_CELL_SIZE / 2;
                long distance = (long) (x - positionX) * (x - positionX) + (long) (y - positionY) * (y - positionY);

                if (distance < (long) UNWALKABLE_DISTANCE * UNWALKABLE_DISTANCE || distance >= bestDistance || !isUnwalkable(terrain, left, top)) {
                    continue;
                }

                best         = new int[]{x, y};
                bestDistance = distance;
            }
        }

        Assertions.assertNotNull(best, "the map has terrain no ground unit stands on near the Hive");

        return best;
    }

    private static boolean isUnwalkable(@NotNull ScrTerrainGrid terrain, int left, int top) {
        for (int y = top; y < top + UNWALKABLE_BLOCK_CELLS; y++) {
            for (int x = left; x < left + UNWALKABLE_BLOCK_CELLS; x++) {
                if (terrain.isWalkable(x, y)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Runs the game until a unit is idle again, failing after {@link #MOVE_FRAME_LIMIT} frames.
     */
    private static void runUntilIdle(@NotNull CloneZergPlayer zerg, @NotNull Unit unit) {
        int idleOrder = zerg.getGame().requireGameData().getUnitTypes().getReturnToIdleOrder(unit.getUnitId());

        runFrames(zerg, COMMAND_FRAMES);

        for (int frame = 0; unit.getPrimaryOrder() != idleOrder; frame++) {
            Assertions.assertTrue(frame < MOVE_FRAME_LIMIT, () -> "the unit becomes idle, its order is " + unit.getPrimaryOrder());

            step(zerg);
        }
    }

    /**
     * Runs the game until a target took {@link #HIT_COUNT} breaths: each frame's loss of hit points is the sum of the breath's projectile hits on
     * it, each the weapon's damage in full, half or a quarter (its splash share) less the armour, at least half a hit point. A unit beside the
     * target, if given, takes damage of the splash too. The attacker's picture never stays one frame for more than
     * {@link #MAXIMUM_STILL_FRAMES} game frames: it hovers between the breaths.
     */
    private static void assertBreaths(@NotNull CloneZergPlayer zerg, @NotNull Unit attacker, @NotNull Unit target, @Nullable Unit beside,
                                      int damage, int armor) {
        int hits = 0;
        int hitPoints = target.getHitPoints();
        int besideHitPoints = beside == null ? 0 : beside.getHitPoints();
        int picture = getPicture(attacker);
        int still = 0;

        for (int frame = 0; hits < HIT_COUNT; frame++) {
            int hit = hits;

            Assertions.assertTrue(frame < FIGHT_FRAME_LIMIT, () -> "the target takes " + HIT_COUNT + " breaths, took " + hit);

            step(zerg);

            int current = getPicture(attacker);

            still   = current == picture ? still + 1 : 0;
            picture = current;

            int stillFrames = still;

            Assertions.assertTrue(stillFrames <= MAXIMUM_STILL_FRAMES,
                                  () -> "the attacker's picture stays frame " + current + " for " + stillFrames + " game frames");

            if (target.getHitPoints() != hitPoints) {
                int loss = hitPoints - target.getHitPoints();

                Assertions.assertTrue(isSumOfSplashHits(loss, damage, armor, BREATH_HITS),
                                      () -> "a breath's loss " + loss + " is a sum of splash hits of " + damage + " less " + armor);

                hitPoints = target.getHitPoints();
                hits++;
            }
        }

        if (beside != null) {
            Assertions.assertTrue(beside.getHitPoints() < besideHitPoints, "the splash burns the unit beside the target");
        }
    }

    /**
     * @return the frame the unit's main image shows
     */
    private static int getPicture(@NotNull Unit unit) {
        SpriteImage image = unit.requireSprite().getMainImage();

        Assertions.assertNotNull(image);

        return image.getDrawnFrameIndex();
    }

    /**
     * Checks that a loss of hit points (in 1/256) is the sum of one to a number of hits, each a splash share of the damage less the armour.
     */
    private static boolean isSumOfSplashHits(int loss, int damage, int armor, int hits) {
        if (hits == 0) {
            return false;
        }

        for (int divisor : SPLASH_DIVISORS) {
            int hit = Math.max(((damage << HIT_POINT_FRACTION_BITS) / divisor) - (armor << HIT_POINT_FRACTION_BITS), MINIMUM_HIT_DAMAGE);

            if (hit == loss || hit < loss && isSumOfSplashHits(loss - hit, damage, armor, hits - 1)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Starts a live melee game of the human Zerg player against a Zerg computer on the installed Bottleneck.
     *
     * @param gameData     the installed data of the clone
     * @param installation the SC:R installation
     * @return the human player of the started game
     * @throws IOException if the map cannot be read or the game cannot start
     */
    public static @NotNull CloneZergPlayer start(@NotNull GameData gameData, @NotNull Path installation) throws IOException {
        byte[] mapData = readMap(installation);
        MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.ZERG, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);
        LiveGame live = new LiveGame(setup, GAME_SEED);
        ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(mapData), gameData, live.getLocalPlayer(),
                                                             VIEWPORT_WIDTH, VIEWPORT_HEIGHT, RunningGameScenario.INSTRUCTION_LIMIT);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        return new CloneZergPlayer(live, runtime);
    }

    /**
     * Reads the scenario of the installed Bottleneck.
     *
     * @param installation the SC:R installation
     * @return the scenario bytes
     * @throws IOException if the map cannot be read
     */
    public static byte @NotNull [] readMap(@NotNull Path installation) throws IOException {
        return MpqArchive.open(installation.resolve(MAPS_FOLDER).resolve(MAP_NAME)).readFile(SCENARIO_FILE);
    }

    /**
     * Prepares the morphs ({@link CloneZergPlayer#prepareMorphs}) and morphs the start Hatchery into a Lair and a Hive.
     *
     * @param zerg the player of a single player game
     * @return the Hive
     */
    public static @NotNull Unit morphToHive(@NotNull CloneZergPlayer zerg) {
        Unit hatchery = zerg.requireUnit(ScrUnitIds.ZERG_HATCHERY);

        runTo(zerg, PREPARE_FRAME);
        zerg.prepareMorphs(zerg.getPlayer());
        zerg.select(hatchery);
        zerg.morphBuilding(ScrUnitIds.ZERG_LAIR);
        runUntilType(zerg, hatchery, ScrUnitIds.ZERG_LAIR);
        zerg.select(hatchery);
        zerg.morphBuilding(ScrUnitIds.ZERG_HIVE);
        runUntilType(zerg, hatchery, ScrUnitIds.ZERG_HIVE);

        return hatchery;
    }

    /**
     * Runs the game until a unit is a type and completed, failing after {@link #MORPH_FRAME_LIMIT} frames.
     *
     * @param zerg     the player of a single player game
     * @param unit     the unit
     * @param unitType the type
     */
    public static void runUntilType(@NotNull CloneZergPlayer zerg, @NotNull Unit unit, int unitType) {
        for (int frame = 0; unit.getUnitId() != unitType || (unit.getStatus() & UnitStatusFlags.COMPLETED) == 0; frame++) {
            Assertions.assertTrue(frame < MORPH_FRAME_LIMIT, () -> "the unit becomes type " + unitType + ", is " + unit.getUnitId());

            step(zerg);
        }
    }

    /**
     * Runs a number of frames of a single player game.
     *
     * @param zerg   the player of a single player game
     * @param frames the frames
     */
    public static void runFrames(@NotNull CloneZergPlayer zerg, int frames) {
        runTo(zerg, zerg.getGame().getCurrentFrame() + frames);
    }

    private static void runTo(@NotNull CloneZergPlayer zerg, int frame) {
        while (zerg.getGame().getCurrentFrame() < frame) {
            step(zerg);
        }
    }

    /**
     * Exchanges the turn and runs one frame of a single player game.
     */
    private static void step(@NotNull CloneZergPlayer zerg) {
        int frame = zerg.getGame().getCurrentFrame();

        zerg.getLive().exchangeTurn(frame);

        Assertions.assertTrue(zerg.getRuntime().step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
        Assertions.assertNull(zerg.getRuntime().getFailure());
    }

    private CloneMorphScenario() {
    }
}
