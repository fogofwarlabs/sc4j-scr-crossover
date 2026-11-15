package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.core.test.TestResultUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the SD frames of every art set of SC:R Crossover are made from the installation's palettes ({@link CloneArtScenario}), and that the
 * added units' graphics (Unity-chan, and the Myriarch beside a Hive) draw in the SD renderer (frames the clone quantizes to the installation's
 * palettes), in the HD renderer and in the HD renderer with lighting, with their team color, and that the Myriarch of a live game walks and dies
 * with its shadow, inside the clone ({@link CloneRenderScenario}); the sample sheets go into the result directory of the test task.
 */
@RequiresInstallation
final class ScrCrossoverArtFilesTest {
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";

    @Test
    void the_sd_frames_of_every_art_set_are_made_from_the_installed_palettes() throws Exception {
        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneArtScenario.class.getName(), "checkSdFrames", ExternalInput.INSTALLATION.getPath());
        }
    }

    @Test
    void the_unit_draws_with_its_team_color_in_sd_hd_and_lit_hd() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneRenderScenario.class.getName(), "drawSamples", ExternalInput.INSTALLATION.getPath(),
                       TestResultUtils.getResultPath("unitychan-samples"));
        }
    }

    @Test
    void the_myriarch_draws_in_every_direction_beside_the_ultralisk() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneRenderScenario.class.getName(), "drawDirections", ExternalInput.INSTALLATION.getPath(),
                       TestResultUtils.getResultPath("myriarch-directions"));
        }
    }

    @Test
    void the_myriarch_draws_with_its_team_color_beside_a_hive_in_sd_hd_and_lit_hd() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneRenderScenario.class.getName(), "drawMyriarchSamples", ExternalInput.INSTALLATION.getPath(),
                       TestResultUtils.getResultPath("myriarch-samples"));
        }
    }

    @Test
    void the_myriarch_walks_and_dies_with_its_shadow_in_a_live_game() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        try (CloneRunScenario clone = new CloneRunScenario()) {
            clone.call(CloneRenderScenario.class.getName(), "drawWalk", ExternalInput.INSTALLATION.getPath(),
                       TestResultUtils.getResultPath("myriarch-walk"));
        }
    }
}
