package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.engine.content.type.WeaponType;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedWeapon;
import org.fogofwarlabs.sc4j.scr.content.ScrDamageKinds;
import org.fogofwarlabs.sc4j.scr.content.ScrRequirementScript;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.content.ScrWeapons;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.WeaponsDat;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponTargetFlags;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers the giant flying Zerg unit of SC:R Crossover, the {@value #NAME}: a unit type after Unity-chan that starts from the Zerg Guardian's
 * values (a large flyer with the air units' elevation, the Guardian's voice and the Zerg flyer upgrades), into which a completed Hive morphs (a
 * building morph whose result is a unit, button at position {@value #MORPH_BUTTON_POSITION} of the Hive), with its own requirement script (a Hive
 * that is not busy, a completed Ultralisk Cavern and a completed Defiler Mound), cost, morph time, hit points, armour, supply, sight and size, its
 * own graphics and interface art from the baked dragon frames ({@link #ART_SET}, {@link ScrCrossoverUnitGraphics}), the Guardian's flight (top speed,
 * acceleration, halt distance, turn rate) and the Hive's selection circle and health bar width.
 * <p>
 * Its weapon breathes fire at ground and air units, as the native Firebat's Flame Thrower does on the ground: the attack creates the fire breath,
 * an overlay image of the project's own frames ({@link ScrCrossoverArtSet#MYRIARCH_FIRE}, with the sound of the Firebat's flame), at the mouth by the
 * unit image's attack overlay offsets ({@code imgoluselo}), and launches {@value #BREATH_HITS} invisible projectiles of its own weapon
 * ({@link #registerWeapon}) at the distances {@link #BREATH_DISTANCES} in front of it ({@code attkshiftproj}), each splashing the enemies around
 * it, as the Firebat's three shifted projectiles do.
 * <p>
 * When the morph completes the Hive becomes the unit and takes off: its Larvae lose their parent, its creep recedes, its supply, depot and Hive
 * count are gone (the SC:R mod's building morph into an added unit type). The computer players do not build it; they fight it as a Guardian, its
 * base type. {@link ScrCrossoverContentExtension} calls it once for the installed data, after Unity-chan.
 */
public final class ScrCrossoverMyriarchUtils {
    /**
     * The unit's name.
     */
    public static final @NotNull String NAME = "Myriarch";
    /**
     * The art set of the unit's graphics and interface art.
     */
    public static final @NotNull ScrCrossoverArtSet ART_SET = ScrCrossoverArtSet.MYRIARCH;
    /**
     * The art set of the fire breath, the overlay image of the unit's attack.
     */
    public static final @NotNull ScrCrossoverArtSet FIRE_ART_SET = ScrCrossoverArtSet.MYRIARCH_FIRE;
    /**
     * The unit's hit points; after the morph the unit keeps the Hive's damage.
     */
    public static final int HIT_POINTS = 2000;
    /**
     * The unit's armour.
     */
    public static final int ARMOR = 3;
    /**
     * The morph's mineral cost, charged when it starts.
     */
    public static final int MINERAL_COST = 400;
    /**
     * The morph's vespene gas cost.
     */
    public static final int GAS_COST = 400;
    /**
     * The morph time, in game frames (about 100 seconds at the fastest game speed).
     */
    public static final int BUILD_TIME = 2400;
    /**
     * The supply the unit takes, in half supply ({@code 16} is eight supply).
     */
    public static final int SUPPLY_REQUIRED = 16;
    /**
     * The unit's sight range, in tiles.
     */
    public static final int SIGHT_RANGE = 10;
    /**
     * The transport space the unit takes: more than any transport holds.
     */
    public static final int SPACE_REQUIRED = 255;
    /**
     * The unit's extents (collision and placement), left, up, right and down from its position, in pixels.
     */
    public static final int @NotNull [] DIMENSIONS = {30, 25, 29, 24};
    /**
     * The unit's placement box, width and height in pixels.
     */
    public static final int @NotNull [] PLACEMENT = {64, 64};
    /**
     * The Hive command card position of the morph button, free in the Hive's own button set.
     */
    public static final int MORPH_BUTTON_POSITION = 9;
    /**
     * The hotkey of the morph button.
     */
    public static final char MORPH_BUTTON_HOTKEY = 'm';
    /**
     * The tooltip text of the morph button.
     */
    public static final @NotNull String MORPH_BUTTON_TEXT = "Mutate into " + NAME;
    /**
     * The tooltip text of the disabled morph button: the buildings the requirement script asks for besides the Hive, in the words of the installed
     * disabled strings.
     */
    public static final @NotNull String MORPH_BUTTON_DISABLED_TEXT = NAME + " Requires:\n   Ultralisk Cavern\n   Defiler Mound";
    /**
     * The client locale of the Korean texts of the morph button.
     */
    public static final @NotNull String KOREAN_LOCALE = "koKR";
    /**
     * The Korean tooltip text of the morph button, in the words of the installed Korean strings.
     */
    public static final @NotNull String KOREAN_MORPH_BUTTON_TEXT = "미리아크로 변이";
    /**
     * The Korean tooltip text of the disabled morph button, in the words of the installed Korean disabled strings.
     */
    public static final @NotNull String KOREAN_MORPH_BUTTON_DISABLED_TEXT = "미리아크 요구 사항:\n   울트라리스크 캐번\n   디파일러 마운드";
    /**
     * The native unit type whose values the unit starts from: a large Zerg flyer.
     */
    public static final @NotNull UnitType BASE_TYPE = ScrUnits.ZERG_GUARDIAN;
    /**
     * The native unit type whose flingy entry (top speed, acceleration, halt distance, turn rate) the unit's starts from: a large air unit's
     * flight, which its flap does not tie to a speed.
     */
    public static final @NotNull UnitType MOVEMENT_TYPE = ScrUnits.ZERG_GUARDIAN;
    /**
     * The native unit type whose selection circle and health bar width the unit takes.
     */
    public static final @NotNull UnitType CIRCLE_TYPE = ScrUnits.ZERG_HIVE;
    /**
     * The native unit type whose flame overlay's values and sound the fire breath starts from.
     */
    public static final @NotNull UnitType FLAME_TYPE = ScrUnits.TERRAN_FIREBAT;
    /**
     * The native weapon the fire breath's weapon starts from: the Flame Thrower (an invisible projectile that appears at the attacker and
     * splashes the enemies around it).
     */
    public static final @NotNull WeaponType BASE_WEAPON = ScrWeapons.FLAME_THROWER;
    /**
     * The chances of the unit's idle portrait clips in percent, as the native portraits have them.
     */
    public static final int @NotNull [] IDLE_CLIP_CHANCES = {85, 5, 5, 5};
    /**
     * The number of projectiles of one breath.
     */
    public static final int BREATH_HITS = 3;
    /**
     * The distances in front of the unit, in pixels, at which the breath's projectiles appear ({@code attkshiftproj}): along the flame, as the
     * Firebat's {@code 24, 52, 80} lie along its flame.
     */
    public static final int @NotNull [] BREATH_DISTANCES = {32, 72, 112};
    /**
     * The fire breath weapon's maximum range in pixels: three tiles from the unit's edge.
     */
    public static final int WEAPON_RANGE = 96;
    /**
     * The damage of one breath projectile.
     */
    public static final int WEAPON_DAMAGE = 12;
    /**
     * The damage each weapon upgrade adds to one breath projectile.
     */
    public static final int WEAPON_DAMAGE_BONUS = 1;
    /**
     * The cooldown between two breaths, in game frames.
     */
    public static final int WEAPON_COOLDOWN = 30;
    /**
     * The inner, medium and outer splash radii of a breath projectile: the Firebat's {@code 15, 20, 25} scaled to the giant's flame.
     */
    public static final int @NotNull [] WEAPON_SPLASH_RADII = {24, 32, 40};

    private static final int HIT_POINT_SCALE = 1 << 8;
    /**
     * The elements of {@link #DIMENSIONS}.
     */
    private static final int LEFT_INDEX = 0;
    private static final int UP_INDEX = 1;
    private static final int RIGHT_INDEX = 2;
    private static final int DOWN_INDEX = 3;
    /**
     * The elements of {@link #PLACEMENT}.
     */
    private static final int WIDTH_INDEX = 0;
    private static final int HEIGHT_INDEX = 1;
    /**
     * The elements of {@link #WEAPON_SPLASH_RADII}.
     */
    private static final int INNER_INDEX = 0;
    private static final int MEDIUM_INDEX = 1;
    private static final int OUTER_INDEX = 2;
    /**
     * The walk moves the unit by its flingy entry, not by its script.
     */
    private static final int FLINGY_WALK = 0;
    /**
     * The position of the attack overlay table where the breath starts.
     */
    private static final int BREATH_OVERLAY_POSITION = 0;
    /**
     * An instruction built here has no place in an installed script.
     */
    private static final int NO_OFFSET = 0;

    /**
     * Registers the unit, its fire breath and its graphics into the installed data.
     *
     * @param gameData the installed data, before its first game copy, after Unity-chan
     * @param files    the files of the crossover's jar ({@link ScrCrossoverUnitGraphics#createFiles})
     * @return the SC:R id of the added unit type
     * @throws IOException if the crossover's graphics or the installed scripts cannot be read
     */
    public static int registerUnit(@NotNull GameData gameData, @NotNull ScrModFiles files) throws IOException {
        DatTable units = gameData.getUnits();
        int weapon = registerWeapon(gameData);
        int fire = ScrCrossoverUnitGraphics.registerOverlay(gameData, files, FIRE_ART_SET, NAME + " fire",
                                                            ScrCrossoverUnitGraphics.findAttackOverlayImage(gameData, FLAME_TYPE));
        List<IscriptInstruction> breath = new ArrayList<>();

        breath.add(new IscriptInstruction(NO_OFFSET, IscriptOpcodes.IMGOLUSELO,
                                          new int[]{fire, ScrImageTypeView.OVERLAY_ATTACK, BREATH_OVERLAY_POSITION}));

        for (int distance : BREATH_DISTANCES) {
            breath.add(new IscriptInstruction(NO_OFFSET, IscriptOpcodes.ATTKSHIFTPROJ, new int[]{distance}));
        }

        ScrCrossoverUnitGraphics.Attack attack = ScrCrossoverUnitGraphics.Attack.of(breath);
        ScrCrossoverUnitGraphics graphics = ScrCrossoverUnitGraphics.register(gameData, files, ART_SET, NAME, BASE_TYPE, MOVEMENT_TYPE, CIRCLE_TYPE,
                                                                              attack, attack, BASE_TYPE, FLINGY_WALK,
                                                                              ScrCrossoverUnitGraphics.BASE_TOP_SPEED, IDLE_CLIP_CHANCES);
        ScrAddedUnitType.TrainButton.Text text = new ScrAddedUnitType.TrainButton.Text(MORPH_BUTTON_TEXT, MORPH_BUTTON_DISABLED_TEXT);
        ScrAddedUnitType.TrainButton.Text koreanText = new ScrAddedUnitType.TrainButton.Text(KOREAN_MORPH_BUTTON_TEXT,
                                                                                             KOREAN_MORPH_BUTTON_DISABLED_TEXT);
        ScrAddedUnitType.TrainButton button = new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HIVE, MORPH_BUTTON_POSITION,
                                                                               ScrAddedUnitType.ProductionAction.BUILDING_MORPH, MORPH_BUTTON_HOTKEY,
                                                                               text, Map.of(KOREAN_LOCALE, koreanText));
        ScrRequirementScript requirements = new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HIVE)
                                                                              .requireNotBusy()
                                                                              .requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN)
                                                                              .requireCompleted(ScrUnits.ZERG_DEFILER_MOUND)
                                                                              .build();

        return gameData.getAddedUnitTypes().add(new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, BASE_TYPE, units)
                                                        .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                                                        .setRequirementScript(requirements)
                                                        .setWireframe(graphics.getWireframe())
                                                        .setCommandIcon(graphics.getCommandIcon())
                                                        .setValue(UnitsDat.PORTRAIT, graphics.getPortrait())
                                                        .setValue(UnitsDat.FLINGY_TYPE, graphics.getFlingy())
                                                        .setValue(UnitsDat.HIT_POINTS, HIT_POINTS * HIT_POINT_SCALE)
                                                        .setValue(UnitsDat.ARMOR, ARMOR)
                                                        .setValue(UnitsDat.MINERAL_COST, MINERAL_COST)
                                                        .setValue(UnitsDat.GAS_COST, GAS_COST)
                                                        .setValue(UnitsDat.BUILD_TIME, BUILD_TIME)
                                                        .setValue(UnitsDat.SUPPLY_REQUIRED, SUPPLY_REQUIRED)
                                                        .setValue(UnitsDat.SIGHT_RANGE, SIGHT_RANGE)
                                                        .setValue(UnitsDat.SPACE_REQUIRED, SPACE_REQUIRED)
                                                        .setValue(UnitsDat.DIMENSION_LEFT, DIMENSIONS[LEFT_INDEX])
                                                        .setValue(UnitsDat.DIMENSION_UP, DIMENSIONS[UP_INDEX])
                                                        .setValue(UnitsDat.DIMENSION_RIGHT, DIMENSIONS[RIGHT_INDEX])
                                                        .setValue(UnitsDat.DIMENSION_DOWN, DIMENSIONS[DOWN_INDEX])
                                                        .setValue(UnitsDat.PLACEMENT_WIDTH, PLACEMENT[WIDTH_INDEX])
                                                        .setValue(UnitsDat.PLACEMENT_HEIGHT, PLACEMENT[HEIGHT_INDEX])
                                                        .setValue(UnitsDat.GROUND_WEAPON, weapon)
                                                        .setValue(UnitsDat.AIR_WEAPON, weapon)
                                                        .setValue(UnitsDat.MAXIMUM_GROUND_HITS, BREATH_HITS)
                                                        .setValue(UnitsDat.MAXIMUM_AIR_HITS, BREATH_HITS)
                                                        .setTrainButton(button)
                                                        .build());
    }

    /**
     * Registers the fire breath's weapon: the Flame Thrower's projectile and behavior (an invisible projectile that appears at the attacker and
     * splashes the enemies around it) against ground and air units, with the giant's range, damage, cooldown and splash radii, normal damage and
     * the Guardian's weapon upgrades (the Zerg flyer attacks).
     *
     * @param gameData the installed data, before its first game copy
     * @return the {@code weapons.dat} id of the added weapon
     */
    public static int registerWeapon(@NotNull GameData gameData) {
        DatTable weapons = gameData.getWeapons();
        int flyerWeapon = gameData.getUnits().get(UnitsDat.GROUND_WEAPON, ScrUnits.getNativeId(BASE_TYPE));

        return gameData.getAddedUnitTypes().addWeapon(new ScrAddedWeapon.Builder(new WeaponType(NAME + " Fire Breath"),
                                                                                 ScrWeapons.getTypes().indexOf(BASE_WEAPON), weapons)
                                                              .setValue(WeaponsDat.TARGET_FLAGS, WeaponTargetFlags.AIR | WeaponTargetFlags.GROUND)
                                                              .setValue(WeaponsDat.MAXIMUM_RANGE, WEAPON_RANGE)
                                                              .setValue(WeaponsDat.DAMAGE_AMOUNT, WEAPON_DAMAGE)
                                                              .setValue(WeaponsDat.DAMAGE_BONUS, WEAPON_DAMAGE_BONUS)
                                                              .setValue(WeaponsDat.COOLDOWN, WEAPON_COOLDOWN)
                                                              .setValue(WeaponsDat.DAMAGE_TYPE, ScrDamageKinds.getTypes().indexOf(ScrDamageKinds.NORMAL))
                                                              .setValue(WeaponsDat.DAMAGE_UPGRADE, weapons.get(WeaponsDat.DAMAGE_UPGRADE, flyerWeapon))
                                                              .setValue(WeaponsDat.INNER_SPLASH_RADIUS, WEAPON_SPLASH_RADII[INNER_INDEX])
                                                              .setValue(WeaponsDat.MEDIUM_SPLASH_RADIUS, WEAPON_SPLASH_RADII[MEDIUM_INDEX])
                                                              .setValue(WeaponsDat.OUTER_SPLASH_RADIUS, WEAPON_SPLASH_RADII[OUTER_INDEX])
                                                              .build());
    }

    private ScrCrossoverMyriarchUtils() {
    }
}
