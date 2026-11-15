package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.log.Logging;
import org.fogofwarlabs.sc4j.manifest.ManifestException;
import org.fogofwarlabs.sc4j.manifest.ModManifest;
import org.fogofwarlabs.sc4j.manifest.ModManifestReader;
import org.fogofwarlabs.sc4j.manifest.install.InstallLayout;
import org.fogofwarlabs.sc4j.manifest.settings.InstallationRecords;
import org.fogofwarlabs.sc4j.manifest.settings.SettingDefinition;
import org.fogofwarlabs.sc4j.manifest.settings.value.SettingValueStore;
import org.fogofwarlabs.sc4j.manifest.settings.value.SettingValuesException;
import org.fogofwarlabs.sc4j.manifest.settings.value.SettingValuesFile;
import org.fogofwarlabs.sc4j.manifest.test.FakeInstallationRecords;
import org.fogofwarlabs.sc4j.manifest.validation.ProblemCode;
import org.fogofwarlabs.sc4j.manifest.validation.ValidationProblem;
import org.fogofwarlabs.sc4j.manifest.validation.ValidationResult;
import org.fogofwarlabs.sc4j.manifest.version.EngineVersion;
import org.fogofwarlabs.sc4j.runtime.cli.ExitCode;
import org.fogofwarlabs.sc4j.runtime.session.RuntimeSession;
import org.fogofwarlabs.sc4j.scr.ScrMod;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests an install whose {@code mods/} folder holds the SC:R mod jar and the crossover's jar: both manifests are read with their own ids and
 * names and their own settings files (the text language), and both use the one shared StarCraft: Remastered folder of the install, which
 * the first resolve finds where the computer records the game and which a save of either mod changes for both. The runtime starts each mod
 * with its own settings file, storage folder and log folder and hands it the shared folder.
 */
final class ScrCrossoverInstallTest {
    private static final @NotNull String SCR_JAR = "scr.jar";
    private static final @NotNull String CROSSOVER_JAR = "scr-crossover.jar";
    private static final @NotNull String SCR_ID = "scr";
    private static final @NotNull String CROSSOVER_ID = "scr-crossover";
    private static final @NotNull String SHARED_GAME_FOLDER = "starcraft-remastered";
    private static final @NotNull String UNINSTALL_ENTRY = "StarCraft";
    private static final @NotNull String LOCALE_KEY = "locale";
    private static final @NotNull String ENGLISH_LOCALE = "enUS";
    private static final @NotNull String KOREAN_LOCALE = "koKR";
    private static final @NotNull String NAME_LANGUAGE = "en";
    private static final @NotNull List<@NotNull String> INSTALLATION_FILES = List.of(".build.info", "x86_64/StarCraft.exe");

    private static @NotNull Path createInstallation(@NotNull Path folder) throws IOException {
        for (String file : INSTALLATION_FILES) {
            Path path = folder.resolve(file);

            Files.createDirectories(path.getParent());
            Files.createFile(path);
        }

        return folder.toAbsolutePath().normalize();
    }

    private static @NotNull ModManifest readManifest(@NotNull Path jar) throws ManifestException {
        ModManifest manifest = ModManifestReader.read(jar).getManifest();
        ValidationResult engine = manifest.checkEngine(EngineVersion.CURRENT);

        Assertions.assertTrue(engine.isValid(), engine::describe);

        SettingDefinition folder = manifest.getSettings().findDefinition(ScrMod.GAME_FOLDER_SETTING);

        Assertions.assertNotNull(folder, () -> manifest.getId() + " has no setting " + ScrMod.GAME_FOLDER_SETTING);
        Assertions.assertEquals(SHARED_GAME_FOLDER, folder.getSharedName(), manifest::getId);

        return manifest;
    }

    private static @NotNull Map<@NotNull String, @NotNull Object> readSharedValues(@NotNull InstallLayout install) throws IOException,
                                                                                                                          SettingValuesException {
        return new SettingValuesFile(install.getSharedSettingsFile()).read();
    }

    private static void assertMissingInstallation(@NotNull ModManifest manifest, @NotNull Map<@NotNull String, @NotNull Object> values) {
        List<ValidationProblem> problems = manifest.getSettings().validate(values).getProblems();

        Assertions.assertEquals(1, problems.size(), () -> manifest.getId() + ": " + problems);
        Assertions.assertEquals(ProblemCode.VALUE_REQUIRED, problems.getFirst().getCode());
        Assertions.assertEquals(ScrMod.GAME_FOLDER_SETTING, problems.getFirst().getSubject());
    }

    private static void assertReady(@NotNull ModManifest manifest, @NotNull Map<@NotNull String, @NotNull Object> values,
                                    @NotNull Path folder) {
        ValidationResult result = manifest.getSettings().validate(values);

        Assertions.assertTrue(result.isValid(), result::describe);
        Assertions.assertEquals(folder.toString(), values.get(ScrMod.GAME_FOLDER_SETTING), manifest::getId);
    }

    // The test installation's empty build info makes the mod refuse the run in initialize, after settings, storage and log are set up.
    private static void assertRuntimeUses(@NotNull InstallLayout install, @NotNull String modId, @NotNull Path jar,
                                          @NotNull Path folder) throws IOException {
        ExitCode exitCode = RuntimeSession.run(new String[]{"--install", install.getRoot().toString(), "--mod", jar.toString(), "--headless"});
        String log = Files.readString(install.getLogDirectory(modId).resolve(Logging.FILE_NAME), StandardCharsets.UTF_8);

        Assertions.assertEquals(ExitCode.MOD_FAILURE, exitCode, log);
        Assertions.assertTrue(log.contains("SETTINGS: " + install.getSettingsFile(modId) + ", shared " + install.getSharedSettingsFile()), log);
        Assertions.assertTrue(log.contains("STORAGE: " + install.getStorageDirectory(modId)), log);
        Assertions.assertTrue(log.contains("The StarCraft: Remastered installation " + folder + " has no readable"), log);
        Assertions.assertTrue(Files.isDirectory(install.getStorageDirectory(modId)));
    }


    @Test
    void both_mods_use_one_game_folder_and_separate_settings(@TempDir @NotNull Path directory) throws IOException, ManifestException,
                                                                                                      SettingValuesException {
        InstallLayout install = InstallLayout.of(directory.resolve("install"));
        Path recorded = createInstallation(directory.resolve("recorded"));
        Path moved = createInstallation(directory.resolve("moved"));
        InstallationRecords records = new FakeInstallationRecords(Map.of(UNINSTALL_ENTRY, List.of(recorded)), List.of());
        Path scrJar = install.getModsDirectory().resolve(SCR_JAR);
        Path crossoverJar = install.getModsDirectory().resolve(CROSSOVER_JAR);

        Files.createDirectories(install.getModsDirectory());
        Files.copy(ScrCrossoverTestResources.getScrModJar(), scrJar);
        Files.copy(ScrCrossoverTestResources.getModJar(), crossoverJar);

        ModManifest scrManifest = readManifest(scrJar);
        ModManifest crossoverManifest = readManifest(crossoverJar);

        Assertions.assertEquals(SCR_ID, scrManifest.getId());
        Assertions.assertEquals(CROSSOVER_ID, crossoverManifest.getId());
        Assertions.assertNotEquals(scrManifest.getName().get(NAME_LANGUAGE), crossoverManifest.getName().get(NAME_LANGUAGE));

        SettingValueStore scrStore = SettingValueStore.of(install, scrManifest);
        SettingValueStore crossoverStore = SettingValueStore.of(install, crossoverManifest);

        // Nothing recorded and nothing saved: both mods miss the game folder.
        assertMissingInstallation(scrManifest, scrStore.resolve(InstallationRecords.NONE));
        assertMissingInstallation(crossoverManifest, crossoverStore.resolve(InstallationRecords.NONE));
        Assertions.assertFalse(Files.exists(install.getSharedSettingsFile()));

        // The first resolve with the game recorded finds and saves its folder once, for both mods.
        assertReady(scrManifest, scrStore.resolve(records), recorded);
        assertReady(crossoverManifest, crossoverStore.resolve(InstallationRecords.NONE), recorded);
        Assertions.assertEquals(Map.of(SHARED_GAME_FOLDER, recorded.toString()), readSharedValues(install));

        // A folder saved by one mod is the folder of both; each mod keeps its own language.
        scrStore.write(Map.of(ScrMod.GAME_FOLDER_SETTING, moved.toString(), LOCALE_KEY, ENGLISH_LOCALE));

        Map<String, Object> crossoverValues = new HashMap<>(crossoverStore.read());

        Assertions.assertEquals(moved.toString(), crossoverValues.get(ScrMod.GAME_FOLDER_SETTING));
        crossoverValues.put(LOCALE_KEY, KOREAN_LOCALE);
        crossoverStore.write(crossoverValues);

        Assertions.assertEquals(Map.of(SHARED_GAME_FOLDER, moved.toString()), readSharedValues(install));
        Assertions.assertEquals(Map.of(LOCALE_KEY, ENGLISH_LOCALE), new SettingValuesFile(install.getSettingsFile(SCR_ID)).read());
        Assertions.assertEquals(Map.of(LOCALE_KEY, KOREAN_LOCALE), new SettingValuesFile(install.getSettingsFile(CROSSOVER_ID)).read());

        // A saved folder wins over the records.
        assertReady(scrManifest, scrStore.resolve(records), moved);
        assertReady(crossoverManifest, crossoverStore.resolve(records), moved);
        Assertions.assertEquals(Map.of(SHARED_GAME_FOLDER, moved.toString()), readSharedValues(install));

        // Each mod starts with its own settings file, storage folder and log folder and gets the shared folder.
        assertRuntimeUses(install, SCR_ID, scrJar, moved);
        assertRuntimeUses(install, CROSSOVER_ID, crossoverJar, moved);
    }
}
