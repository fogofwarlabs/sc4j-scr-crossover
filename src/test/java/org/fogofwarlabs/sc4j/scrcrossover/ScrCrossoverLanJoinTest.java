package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.net.UdpDatagramEndpoint;
import org.fogofwarlabs.sc4j.engine.net.lobby.GameLobby;
import org.fogofwarlabs.sc4j.engine.net.lobby.LobbyState;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.net.lan.LanGameOptions;
import org.fogofwarlabs.sc4j.scr.net.lan.LanLobby;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

/**
 * Tests the LAN join between SC:R Crossover and the SC:R mod on 127.0.0.1 without discovery: the LAN lobby, provider session and game socket of each
 * mod come from its built jar in its own mod class loader ({@link ModJarScenario}) and run over a loopback UDP endpoint. A LAN host of either
 * mod refuses a joiner of the other with the native version refusal, and accepts a joiner of its own mod.
 * <p>
 * The two mods' classes share only the engine's types ({@link GameLobby}, {@link LobbyState}) with the test.
 */
final class ScrCrossoverLanJoinTest {
    private static final @NotNull String SCR_ID = "scr";
    private static final @NotNull String CROSSOVER_ID = "scr-crossover";

    private static final @NotNull String GAME_PROXY_SOCKET = "org.fogofwarlabs.sc4j.scr.net.proxy.GameProxySocket";
    private static final @NotNull String PROVIDER_SESSION = "org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession";
    private static final @NotNull String LAN_LOBBY = "org.fogofwarlabs.sc4j.scr.net.lan.LanLobby";
    private static final @NotNull String LAN_LOBBY_SETUP = LAN_LOBBY + "$Setup";
    private static final @NotNull String PLAYER_RECORD = "org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup$PlayerRecord";
    private static final @NotNull String JOIN_VERSION_FIELD = "JOIN_VERSION";
    /**
     * The join version of the crossover's overlay of {@code lan-lobby.properties}.
     */
    private static final int CROSSOVER_JOIN_VERSION = 0xD005;
    private static final @NotNull String JOINED_STATE = "JOINED";

    /**
     * The refusal code of the host's refusal message for a joiner with another lobby join version (version mismatch).
     */
    private static final int VERSION_REFUSAL = 7;
    /**
     * The opaque player block of the joined-game command.
     */
    private static final int PLAYER_BLOCK_SIZE = 350;

    /**
     * The host's lobby table: the host in slot {@code 0}, slot {@code 1} open, the other player slots empty and the observer slots closed.
     */
    private static final int HOST_SLOT = 0;
    private static final int OPEN_SLOT = 1;
    private static final int HOST_PLAYER = 0;
    private static final int NO_PLAYER = -1;
    private static final int MELEE_FORCE = 0;
    private static final int TILESET = 1;
    private static final int MAP_SIZE = 128;
    private static final int FORCE_FLAGS_SIZE = 8;
    private static final @NotNull String TEMPLATE_BYTES = "060606000000000000000000";
    private static final @NotNull String MAP_FILE_NAME = "melee.scm";

    private static final int CLOCK_BASE = 10_000_000;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final long JOIN_LIMIT_MILLISECONDS = 15_000L;
    private static final long STEP_MILLISECONDS = 1L;

    /**
     * One mod's side of a LAN game on a loopback UDP endpoint: the mod's classes from its jar, its game socket and provider session, and its
     * lobby once it exists (at once on a host, after the provider joined on a joiner).
     */
    private static final class ModLanPeer implements AutoCloseable {
        private static @NotNull ModLanPeer openHost(@NotNull String modId, @NotNull Path jar) throws ReflectiveOperationException, IOException {
            ModLanPeer peer = new ModLanPeer(modId, jar);

            peer.mod.invoke(peer.session, "createGame", "game", "statistics", "", PlayerTable.PLAYABLE_PLAYER_SLOTS, 0, 0, 0,
                            LanGameOptions.MELEE.encode(), "host", "");
            peer.lobby = peer.createLobby(peer.createSetup(), "host");

            return peer;
        }

        private static @NotNull ModLanPeer openJoiner(@NotNull String modId, @NotNull Path jar, @NotNull ModLanPeer host) throws ReflectiveOperationException,
                                                                                                                                 IOException {
            ModLanPeer peer = new ModLanPeer(modId, jar);

            peer.mod.invoke(peer.session, "beginJoin", host.endpoint.getLocalAddress(), "joiner", "", "");

            return peer;
        }


        private final @NotNull ModJarScenario mod;
        private final @NotNull IntSupplier clock;

        private final @NotNull UdpDatagramEndpoint endpoint;
        private final @NotNull Object socket;
        private final @NotNull Object session;
        private @Nullable Object lobby;

        private ModLanPeer(@NotNull String modId, @NotNull Path jar) throws ReflectiveOperationException, IOException {
            long start = System.nanoTime();

            this.mod   = new ModJarScenario(modId, jar);
            this.clock = () -> CLOCK_BASE + (int) ((System.nanoTime() - start) / NANOSECONDS_PER_MILLISECOND);

            this.endpoint = UdpDatagramEndpoint.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            this.socket   = ModJarScenario.require(this.mod.callStatic(GAME_PROXY_SOCKET, "open",
                                                                       this.endpoint, this.endpoint.getLocalAddress().getPort()));
            this.session  = this.mod.construct(PROVIDER_SESSION, this.socket, this.clock);
            this.lobby    = null;
        }

        private int getRefusal() throws ReflectiveOperationException {
            return (Integer) ModJarScenario.require(this.mod.invoke(this.requireLobby(), "getRefusal"));
        }

        private @NotNull GameLobby requireLobby() {
            Assertions.assertNotNull(this.lobby, "the lobby exists");

            return (GameLobby) this.lobby;
        }

        /**
         * @return {@code true} if the lobby exists and was refused or shows this machine in a slot, {@code false} otherwise
         */
        private boolean isSettled() {
            GameLobby gameLobby = (GameLobby) this.lobby;

            return gameLobby != null && (gameLobby.getLobbyState() == LobbyState.REFUSED || gameLobby.getLocalMember() != null);
        }

        /**
         * Delivers the received datagrams to the provider session, updates it, creates a joiner's lobby once the provider joined, and updates
         * the lobby.
         */
        private void update() throws ReflectiveOperationException {
            this.mod.invoke(this.socket, "poll", (BiConsumer<InetSocketAddress, byte[]>) (source, packet) -> {
                try {
                    this.mod.invoke(this.session, "receive", source, packet);
                } catch (ReflectiveOperationException exception) {
                    throw Exceptions.newIllegalStateException(exception, "The provider session cannot take a packet.");
                }
            });
            this.mod.invoke(this.session, "update");

            if (this.lobby == null && this.mod.invoke(this.session, "pollJoin") instanceof Enum<?> state && state.name().equals(JOINED_STATE)) {
                this.lobby = this.createLobby(null, "joiner");
            }

            if (this.lobby != null) {
                this.mod.invoke(this.lobby, "update");
            }
        }

        private @NotNull Object createLobby(@Nullable Object setup, @NotNull String name) throws ReflectiveOperationException {
            return this.mod.construct(LAN_LOBBY, this.session, this.clock, setup, name, new byte[PLAYER_BLOCK_SIZE]);
        }

        /**
         * Creates the host's lobby table in the mod's own classes.
         */
        private @NotNull Object createSetup() throws ReflectiveOperationException {
            Object records = Array.newInstance(this.mod.loadClass(PLAYER_RECORD), LanLobby.RECORDS);
            byte[] template = HexFormat.of().parseHex(TEMPLATE_BYTES);

            for (int slot = 0; slot < LanLobby.RECORDS; slot++) {
                Object record;

                if (slot == HOST_SLOT) {
                    record = this.mod.construct(PLAYER_RECORD, HOST_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.RANDOM, MELEE_FORCE, "host");
                } else if (slot == OPEN_SLOT) {
                    record = this.mod.construct(PLAYER_RECORD, NO_PLAYER, LanLobby.TYPE_OPEN, ScrRaceIds.RANDOM, MELEE_FORCE, "");
                } else {
                    record = this.mod.construct(PLAYER_RECORD, NO_PLAYER,
                                                slot < PlayerTable.PLAYABLE_PLAYER_SLOTS ? PlayerTypes.NONE : PlayerTypes.CLOSED, 0, 0, "");
                }

                Array.set(records, slot, record);
            }

            return this.mod.construct(LAN_LOBBY_SETUP, TILESET,
                                      MAP_SIZE, MAP_SIZE,
                                      template, template, template,
                                      new byte[LanLobby.RECORDS], new byte[FORCE_FLAGS_SIZE],
                                      new byte[0],
                                      records,
                                      MAP_FILE_NAME, 1, 1);
        }

        @Override
        public void close() throws ReflectiveOperationException, IOException {
            try {
                this.mod.invoke(this.socket, "close");
            } finally {
                this.mod.close();
            }
        }
    }

    /**
     * Runs both peers until the joiner's lobby was refused or shows the joiner in a slot.
     */
    private static void settle(@NotNull ModLanPeer host, @NotNull ModLanPeer joiner) throws ReflectiveOperationException, InterruptedException {
        long deadline = System.nanoTime() + JOIN_LIMIT_MILLISECONDS * NANOSECONDS_PER_MILLISECOND;

        while (!joiner.isSettled()) {
            if (System.nanoTime() - deadline > 0) {
                Assertions.fail("the LAN join neither ended refused nor placed the joiner");
            }

            host.update();
            joiner.update();
            Thread.sleep(STEP_MILLISECONDS);
        }
    }


    @Test
    void a_lan_host_of_the_sc_r_mod_refuses_a_crossover_joiner_with_the_version_refusal() throws Exception {
        try (ModLanPeer host = ModLanPeer.openHost(SCR_ID, ScrCrossoverTestResources.getScrModJar());
             ModLanPeer joiner = ModLanPeer.openJoiner(CROSSOVER_ID, ScrCrossoverTestResources.getModJar(), host)) {
            settle(host, joiner);

            Assertions.assertEquals(VERSION_REFUSAL, joiner.getRefusal());
            Assertions.assertEquals(LobbyState.REFUSED, joiner.requireLobby().getLobbyState());
            Assertions.assertEquals(1, host.requireLobby().getMembers().size(), "the SC:R host holds only itself");
        }
    }

    @Test
    void a_lan_host_of_the_crossover_refuses_a_sc_r_joiner_with_the_version_refusal() throws Exception {
        try (ModLanPeer host = ModLanPeer.openHost(CROSSOVER_ID, ScrCrossoverTestResources.getModJar());
             ModLanPeer joiner = ModLanPeer.openJoiner(SCR_ID, ScrCrossoverTestResources.getScrModJar(), host)) {
            settle(host, joiner);

            Assertions.assertEquals(VERSION_REFUSAL, joiner.getRefusal());
            Assertions.assertEquals(LobbyState.REFUSED, joiner.requireLobby().getLobbyState());
            Assertions.assertEquals(1, host.requireLobby().getMembers().size(), "the crossover host holds only itself");
        }
    }

    @Test
    void a_lan_host_accepts_a_joiner_of_its_own_mod() throws Exception {
        for (String modId : new String[]{SCR_ID, CROSSOVER_ID}) {
            Path jar = modId.equals(SCR_ID) ? ScrCrossoverTestResources.getScrModJar() : ScrCrossoverTestResources.getModJar();

            try (ModLanPeer host = ModLanPeer.openHost(modId, jar);
                 ModLanPeer joiner = ModLanPeer.openJoiner(modId, jar, host)) {
                settle(host, joiner);

                Assertions.assertEquals(LanLobby.NO_REFUSAL, joiner.getRefusal(), modId);
                Assertions.assertNotNull(joiner.requireLobby().getLocalMember(), modId);
                Assertions.assertEquals(2, host.requireLobby().getMembers().size(), modId);
            }
        }
    }

    @Test
    void the_crossover_lan_lobby_has_its_own_join_version() throws ReflectiveOperationException, IOException {
        try (ModJarScenario crossover = new ModJarScenario(CROSSOVER_ID, ScrCrossoverTestResources.getModJar())) {
            int crossoverVersion = crossover.loadClass(LAN_LOBBY).getField(JOIN_VERSION_FIELD).getInt(null);

            Assertions.assertEquals(CROSSOVER_JOIN_VERSION, crossoverVersion);
            Assertions.assertNotEquals(LanLobby.JOIN_VERSION, crossoverVersion);
        }
    }
}
