package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Registers the added Terran unit of SC:R Crossover, the {@value #NAME}: a unit type after the native ones that starts from the Terran Marine's
 * values, with its own name, cost, build time, hit points and size, its own graphics from the baked Unity-chan frames ({@link #ART_SET},
 * {@link ScrCrossoverUnitGraphics}: flingy entry, sprite, image, shadow image and script) and interface art (portrait, command icon and wireframe),
 * and these parts of the installation by reference: the Terran Ghost's weapons and the sound of their shot, the Terran Medic's voice (ready, what,
 * annoyed and yes sounds, the death sound), and the Terran Marine's requirement (a Barracks). The Barracks trains it with a button at position
 * {@value #TRAIN_BUTTON_POSITION}. Its size on the map is the Terran Goliath's ({@link #SIZE_TYPE}). The computer players do not build it: their
 * scripts know the native unit types only; they fight it, and use it when they own one, as a unit of its base type, the Marine.
 * <p>
 * {@link ScrCrossoverContentExtension} calls it once for the installed data ({@link GameData#load}), before the first game is made from it.
 */
public final class ScrCrossoverUnitUtils {
    /**
     * The unit's name.
     */
    public static final @NotNull String NAME = "Unity-chan";
    /**
     * The unit's hit points.
     */
    public static final int HIT_POINTS = 50;
    /**
     * The unit's mineral cost.
     */
    public static final int MINERAL_COST = 75;
    /**
     * The unit's vespene gas cost.
     */
    public static final int GAS_COST = 25;
    /**
     * The unit's build time, in game frames.
     */
    public static final int BUILD_TIME = 360;
    /**
     * The art set of the unit's graphics and interface art.
     */
    public static final @NotNull ScrCrossoverArtSet ART_SET = ScrCrossoverArtSet.UNITY_CHAN;
    /**
     * The native unit type whose size the unit has, read from the installation: its extents and placement box (collision and pathing), its
     * selection circle and its health bar. The selection circle lies the art set's ground offset below the sprite position (the Goliath's
     * distance), under the unit's feet. The unit keeps the Marine's size class (damage taken), only its size on the map is the Goliath's.
     */
    public static final @NotNull UnitType SIZE_TYPE = ScrUnits.TERRAN_GOLIATH;
    /**
     * The supply the unit takes, in half supply ({@code 2} is one supply).
     */
    public static final int SUPPLY_REQUIRED = 2;
    /**
     * The Barracks command card position of the unit's train button, free in the Barracks' own button set.
     */
    public static final int TRAIN_BUTTON_POSITION = 5;
    /**
     * The hotkey of the unit's train button.
     */
    public static final char TRAIN_BUTTON_HOTKEY = 'u';
    /**
     * The tooltip text of the unit's train button.
     */
    public static final @NotNull String TRAIN_BUTTON_TEXT = "Train " + NAME;
    /**
     * The native unit type whose weapons the unit uses.
     */
    public static final @NotNull UnitType WEAPON_TYPE = ScrUnits.TERRAN_GHOST;
    /**
     * The native unit type whose voice the unit uses.
     */
    public static final @NotNull UnitType VOICE_TYPE = ScrUnits.TERRAN_MEDIC;
    /**
     * The chances of the unit's idle portrait clips in percent, as the native Terran portraits have them.
     */
    public static final int @NotNull [] IDLE_CLIP_CHANCES = {85, 5, 5, 5};

    private static final int HIT_POINT_SCALE = 1 << 8;
    /**
     * The pixels the unit moves per game frame while it walks, as the Terran Marine's walk does.
     */
    private static final int WALK_STEP = 4;

    /**
     * Registers the unit and its graphics into the installed data.
     *
     * @param gameData the installed data, before its first game copy
     * @param files    the files of the crossover's jar ({@link ScrCrossoverUnitGraphics#createFiles})
     * @return the SC:R id of the added unit type
     * @throws IOException if the crossover's graphics or the installed scripts cannot be read
     */
    public static int registerUnit(@NotNull GameData gameData, @NotNull ScrModFiles files) throws IOException {
        DatTable units = gameData.getUnits();
        int weaponType = ScrUnits.getNativeId(WEAPON_TYPE);
        int voiceType = ScrUnits.getNativeId(VOICE_TYPE);
        int sizeType = ScrUnits.getNativeId(SIZE_TYPE);
        ScrCrossoverUnitGraphics.Attack groundAttack = ScrCrossoverUnitGraphics.Attack.ofWeaponType(gameData, WEAPON_TYPE, false);
        ScrCrossoverUnitGraphics.Attack airAttack = ScrCrossoverUnitGraphics.Attack.ofWeaponType(gameData, WEAPON_TYPE, true);
        ScrCrossoverUnitGraphics graphics = ScrCrossoverUnitGraphics.register(gameData, files, ART_SET, NAME, ScrUnits.TERRAN_MARINE,
                                                                              ScrUnits.TERRAN_MARINE, SIZE_TYPE, groundAttack, airAttack, VOICE_TYPE,
                                                                              WALK_STEP, ScrCrossoverUnitGraphics.BASE_TOP_SPEED, IDLE_CLIP_CHANCES);
        ScrAddedUnitType.Builder unit = new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, units)
                .setRequirementType(ScrUnits.TERRAN_MARINE)
                .setWireframe(graphics.getWireframe())
                .setCommandIcon(graphics.getCommandIcon())
                .setValue(UnitsDat.PORTRAIT, graphics.getPortrait())
                .setValue(UnitsDat.FLINGY_TYPE, graphics.getFlingy())
                .setValue(UnitsDat.HIT_POINTS, HIT_POINTS * HIT_POINT_SCALE)
                .setValue(UnitsDat.MINERAL_COST, MINERAL_COST)
                .setValue(UnitsDat.GAS_COST, GAS_COST)
                .setValue(UnitsDat.BUILD_TIME, BUILD_TIME)
                .setValue(UnitsDat.SUPPLY_REQUIRED, SUPPLY_REQUIRED)
                .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_BARRACKS, TRAIN_BUTTON_POSITION,
                                                                 TRAIN_BUTTON_HOTKEY, TRAIN_BUTTON_TEXT));

        for (String field : List.of(UnitsDat.GROUND_WEAPON, UnitsDat.MAXIMUM_GROUND_HITS, UnitsDat.AIR_WEAPON, UnitsDat.MAXIMUM_AIR_HITS)) {
            unit.setValue(field, units.get(field, weaponType));
        }

        for (String field : List.of(UnitsDat.DIMENSION_LEFT, UnitsDat.DIMENSION_UP, UnitsDat.DIMENSION_RIGHT, UnitsDat.DIMENSION_DOWN,
                                    UnitsDat.PLACEMENT_WIDTH, UnitsDat.PLACEMENT_HEIGHT)) {
            unit.setValue(field, units.get(field, sizeType));
        }

        for (String field : List.of(UnitsDat.READY_SOUND, UnitsDat.WHAT_SOUND_START, UnitsDat.WHAT_SOUND_END, UnitsDat.ANNOYED_SOUND_START,
                                    UnitsDat.ANNOYED_SOUND_END, UnitsDat.YES_SOUND_START, UnitsDat.YES_SOUND_END)) {
            unit.setValue(field, units.get(field, voiceType));
        }

        return gameData.getAddedUnitTypes().add(unit.build());
    }

    private ScrCrossoverUnitUtils() {
    }
}
