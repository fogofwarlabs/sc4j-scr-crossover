package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Tests the Myriarch of SC:R Crossover with the SC:R installation, inside the clone ({@link CloneRunScenario}): a Hive morphs into it in a live game
 * of the clone, takes off and then flies over terrain no ground unit stands on, attack-moves and breathes fire on ground and air units with its own
 * splashing weapon ({@link CloneMorphScenario}), and both clients of a LAN game of the clone stay in sync while their Hives morph into it and it
 * flies ({@code CloneLanMorphScenario}).
 */
@RequiresInstallation
final class ScrCrossoverMyriarchTest {
    private static final @NotNull String LAN_SCENARIO = "org.fogofwarlabs.sc4j.scr.net.lan.CloneLanMorphScenario";

    @Test
    void the_hive_morphs_into_the_myriarch_and_leaves_its_base_behind() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneMorphScenario.class.getName(), "morphHive", ExternalInput.INSTALLATION.getPath());
        }
    }

    @Test
    void the_myriarch_flies_over_cliffs_and_breathes_fire_on_ground_and_air_units_after_the_morph() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneMorphScenario.class.getName(), "flyAndFight", ExternalInput.INSTALLATION.getPath());
        }
    }

    @Test
    void both_clients_of_a_lan_game_stay_in_sync_when_their_hives_morph_into_the_myriarch() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(LAN_SCENARIO, "playMorph", ExternalInput.INSTALLATION.getPath());
        }
    }
}
