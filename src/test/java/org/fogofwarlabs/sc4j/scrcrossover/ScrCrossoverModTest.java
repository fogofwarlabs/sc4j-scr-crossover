package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.log.Logging;
import org.fogofwarlabs.sc4j.engine.mod.identity.ModIdentity;
import org.fogofwarlabs.sc4j.manifest.ManifestException;
import org.fogofwarlabs.sc4j.manifest.ModManifestReader;
import org.fogofwarlabs.sc4j.manifest.install.InstallLayout;
import org.fogofwarlabs.sc4j.manifest.settings.value.SettingValuesFile;
import org.fogofwarlabs.sc4j.runtime.cli.ExitCode;
import org.fogofwarlabs.sc4j.runtime.session.RuntimeSession;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tests SC:R Crossover with the SC:R installation: the runtime loads its jar headless through the whole mod sequence (manifest, settings, the
 * installation check, registration) to the mod identity it logs, which is the clone's own and not the SC:R mod's; both mods use the one
 * saved StarCraft: Remastered folder of the install.
 */
@RequiresInstallation
final class ScrCrossoverModTest {
    private static final @NotNull String CROSSOVER_ID = "scr-crossover";
    private static final @NotNull String SCR_ID = "scr";
    private static final @NotNull String SHARED_GAME_FOLDER = "starcraft-remastered";

    /**
     * Runs the runtime headless with a mod jar of the install. The SC:R mod's client needs a window, so the run ends after the mod identity
     * with the runtime's refusal of a headless run.
     *
     * @return the runtime's log of the run
     */
    private static @NotNull String load(@NotNull InstallLayout install, @NotNull String modId, @NotNull Path jar) throws IOException, ManifestException {
        Path installed = install.getModsDirectory().resolve(modId + ".jar");

        Assertions.assertEquals(modId, ModManifestReader.read(jar).getManifest().getId());
        Files.copy(jar, installed);

        ExitCode exitCode = RuntimeSession.run(new String[]{"--install", install.getRoot().toString(), "--mod", installed.toString(), "--headless"});
        String log = Files.readString(install.getLogDirectory(modId).resolve(Logging.FILE_NAME), StandardCharsets.UTF_8);

        Assertions.assertEquals(ExitCode.MOD_FAILURE, exitCode, log);
        Assertions.assertTrue(log.contains("cannot run headless"), log);

        return log;
    }

    private static @NotNull InstallLayout createInstall(@NotNull Path root) throws IOException {
        InstallLayout install = InstallLayout.of(root);

        Files.createDirectories(install.getModsDirectory());
        new SettingValuesFile(install.getSharedSettingsFile()).write(Map.of(SHARED_GAME_FOLDER, ExternalInput.INSTALLATION.getPath().toString()));

        return install;
    }


    @Test
    void the_runtime_loads_the_crossover_headless_with_its_own_identity(@TempDir @NotNull Path directory) throws IOException, ManifestException {
        InstallLayout install = createInstall(directory);
        String crossoverLog = load(install, CROSSOVER_ID, ScrCrossoverTestResources.getModJar());
        String scrLog = load(install, SCR_ID, ScrCrossoverTestResources.getScrModJar());
        ModIdentity crossover = ScrCrossoverJarTest.createIdentity(ScrCrossoverTestResources.getModJar());
        ModIdentity scr = ScrCrossoverJarTest.createIdentity(ScrCrossoverTestResources.getScrModJar());

        String gameLine = "SC:R INSTALLATION: " + ExternalInput.INSTALLATION.getPath() + " (build";

        Assertions.assertTrue(crossoverLog.contains(gameLine), crossoverLog);
        Assertions.assertTrue(scrLog.contains(gameLine), scrLog);
        Assertions.assertTrue(crossoverLog.contains("IDENTITY: " + crossover.describe()), crossoverLog);
        Assertions.assertTrue(scrLog.contains("IDENTITY: " + scr.describe()), scrLog);
        Assertions.assertNotEquals(scr.describe(), crossover.describe());
        Assertions.assertTrue(Files.isDirectory(install.getStorageDirectory(CROSSOVER_ID)));
    }
}
