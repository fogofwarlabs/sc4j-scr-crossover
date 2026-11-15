package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.beaconlink.client.BeaconLinkClient;
import org.fogofwarlabs.sc4j.beaconlink.peer.PeerSocket;
import org.fogofwarlabs.sc4j.beaconlink.test.client.ClientScenario;
import org.fogofwarlabs.sc4j.engine.mod.identity.ModIdentity;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.*;
import org.fogofwarlabs.sc4j.engine.net.lobby.JoinRefusal;
import org.fogofwarlabs.sc4j.manifest.ManifestException;
import org.fogofwarlabs.sc4j.scr.net.beaconlink.BeaconLinkProvider;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanRecord;
import org.fogofwarlabs.sc4j.scr.net.proxy.GameProxySocket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Tests the join of SC:R Crossover into a Beacon.Link room of the SC:R mod on 127.0.0.1, with the identities the runtime computes for the two built
 * jars: the clone lists no room of the SC:R mod, and a room that the SC:R mod's Beacon.Link provider hosts refuses the clone's join as
 * another mod.
 */
final class ScrCrossoverRoomJoinTest {
    private static final @NotNull String GAME_NAME = "s4jscrgame";
    private static final long CONNECT_MILLISECONDS = 20_000L;
    private static final long STEP_MILLISECONDS = 100L;

    /**
     * Searches with another client of the SC:R mod until it lists the host's room.
     */
    private static @NotNull FoundRoom findRoom(@NotNull ClientScenario link, @NotNull BeaconLinkClient searcher) throws IOException {
        for (long waited = 0; waited < CONNECT_MILLISECONDS; waited += STEP_MILLISECONDS) {
            RoomSearch search = searcher.searchRooms();

            link.await(search::isComplete, "the SC:R search completes");

            if (!search.getRooms().isEmpty()) {
                return search.getRooms().getFirst();
            }

            link.advance(STEP_MILLISECONDS);
        }

        return Assertions.fail("no client of the SC:R mod lists the host's room");
    }

    private static @NotNull BeaconLinkClient openClient(@NotNull ClientScenario link, @NotNull PeerSocket node, @NotNull ModIdentity identity) throws IOException {
        BeaconLinkClient client = link.openClient(identity, List.of(ClientScenario.listed(node)));

        link.await(() -> client.getObservedAddress() != null, "the client registers");

        return client;
    }


    @Test
    void a_beacon_link_room_of_the_sc_r_mod_refuses_the_crossover_as_another_mod() throws IOException, ManifestException {
        ModIdentity scr = ScrCrossoverJarTest.createIdentity(ScrCrossoverTestResources.getScrModJar());
        ModIdentity crossover = ScrCrossoverJarTest.createIdentity(ScrCrossoverTestResources.getModJar());

        try (ClientScenario link = new ClientScenario()) {
            PeerSocket node = link.openNode(ClientScenario.createNodeConfiguration());
            BeaconLinkProvider host = new BeaconLinkProvider(openClient(link, node, scr));
            LanRecord record = LanRecord.createBuilder(GAME_NAME, 1L).add("_name", GAME_NAME).add("_type", "2").add("map_name", "Bottleneck")
                                        .add("players_current", "1").add("players_max", "4").build();

            link.addPollAction(host::update);
            host.setGame(record, host.openGameSocket(GameProxySocket.GAME_PORT).getLocalPort());

            FoundRoom room = findRoom(link, openClient(link, node, scr));
            BeaconLinkClient clone = openClient(link, node, crossover);
            RoomSearch cloneSearch = clone.searchRooms();

            link.await(cloneSearch::isComplete, "the clone's search completes");

            Assertions.assertEquals(List.of(), cloneSearch.getRooms(), "the clone lists no room of the SC:R mod");

            ParticipantChannel channel = clone.joinRoom(room);

            link.advanceUntil(() -> channel.getState() != ChannelState.CONNECTING, CONNECT_MILLISECONDS, STEP_MILLISECONDS,
                              "the clone's join settles");

            Assertions.assertEquals(ChannelFailure.JOIN_REFUSED, channel.getFailure());
            Assertions.assertEquals(JoinRefusal.MOD_ID, channel.getJoinRefusal());
            Assertions.assertNull(host.getConnectedGame());
        }
    }
}
