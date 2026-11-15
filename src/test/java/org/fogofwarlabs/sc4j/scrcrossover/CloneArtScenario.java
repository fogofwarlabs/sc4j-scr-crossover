package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;

/**
 * Test code that runs inside the clone of SC:R Crossover ({@link CloneRunScenario}) and checks the SD frames of every art set, registered as a unit
 * or not: the crossover's art file reader of the jar makes them from the installation's palettes.
 */
public final class CloneArtScenario {
    /**
     * The art sets of the crossover jar, the folders under {@code third-party/}.
     */
    private static final @NotNull List<@NotNull String> ART_SETS = List.of("unity-chan", "myriarch");
    /**
     * The crossover's art file reader in the jar; the tests compile against the SC:R mod's API only.
     */
    private static final @NotNull String ART_FILES_CLASS = "org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverArtFiles";
    private static final int TILESET_COUNT = 8;
    private static final int DIRECTIONS = 17;
    private static final int MAXIMUM_GRP_SIZE = 255;
    private static final int FIRST_TEAM_INDEX = 8;
    private static final int LAST_TEAM_INDEX = 15;
    private static final int FIRST_UNIT_INDEX = 16;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Checks that the SD frames of every art set are made from the installation's palettes: one canvas of the set's canvas size (at most the
     * GRP limit) per frame of its animations, the unit's frames with team color indices {@code 8..15} and shared unit colors only, and a shadow
     * in its shadow's frames.
     *
     * @param installation the SC:R installation
     * @throws Exception if the installation or the crossover's graphics cannot be read
     */
    public static void checkSdFrames(@NotNull Path installation) throws Exception {
        try (GameData gameData = GameData.loadFrom(installation)) {
            List<byte[]> palettes = new ArrayList<>();

            for (int tileset = 0; tileset < TILESET_COUNT; tileset++) {
                palettes.add(gameData.loadTerrainPalette(tileset));
            }

            ScrModFiles files = createArtFiles(palettes);

            for (String set : ART_SETS) {
                String graphics = "third-party/" + set + "/graphics/";
                int frameCount = readFrameSetCount(files.readFile(graphics + "animations.json")) * DIRECTIONS;
                GrpFile main = GrpFile.parse(files.readFile(graphics + "main.grp"));
                GrpFile shadow = GrpFile.parse(files.readFile(graphics + "shadow.grp"));
                Set<Integer> indices = new TreeSet<>();
                int shadowPixels = 0;

                Assertions.assertEquals(frameCount, main.getFrameCount(), set);
                Assertions.assertEquals(frameCount, shadow.getFrameCount(), set);
                Assertions.assertTrue(main.getWidth() <= MAXIMUM_GRP_SIZE && main.getWidth() == main.getHeight(), set);
                Assertions.assertEquals(main.getWidth(), shadow.getWidth(), set);

                for (int frame = 0; frame < frameCount; frame++) {
                    for (byte pixel : main.decodeFrame(frame).getPixels()) {
                        indices.add(pixel & BYTE_MASK);
                    }

                    for (byte pixel : shadow.decodeFrame(frame).getPixels()) {
                        shadowPixels += pixel == 0 ? 0 : 1;
                    }
                }

                Assertions.assertTrue(indices.stream().anyMatch(index -> index >= FIRST_TEAM_INDEX && index <= LAST_TEAM_INDEX), set + " " + indices);
                Assertions.assertTrue(indices.stream().allMatch(index -> index == 0 || index >= FIRST_TEAM_INDEX), set + " " + indices);
                Assertions.assertTrue(indices.stream().anyMatch(index -> index >= FIRST_UNIT_INDEX), set + " " + indices);
                Assertions.assertTrue(shadowPixels > 0, set);
            }
        }
    }

    /**
     * Creates the crossover's art file reader over the jar's files.
     */
    private static @NotNull ScrModFiles createArtFiles(@NotNull List<byte @NotNull []> palettes) throws ReflectiveOperationException {
        ClassLoader loader = CloneArtScenario.class.getClassLoader();

        try {
            return (ScrModFiles) Class.forName(ART_FILES_CLASS, true, loader).getConstructor(ScrModFiles.class, List.class)
                                      .newInstance(ScrModFiles.of(loader), palettes);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }

            throw exception;
        }
    }

    private static int readFrameSetCount(byte @NotNull [] animations) throws IOException {
        Map<?, ?> root = Assertions.assertInstanceOf(Map.class, JsonReader.parse(animations));
        int frameSets = 0;

        for (Object animation : (List<?>) root.get("animations")) {
            frameSets += ((Long) ((Map<?, ?>) animation).get("frameCount")).intValue();
        }

        return frameSets;
    }

    private CloneArtScenario() {
    }
}
