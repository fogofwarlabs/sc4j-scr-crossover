package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.WeaponsDat;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

/**
 * Writes the unit and weapon values of SC:R Crossover into a game's own tables ({@link GameData#createGameCopy}), as changed DAT files would hold
 * them: the Terran Marine costs {@value #MARINE_MINERAL_COST} minerals and trains in {@value #MARINE_BUILD_TIME} frames, and its Gauss Rifle deals
 * {@value #GAUSS_RIFLE_DAMAGE} damage. Every other value stays the installation's.
 * <p>
 * {@link ScrCrossoverContentExtension} calls it once per game, after the game's tables were copied from the installation and before a Use Map
 * Settings map's unit settings apply, so such a map's own values still replace these as they replace installed ones.
 */
public final class ScrCrossoverValueUtils {
    /**
     * The Terran Marine's mineral cost in SC:R Crossover.
     */
    public static final int MARINE_MINERAL_COST = 45;
    /**
     * The Terran Marine's build time in SC:R Crossover, in game frames.
     */
    public static final int MARINE_BUILD_TIME = 300;
    /**
     * The base damage of the Gauss Rifle in SC:R Crossover.
     */
    public static final int GAUSS_RIFLE_DAMAGE = 7;

    /**
     * Writes the crossover's values into a game's own tables.
     *
     * @param game the game's own data, whose units and weapons tables are written
     */
    public static void applyValues(@NotNull GameData game) {
        DatTable units = game.getUnits();

        units.set(UnitsDat.MINERAL_COST, ScrUnitIds.TERRAN_MARINE, MARINE_MINERAL_COST);
        units.set(UnitsDat.BUILD_TIME, ScrUnitIds.TERRAN_MARINE, MARINE_BUILD_TIME);
        game.getWeapons().set(WeaponsDat.DAMAGE_AMOUNT, ScrWeaponIds.GAUSS_RIFLE, GAUSS_RIFLE_DAMAGE);
    }

    private ScrCrossoverValueUtils() {
    }
}
