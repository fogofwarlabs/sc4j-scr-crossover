package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scrcrossover.CloneMorphScenario;
import org.fogofwarlabs.sc4j.scrcrossover.CloneZergPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test code that runs inside the clone of SC:R Crossover and plays a LAN game of the clone with the Myriarch's morph: two Zerg players join over the
 * SC:R mod's in-memory LAN scenario (the clone's own LAN lobby and turn transport), each prepares its morphs in the same frame
 * ({@link CloneZergPlayer#prepareMorphs}) and morphs its start Hatchery into a Lair, a Hive and the Myriarch by commands, then flies the Myriarch
 * away from the Hive's place; both clients hash every frame alike, report no Sync mismatch and end with both players' Myriarchs at their flights'
 * ends.
 */
public final class CloneLanMorphScenario {
    private static final int PLAYERS = 2;
    private static final int HOST_CLIENT = 0;
    private static final int JOIN_CLIENT = 1;
    private static final int LATENCY = 2;
    private static final int TURNS_IN_TRANSIT = 3;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int ITERATION_LIMIT = 400_000;
    private static final int STATE_GAME = 9;
    private static final @NotNull String MAPS_FOLDER = "Maps";
    private static final int MAP_TRANSFER_LIMIT_MILLISECONDS = 20_000;
    private static final int LOBBY_SETTLE_MILLISECONDS = 1000;
    private static final int GAME_START_LIMIT_MILLISECONDS = 20_000;
    /**
     * The frames the game runs: the three morphs at sixteen times the building speed take about 400.
     */
    private static final int FRAMES = 900;
    /**
     * The pixels each Myriarch flies from its Hive's place toward the middle of the map, and the pixels it may stop short.
     */
    private static final int WALK_DISTANCE = 256;
    private static final int ARRIVAL_DISTANCE = 32;

    /**
     * The morph each client commands next.
     */
    private static final int @NotNull [] MORPHS = {ScrUnitIds.ZERG_LAIR, ScrUnitIds.ZERG_HIVE, CloneMorphScenario.MYRIARCH_ID};

    /**
     * Plays the LAN game and checks it.
     *
     * @param installation the SC:R installation
     * @throws Exception if the installation cannot be read or the game fails
     */
    public static void playMorph(@NotNull Path installation) throws Exception {
        Path mapPath = installation.resolve(MAPS_FOLDER).resolve(CloneMorphScenario.MAP_NAME);
        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = CloneMorphScenario.readMap(installation);
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.MELEE;
        InMemoryLanScenario network = new InMemoryLanScenario(PLAYERS);
        LanLobby.Setup setup = LanLobby.Setup.create(scenario, options, "host", CloneMorphScenario.MAP_NAME, mapFile.length,
                                                     LanMapTransfer.calculateChecksum(mapFile, mapFile.length));
        LanLobby[] lobbies = network.lobbies;

        network.latency = LATENCY;
        network.host(setup, options.encode(), mapPath);
        network.join(JOIN_CLIENT, mapPath.getParent(), mapPath.getParent().resolve("Download"));
        network.runUntil(MAP_TRANSFER_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[HOST_CLIENT];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });
        network.run(LOBBY_SETTLE_MILLISECONDS);

        for (LanLobby lobby : lobbies) {
            Assertions.assertNotNull(lobby);

            lobby.changeRace(ScrRaceIds.ZERG);
        }

        network.run(LOBBY_SETTLE_MILLISECONDS);

        LanLobby host = lobbies[HOST_CLIENT];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.startGame());

        network.runUntil(GAME_START_LIMIT_MILLISECONDS, lan -> {
            for (LanLobby lobby : lan.lobbies) {
                Assertions.assertNotNull(lobby);

                if (lobby.getState() != STATE_GAME) {
                    return false;
                }
            }

            return true;
        });

        try (GameData data = GameData.loadFrom(installation)) {
            CloneZergPlayer[] clients = new CloneZergPlayer[PLAYERS];
            int[] nextMorph = new int[PLAYERS];
            long[] commanded = {-1, -1};
            int[] players = new int[PLAYERS];
            int[] walkX = new int[PLAYERS];
            int[] walkY = new int[PLAYERS];

            for (int index = 0; index < PLAYERS; index++) {
                LanLobby lobby = lobbies[index];

                Assertions.assertNotNull(lobby);

                MeleeGameSetup gameSetup = lobby.createGameSetup(mapData, GameSpeed.FASTEST, true);
                LiveGame live = new LiveGame(gameSetup, lobby.getSeed(), network.sessions[index].getLocalIdentifier());

                live.attachNetwork(new LanTurnTransport(network.sessions[index], network::deliver), TURNS_IN_TRANSIT, false);

                ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario, data, live.getLocalPlayer(), true,
                                                                     VIEWPORT_WIDTH, VIEWPORT_HEIGHT, INSTRUCTION_LIMIT);

                runtime.setSourceFile(GameSourceFile.create(mapPath, mapFile));
                live.setSyncSource(runtime.getGame(), () -> 0);
                runtime.start(0, network.now);

                Assertions.assertNull(runtime.getFailure());

                ReplaySession session = runtime.getSession();

                Assertions.assertNotNull(session);

                live.attachReplaySession(session);
                clients[index] = new CloneZergPlayer(live, runtime);
                players[index] = live.getLocalPlayer();

                Unit hatchery = clients[index].requireUnit(ScrUnitIds.ZERG_HATCHERY);
                int middleX = clients[index].getGame().getMap().getWidth() * TerrainTileset.TILE_SIZE / 2;

                walkX[index] = hatchery.getPositionX() + (hatchery.getPositionX() < middleX ? WALK_DISTANCE : -WALK_DISTANCE);
                walkY[index] = hatchery.getPositionY();
            }

            for (int iteration = 0; iteration < ITERATION_LIMIT; iteration++) {
                boolean done = true;

                network.now++;

                for (int index = 0; index < PLAYERS; index++) {
                    CloneZergPlayer client = clients[index];
                    long frame = client.getGame().getCurrentFrame();

                    if (frame >= FRAMES) {
                        continue;
                    }

                    done = false;

                    if (commanded[index] != frame) {
                        nextMorph[index] = command(client, frame, nextMorph[index], players, walkX[index], walkY[index]);
                        commanded[index] = frame;
                    }

                    if (!client.getLive().exchangeTurn(frame)) {
                        continue;
                    }

                    client.getRuntime().step(FRAME_MILLISECONDS, network.now);

                    Assertions.assertNull(client.getRuntime().getFailure(), () -> "frame " + client.getGame().getCurrentFrame() + ": " +
                                                                                  client.getRuntime().getFailure());
                }

                long frame = clients[HOST_CLIENT].getGame().getCurrentFrame();

                if (clients[JOIN_CLIENT].getGame().getCurrentFrame() == frame) {
                    Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(clients[HOST_CLIENT].getGame()),
                                            SyncChecksumUtils.calculateUnitHash(clients[JOIN_CLIENT].getGame()), "unit hash at frame " + frame);
                }

                if (done) {
                    break;
                }
            }

            for (int index = 0; index < PLAYERS; index++) {
                Unit myriarch = clients[index].requireUnit(CloneMorphScenario.MYRIARCH_ID);
                int clientIndex = index;

                Assertions.assertTrue(Math.abs(myriarch.getPositionX() - walkX[index]) <= ARRIVAL_DISTANCE &&
                                      Math.abs(myriarch.getPositionY() - walkY[index]) <= ARRIVAL_DISTANCE,
                                      () -> "player " + players[clientIndex] + "'s Myriarch flies to " + walkX[clientIndex] + "," +
                                            walkY[clientIndex] + ", is at " + myriarch.getPositionX() + "," + myriarch.getPositionY());
            }

            for (CloneZergPlayer client : clients) {
                UnitAccounting accounting = client.getGame().getPlayerTable().requireUnitAccounting();

                Assertions.assertEquals(FRAMES, client.getGame().getCurrentFrame());
                Assertions.assertEquals(0, client.getLive().getSyncMismatches());

                for (CloneZergPlayer owner : clients) {
                    Assertions.assertEquals(1, accounting.getCompletedTypeCount(CloneMorphScenario.MYRIARCH_ID, owner.getPlayer()),
                                            "player " + owner.getPlayer() + " has the Myriarch");
                    Assertions.assertEquals(0, accounting.getTypeCount(ScrUnitIds.ZERG_HIVE, owner.getPlayer()));
                }
            }
        }
    }

    /**
     * Gives a client's commands at a frame: the preparation of every player's morphs, then each morph of the local player once the morph before it
     * is complete, then the walk of the completed Myriarch to a position.
     *
     * @return the index of the next morph
     */
    private static int command(@NotNull CloneZergPlayer client, long frame, int nextMorph, int @NotNull [] players, int walkX, int walkY) {
        if (frame == CloneMorphScenario.PREPARE_FRAME) {
            client.prepareMorphs(players);
        }

        if (frame < CloneMorphScenario.PREPARE_FRAME || nextMorph > MORPHS.length) {
            return nextMorph;
        }

        if (nextMorph == MORPHS.length) {
            Unit myriarch = client.findUnits(CloneMorphScenario.MYRIARCH_ID).stream().findFirst().orElse(null);

            if (myriarch == null || (myriarch.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                return nextMorph;
            }

            client.select(myriarch);
            client.rightClick(walkX, walkY, null);

            return nextMorph + 1;
        }

        Unit base = client.findUnits(nextMorph == 0 ? ScrUnitIds.ZERG_HATCHERY : MORPHS[nextMorph - 1]).stream().findFirst().orElse(null);

        if (base == null || (base.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            return nextMorph;
        }

        client.select(base);
        client.morphBuilding(MORPHS[nextMorph]);

        return nextMorph + 1;
    }

    private CloneLanMorphScenario() {
    }
}
