package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Extends the game data of SC:R Crossover: the added units, Unity-chan ({@link ScrCrossoverUnitUtils}) and the Myriarch
 * ({@link ScrCrossoverMyriarchUtils}), join the installed data, and the crossover's values ({@link ScrCrossoverValueUtils}) are written into every
 * game's own tables. The crossover's jar names it in {@code META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension}.
 */
public final class ScrCrossoverContentExtension implements ScrContentExtension {
    /**
     * Creates a {@link ScrCrossoverContentExtension} instance.
     */
    public ScrCrossoverContentExtension() {
    }

    /**
     * Extends the installed data right after it is loaded, before any game is made from it: unit types, graphics and interface art added
     * through {@link GameData#getAddedUnitTypes()} and {@link GameData#getAddedGraphics()} belong here, so every game, replay and screen of
     * the session sees them.
     *
     * @param installed the installed data
     * @throws IOException if a file the extension reads cannot be read
     */
    @Override
    public void extendInstalledData(@NotNull GameData installed) throws IOException {
        ScrModFiles files = ScrCrossoverUnitGraphics.createFiles(installed);

        // Unity-chan first: replays and commands hold the added ids, 233 Unity-chan and 234 the Myriarch.
        ScrCrossoverUnitUtils.registerUnit(installed, files);
        ScrCrossoverMyriarchUtils.registerUnit(installed, files);
    }

    /**
     * Extends a game's own tables right after the game is created over its copy of the installed tables, before a Use Map Settings map
     * writes its unit, upgrade and technology settings, so such a map's own values still replace the values written here. Every game, live
     * or replay, passes here.
     *
     * @param game the game's own data
     */
    @Override
    public void extendGameData(@NotNull GameData game) {
        ScrCrossoverValueUtils.applyValues(game);
    }
}
