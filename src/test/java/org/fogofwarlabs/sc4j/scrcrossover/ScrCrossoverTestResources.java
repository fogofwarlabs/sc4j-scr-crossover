package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Names the build outputs and the overlay of SC:R Crossover that its tests use.
 */
public final class ScrCrossoverTestResources {
    /**
     * The system property, set by the crossover's build for every test task, that names the built mod jar {@code scr-crossover.jar}.
     */
    public static final @NotNull String MOD_JAR_PROPERTY = "sc4j.scrcrossover.jar";

    /**
     * The system property, set by the crossover's build for every test task, that names the built SC:R mod jar {@code scr.jar}.
     */
    public static final @NotNull String SCR_MOD_JAR_PROPERTY = "sc4j.scr.jar";

    /**
     * The system property, set by the crossover's build for every test task, that names the overlay folder {@code overlay}.
     */
    public static final @NotNull String OVERLAY_PROPERTY = "sc4j.scrcrossover.overlay";

    /**
     * The system property, set by the crossover's build for every test task, that names the folder of the art sets {@code third-party}.
     */
    public static final @NotNull String ART_FOLDER_PROPERTY = "sc4j.scrcrossover.art";

    /**
     * @return the built mod jar of SC:R Crossover
     * @throws IllegalStateException if the build did not name the jar
     */
    public static @NotNull Path getModJar() {
        return getPath(MOD_JAR_PROPERTY);
    }

    /**
     * @return the built mod jar of the SC:R mod
     * @throws IllegalStateException if the build did not name the jar
     */
    public static @NotNull Path getScrModJar() {
        return getPath(SCR_MOD_JAR_PROPERTY);
    }

    /**
     * @return the overlay folder of SC:R Crossover
     * @throws IllegalStateException if the build did not name the folder
     */
    public static @NotNull Path getOverlay() {
        return getPath(OVERLAY_PROPERTY);
    }

    /**
     * @return the folder of the art sets of SC:R Crossover, one folder per set holding the baked frames, their {@code metadata.json}, the NOTICE and
     * the license files
     * @throws IllegalStateException if the build did not name the folder
     */
    public static @NotNull Path getArtFolder() {
        return getPath(ART_FOLDER_PROPERTY);
    }

    private static @NotNull Path getPath(@NotNull String property) {
        String path = System.getProperty(property, "");

        if (path.isBlank()) {
            throw Exceptions.newIllegalStateException("The SC:R Crossover tests need the system property {0} of the crossover's build.", property);
        }

        return Path.of(path);
    }

    private ScrCrossoverTestResources() {
    }
}
