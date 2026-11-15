package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandCodecs;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayTargetCommand;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * The local Zerg player of a live game of the clone (single player or one client of a LAN game) that the morph scenarios drive: the preparation of
 * the morph (resources, fast building, the completed Zerg tech buildings and Overlords), its commands (selection, building morph) and queries of its
 * units and of the creep.
 */
public final class CloneZergPlayer {
    /**
     * The units the Hive and the Myriarch need, created completed beside the start Hatchery, each with its offset from the Hatchery in pixels: the
     * tech buildings (Spawning Pool, Queen's Nest, Ultralisk Cavern and Defiler Mound) and two Overlords for the Myriarch's eight supply.
     */
    private static final int @NotNull [] @NotNull [] PREPARED_UNITS = {{ScrUnitIds.ZERG_SPAWNING_POOL, -160, -128},
                                                                       {ScrUnitIds.ZERG_QUEENS_NEST, 160, -128},
                                                                       {ScrUnitIds.ZERG_ULTRALISK_CAVERN, -160, 128},
                                                                       {ScrUnitIds.ZERG_DEFILER_MOUND, 160, 128},
                                                                       {ScrUnitIds.ZERG_OVERLORD, 0, -96},
                                                                       {ScrUnitIds.ZERG_OVERLORD, 0, 96}};
    /**
     * The elements of a prepared unit: its type and its offset from the Hatchery.
     */
    private static final int PREPARED_TYPE = 0;
    private static final int PREPARED_OFFSET_X = 1;
    private static final int PREPARED_OFFSET_Y = 2;
    /**
     * The minerals and gas the player gets for the Lair, Hive and Myriarch morphs.
     */
    private static final int RESOURCES = 5000;
    /**
     * The selection command of one unit: the unit count, then the handle.
     */
    private static final int SELECTION_SIZE = Byte.BYTES + Integer.BYTES;

    private final @NotNull LiveGame live;
    private final @NotNull ReplayRuntime runtime;
    private final @NotNull Game game;
    private final int player;

    /**
     * Creates a {@link CloneZergPlayer} instance over a started live game runtime.
     *
     * @param live    the live game
     * @param runtime the started runtime of the live game
     */
    public CloneZergPlayer(@NotNull LiveGame live, @NotNull ReplayRuntime runtime) {
        this.live    = live;
        this.runtime = runtime;
        this.game    = runtime.getGame();
        this.player  = live.getLocalPlayer();
    }

    /**
     * @return the live game
     */
    public @NotNull LiveGame getLive() {
        return this.live;
    }

    /**
     * @return the runtime
     */
    public @NotNull ReplayRuntime getRuntime() {
        return this.runtime;
    }

    /**
     * @return the game
     */
    public @NotNull Game getGame() {
        return this.game;
    }

    /**
     * @return the local player
     */
    public int getPlayer() {
        return this.player;
    }

    /**
     * Returns the local player's state, failing if the game has none.
     *
     * @return the local player's state
     */
    public @NotNull Player requirePlayer() {
        Player owner = this.game.getPlayerTable().getPlayer(this.player);

        Assertions.assertNotNull(owner);

        return owner;
    }

    /**
     * Prepares the morphs of players inside a command dispatch, as a game's own step: each player gets {@link #RESOURCES} minerals and gas and the
     * completed tech buildings and Overlords beside its start Hatchery, and the game builds sixteen times as fast (the Operation Cwal flag, which a
     * game of several humans takes from no cheat). Every client of a game must do it for the same players in the same frame.
     *
     * @param players the players, in the same order on every client
     */
    public void prepareMorphs(int @NotNull ... players) {
        GameFrame frame = this.game.getFrame();

        Assertions.assertNotNull(frame);
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            frame.getVisibility().setCheatFlags(frame.getVisibility().getCheatFlags() | GameCheats.OPERATION_CWAL);

            for (int owner : players) {
                Player state = this.game.getPlayerTable().getPlayer(owner);
                List<Unit> hatcheries = this.findUnits(ScrUnitIds.ZERG_HATCHERY, owner);

                Assertions.assertNotNull(state);
                Assertions.assertEquals(1, hatcheries.size(), "player " + owner + " has one Hatchery");
                state.setMinerals(RESOURCES);
                state.setGas(RESOURCES);

                for (int[] building : PREPARED_UNITS) {
                    RunningGameScenario.createUnit(this.game, building[PREPARED_TYPE],
                                                   hatcheries.getFirst().getPositionX() + building[PREPARED_OFFSET_X],
                                                   hatcheries.getFirst().getPositionY() + building[PREPARED_OFFSET_Y], owner);
                }
            }
        });
    }

    /**
     * Submits the selection of one unit.
     *
     * @param unit the unit
     */
    public void select(@NotNull Unit unit) {
        ByteBuffer selection = ByteBuffer.allocate(SELECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        selection.put((byte) 1).putInt(this.game.getUnitSystem().requireUnitPool().getHandle(unit.getIndex()));

        Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, selection.array()));
    }

    /**
     * Submits the building morph of the selected building into a type.
     *
     * @param unitType the type
     */
    public void morphBuilding(int unitType) {
        ByteBuffer morph = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        morph.putShort((short) unitType);

        Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILDING_MORPH, morph.array()));
    }

    /**
     * Submits a right click of the selected units on a position or a unit.
     *
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param target    the clicked unit, or {@code null} for the ground
     */
    public void rightClick(int positionX, int positionY, @Nullable Unit target) {
        ReplayTargetCommand command = ReplayTargetCommand.createRightClick(positionX, positionY, this.getHandle(target), Integer.BYTES,
                                                                           ScrUnitIds.NONE, 0);

        Assertions.assertTrue(this.live.submit(ReplayCommandIds.RIGHT_CLICK_121,
                                               ReplayCommandCodecs.encode(ReplayCommandCodecs.RIGHT_CLICK_121, command)));
    }

    /**
     * Submits an order of the selected units on a position or a unit, as a targeting click of the command card.
     *
     * @param order     the order
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param target    the clicked unit, or {@code null} for the ground
     */
    public void order(int order, int positionX, int positionY, @Nullable Unit target) {
        ReplayTargetCommand command = ReplayTargetCommand.createTargetedOrder(positionX, positionY, this.getHandle(target), Integer.BYTES,
                                                                              ScrUnitIds.NONE, order, 0);

        Assertions.assertTrue(this.live.submit(ReplayCommandIds.TARGETED_ORDER_121,
                                               ReplayCommandCodecs.encode(ReplayCommandCodecs.TARGETED_ORDER_121, command)));
    }

    private long getHandle(@Nullable Unit unit) {
        return unit == null ? 0 : this.game.getUnitSystem().requireUnitPool().getHandle(unit.getIndex());
    }

    /**
     * Submits the stop of the selected units.
     */
    public void stop() {
        Assertions.assertTrue(this.live.submit(ReplayCommandIds.STOP, new byte[]{0}));
    }

    /**
     * Creates a completed unit inside a command dispatch, as a game's own step.
     *
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param owner     the owner
     * @return the created unit
     */
    public @NotNull Unit createUnit(int unitType, int positionX, int positionY, int owner) {
        List<Unit> created = new ArrayList<>();

        RunningGameScenario.runInCommandDispatch(this.runtime,
                                                 () -> created.add(RunningGameScenario.createUnit(this.game, unitType, positionX, positionY, owner)));

        return created.getFirst();
    }

    /**
     * Finds the first other player that has a Hatchery, failing if there is none.
     *
     * @return the enemy player: the computer of a single player game
     */
    public int findEnemyPlayer() {
        for (int owner = 0; owner < PlayerTable.PLAYABLE_PLAYER_SLOTS; owner++) {
            if (owner != this.player && !this.findUnits(ScrUnitIds.ZERG_HATCHERY, owner).isEmpty()) {
                return owner;
            }
        }

        return Assertions.fail("the game has an enemy player");
    }

    /**
     * @return the active bullets of the game
     */
    public int getActiveBulletCount() {
        GameFrame frame = this.game.getFrame();

        Assertions.assertNotNull(frame);

        return frame.getProjectiles().getActiveCount();
    }

    /**
     * Returns the player's only unit of a type, failing if the player has none or more than one.
     *
     * @param unitType the unit type
     * @return the player's only unit of the type
     */
    public @NotNull Unit requireUnit(int unitType) {
        List<Unit> found = this.findUnits(unitType);

        Assertions.assertEquals(1, found.size(), "one unit of type " + unitType);

        return found.getFirst();
    }

    /**
     * Finds the player's units of a type that are alive and shown.
     *
     * @param unitType the unit type
     * @return the player's units of the type that are alive and shown
     */
    public @NotNull List<@NotNull Unit> findUnits(int unitType) {
        return this.findUnits(unitType, this.player);
    }

    private @NotNull List<@NotNull Unit> findUnits(int unitType, int owner) {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();
        List<Unit> found = new ArrayList<>();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() == unitType && !unit.isDestroyed() && unit.getSpriteOrNull() != null) {
                found.add(unit);
            }
        }

        return found;
    }

    /**
     * Counts the creep tiles in a square of tiles around a position.
     *
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param radius    the half side of the square, in tiles
     * @return the creep tiles
     */
    public int countCreepTiles(int positionX, int positionY, int radius) {
        GameMap map = this.game.getMap();
        int centerX = positionX / TerrainTileset.TILE_SIZE;
        int centerY = positionY / TerrainTileset.TILE_SIZE;
        int count = 0;

        for (int tileY = Math.max(0, centerY - radius); tileY <= Math.min(map.getHeight() - 1, centerY + radius); tileY++) {
            for (int tileX = Math.max(0, centerX - radius); tileX <= Math.min(map.getWidth() - 1, centerX + radius); tileX++) {
                if ((map.getTileFlags(tileX, tileY) & MapTileFlags.HAS_CREEP) != 0) {
                    count++;
                }
            }
        }

        return count;
    }
}
