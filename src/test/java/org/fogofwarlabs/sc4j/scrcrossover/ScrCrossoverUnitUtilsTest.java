package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the added Terran unit of SC:R Crossover with the SC:R installation, inside the clone ({@link CloneRunScenario}, {@link CloneGameScenario}):
 * its registration with its values, references and graphics; its training at the Barracks from a written replay, whose playback is the same in two
 * runs of the clone; its walk, fight and death through its own animations; and a game against the computer player with the unit on both sides,
 * the same in two runs of the clone, also in a long game with the Marine's upgrades and Medics on both sides.
 */
@RequiresInstallation
final class ScrCrossoverUnitUtilsTest {
    private static final @NotNull String SCENARIO = CloneGameScenario.class.getName();

    @Test
    void the_unit_joins_the_installed_data_with_its_values_references_and_graphics() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(SCENARIO, "checkRegistration", ExternalInput.INSTALLATION.getPath());
        }
    }

    @Test
    void the_barracks_trains_the_unit_in_a_written_replay_that_plays_the_same_in_two_clone_runs() throws Exception {
        long[] first;
        long[] second;

        try (CloneRunScenario clone = new CloneRunScenario()) {
            first = (long[]) clone.call(SCENARIO, "playTrainingReplay", ExternalInput.INSTALLATION.getPath());
        }

        try (CloneRunScenario clone = new CloneRunScenario()) {
            second = (long[]) clone.call(SCENARIO, "playTrainingReplay", ExternalInput.INSTALLATION.getPath());
        }

        Assertions.assertArrayEquals(first, second, "Two runs of the clone play the written replay with equal state hashes.");
    }

    @Test
    void the_unit_walks_fights_and_dies_through_its_own_animations() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(SCENARIO, "fight", ExternalInput.INSTALLATION.getPath());
        }
    }

    @Test
    void the_computer_player_fights_the_unit_on_both_sides_the_same_in_two_clone_runs() throws Exception {
        long[] first;
        long[] second;

        try (CloneRunScenario clone = new CloneRunScenario()) {
            first = (long[]) clone.call(SCENARIO, "playComputerGame", ExternalInput.INSTALLATION.getPath());
        }

        try (CloneRunScenario clone = new CloneRunScenario()) {
            second = (long[]) clone.call(SCENARIO, "playComputerGame", ExternalInput.INSTALLATION.getPath());
        }

        Assertions.assertArrayEquals(first, second, "Two runs of the clone play the computer game with equal state hashes.");
    }

    @Test
    void a_long_computer_game_with_the_unit_on_both_sides_plays_the_same_in_two_clone_runs() throws Exception {
        long[] first;
        long[] second;

        try (CloneRunScenario clone = new CloneRunScenario()) {
            first = (long[]) clone.call(SCENARIO, "playLongComputerGame", ExternalInput.INSTALLATION.getPath());
        }

        try (CloneRunScenario clone = new CloneRunScenario()) {
            second = (long[]) clone.call(SCENARIO, "playLongComputerGame", ExternalInput.INSTALLATION.getPath());
        }

        Assertions.assertArrayEquals(first, second, "Two runs of the clone play the long computer game with equal state hashes.");
    }

    @Test
    void the_unit_follows_every_command_while_it_walks_and_fights() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(SCENARIO, "command", ExternalInput.INSTALLATION.getPath());
        }
    }
}
