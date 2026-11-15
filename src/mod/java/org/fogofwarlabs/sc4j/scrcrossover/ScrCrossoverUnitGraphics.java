package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ImageType;
import org.fogofwarlabs.sc4j.engine.content.type.MotionProfile;
import org.fogofwarlabs.sc4j.engine.content.type.SpriteType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedFlingy;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedImage;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedScript;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedSprite;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptCode;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModGraphics;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModSheetFrame;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModWireframe;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The graphics and interface art of an added unit of SC:R Crossover, registered from one art set ({@link ScrCrossoverArtSet}) into the installed
 * data: the shadow image, the script made from the set's animations, the unit image, the sprite and the flingy entry, the portrait, the command icon
 * and the wireframe. The unit types ({@link ScrCrossoverUnitUtils}, {@link ScrCrossoverMyriarchUtils}) name the registered entries.
 * <p>
 * The script plays the animations {@value #IDLE}, {@value #WALK}, {@value #ATTACK} and {@value #DEATH}: idle in its play order (with the shadow image
 * created first, {@link ScrCrossoverArtSet#getShadowOffset()} below the image position), the walk (moving the unit by its walk step per game frame
 * when its flingy entry lets the script move it), the ground and the air attack, whose first action frame plays the attack's instructions
 * ({@link Attack}: the attack of a native weapon type's own script, or the mod's own instructions such as an overlay and shifted projectiles), and
 * the death with the voice's death sound, holding its last frame. A set with overlay offsets ({@link ScrCrossoverArtSet#getOverlayOffsets()}) gives
 * the unit image those offsets as its attack overlay table, where the attack's {@code imgoluselo} creates an overlay image
 * ({@link #registerOverlay}).
 * <p>
 * Every animation's code ends in a loop or {@code end}: the code blocks follow each other, so a block that ran past its last instruction would run
 * the next block. A ground unit's attack ends with {@code ignorerest}, which, when the order has no target unit any more, starts the attack's way to
 * idle and goes on with the instruction after it; as in the native unit scripts, the idle loop follows it there. Without it the unit would run the
 * next block, another attack with an unbreakable part, while its animation is already the way to idle, and a walk started then would leave that part
 * unbreakable for good: the unit would walk on the spot and take no command. A flying unit's attack (its base type flies) goes on into its idle loop
 * after {@code gotorepeatattk}, as the native air attackers' scripts (the Mutalisk's, the Guardian's) jump back to theirs: it keeps hovering through
 * the weapon's cooldown, and the repeated attack plays the whole attack again from its first frame, a hover pose.
 */
public final class ScrCrossoverUnitGraphics {
    /**
     * The top speed that keeps the one of the base flingy entry.
     */
    public static final int BASE_TOP_SPEED = 0;

    /**
     * The classic tilesets, whose palettes the SD frames are quantized to.
     */
    private static final int TILESET_COUNT = 8;

    private static final int ANIMATION_INIT = 0;
    private static final int ANIMATION_DEATH = 1;
    private static final int ANIMATION_GROUND_ATTACK_INIT = 2;
    private static final int ANIMATION_AIR_ATTACK_INIT = 3;
    private static final int ANIMATION_GROUND_ATTACK_REPEAT = 5;
    private static final int ANIMATION_AIR_ATTACK_REPEAT = 6;
    private static final int ANIMATION_GROUND_ATTACK_TO_IDLE = 8;
    private static final int ANIMATION_AIR_ATTACK_TO_IDLE = 9;
    private static final int ANIMATION_WALKING = 11;
    private static final int ANIMATION_WALKING_TO_IDLE = 12;
    /**
     * The {@code images.dat} draw function that draws an image's frames as they are.
     */
    private static final int NORMAL_DRAW_FUNCTION = 0;
    /**
     * The sound opcodes a native script plays a shot or death sound with.
     */
    private static final int @NotNull [] SOUND_OPCODES = {IscriptOpcodes.PLAYSND, IscriptOpcodes.PLAYSNDRAND, IscriptOpcodes.PLAYSNDBTWN};
    /**
     * The opcodes a native script attacks with: a melee weapon damages its target at once ({@code attackmelee}, which also plays the hit
     * sound), a weapon with a bullet fires it ({@code attack}, {@code attackwith}).
     */
    private static final int @NotNull [] ATTACK_OPCODES = {IscriptOpcodes.ATTACKMELEE, IscriptOpcodes.ATTACK, IscriptOpcodes.ATTACKWITH};

    private static final @NotNull String IDLE = "idle";
    private static final @NotNull String WALK = "walk";
    private static final @NotNull String ATTACK = "attack";
    private static final @NotNull String DEATH = "death";
    private static final @NotNull String LOOP_LABEL = "loop";
    private static final @NotNull String SHADOW_SUFFIX = " shadow";
    /**
     * The game frames the last death frame stays before the sprite ends.
     */
    private static final int DEATH_HOLD_WAIT = 72;
    /**
     * The halt distance of a flingy entry is the square of its top speed over this multiple of its acceleration.
     */
    private static final int HALT_DISTANCE_ACCELERATION_FACTOR = 2;

    /**
     * The instructions an attack plays at the attack animation's first action frame.
     */
    public static final class Attack {
        /**
         * Creates an {@link Attack} instance of a native unit type's weapon: the attack of an animation of its installed script, its first attack
         * opcode ({@code attackmelee}, {@code attack} or {@code attackwith}: a melee weapon damages its target at once, a weapon with a bullet fires
         * it), after the last sound instruction before it when there is one.
         *
         * @param gameData the installed data
         * @param unitType the native unit type
         * @param air      {@code true} for the air attack, {@code false} for the ground attack
         * @return the created {@link Attack} instance
         * @throws IOException if the installed script has no such attack
         */
        public static @NotNull Attack ofWeaponType(@NotNull GameData gameData, @NotNull UnitType unitType, boolean air) throws IOException {
            int animation = air ? ANIMATION_AIR_ATTACK_REPEAT : ANIMATION_GROUND_ATTACK_REPEAT;

            return new Attack(findAttack(gameData, ScrUnits.getNativeId(unitType), animation));
        }

        /**
         * Creates an {@link Attack} instance of the mod's own instructions.
         *
         * @param instructions the instructions, in order
         * @return the created {@link Attack} instance
         */
        public static @NotNull Attack of(@NotNull List<@NotNull IscriptInstruction> instructions) {
            return new Attack(List.copyOf(instructions));
        }


        private final @NotNull List<@NotNull IscriptInstruction> instructions;

        private Attack(@NotNull List<@NotNull IscriptInstruction> instructions) {
            this.instructions = instructions;
        }

        /**
         * @return the instructions, in order
         */
        public @NotNull List<@NotNull IscriptInstruction> getInstructions() {
            return this.instructions;
        }
    }

    /**
     * Creates the reader of the crossover's graphics, which makes the SD frames of every art set from the installed tileset palettes.
     *
     * @param gameData the installed data
     * @return the files of the crossover's jar
     * @throws IOException if the palettes cannot be read
     */
    public static @NotNull ScrModFiles createFiles(@NotNull GameData gameData) throws IOException {
        List<byte[]> palettes = new ArrayList<>();

        for (int tileset = 0; tileset < TILESET_COUNT; tileset++) {
            palettes.add(gameData.loadTerrainPalette(tileset));
        }

        return new ScrCrossoverArtFiles(ScrModFiles.of(ScrCrossoverUnitGraphics.class.getClassLoader()), palettes);
    }

    /**
     * Registers the graphics and interface art of an added unit.
     *
     * @param gameData     the installed data, before its first game copy
     * @param files        the files of the crossover's jar ({@link #createFiles})
     * @param set          the art set
     * @param name         the unit's name, also the debug name of its entries
     * @param baseType     the native unit type whose image, script, sprite and shadow image the entries start from
     * @param flingyType   the native unit type whose flingy entry (speed, acceleration, turning, movement control) the unit's starts from:
     *                     one moved by its flingy entry for a walk of walk step {@code 0}, one moved by its script otherwise
     * @param circleType   the native unit type whose selection circle and health bar width the unit's sprite takes
     * @param groundAttack the instructions the ground attack plays at its action frame
     * @param airAttack    the instructions the air attack plays at its action frame
     * @param voiceType    the native unit type whose death sound the death plays
     * @param walkStep     the pixels the script moves the unit per game frame of its walk, {@code 0} when its flingy entry moves it
     * @param topSpeed     the top speed in 1/256 pixels per game frame of a unit moved by its flingy entry, or {@link #BASE_TOP_SPEED}
     * @param idleChances  the chances of the idle portrait clips, in percent
     * @return the registered graphics
     * @throws IOException              if the crossover's graphics or the installed scripts cannot be read
     * @throws IllegalArgumentException if the flingy entry of the flingy type is not moved the way the walk is, or a top speed is given for a walk
     *                                  that the script moves
     */
    public static @NotNull ScrCrossoverUnitGraphics register(@NotNull GameData gameData, @NotNull ScrModFiles files, @NotNull ScrCrossoverArtSet set,
                                                             @NotNull String name, @NotNull UnitType baseType, @NotNull UnitType flingyType,
                                                             @NotNull UnitType circleType, @NotNull Attack groundAttack, @NotNull Attack airAttack,
                                                             @NotNull UnitType voiceType, int walkStep, int topSpeed, int @NotNull [] idleChances)
    throws IOException {
        List<ScrCrossoverAnimation> animations = ScrCrossoverAnimation.readAll(files.readFile(set.getAnimations()));
        int baseUnit = ScrUnits.getNativeId(baseType);
        int circleSprite = gameData.getFlingyTypes().getSprite(gameData.getUnitTypes().getFlingyType(ScrUnits.getNativeId(circleType)));
        int baseFlingy = gameData.getUnitTypes().getFlingyType(ScrUnits.getNativeId(flingyType));
        int baseSprite = gameData.getFlingyTypes().getSprite(gameData.getUnitTypes().getFlingyType(baseUnit));
        int baseImage = gameData.getSpriteTypes().getImage(baseSprite);
        int baseScript = gameData.getImageTypes().getIscript(baseImage);
        int baseShadow = findInstruction(gameData, baseUnit, ANIMATION_INIT, IscriptOpcodes.IMGUL).getOperands()[0];
        IscriptInstruction deathSound = findInstruction(gameData, ScrUnits.getNativeId(voiceType), ANIMATION_DEATH, SOUND_OPCODES);
        int movementControl = walkStep == 0 ? ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY : ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT;

        // A unit moved by its script takes its speed from the move steps of its walk; one moved by its flingy entry from that entry's top speed,
        // acceleration and halt distance, which a flingy entry moved by its script does not hold.
        if (gameData.getFlingyTypes().getMovementControl(baseFlingy) != movementControl) {
            throw Exceptions.newIllegalArgumentException("The walk of {0} (walk step {1}) needs a flingy entry of movement control {2}, " +
                                                         "flingy {3} has {4}.",
                                                         name, walkStep, movementControl, baseFlingy,
                                                         gameData.getFlingyTypes().getMovementControl(baseFlingy));
        }

        if (topSpeed != BASE_TOP_SPEED && walkStep != 0) {
            throw Exceptions.newIllegalArgumentException("The walk of {0} is moved by its script, which sets its speed: no top speed.", name);
        }

        ScrAddedGraphics graphics = gameData.getAddedGraphics();
        int shadowImage = graphics.addImage(new ScrAddedImage.Builder(new ImageType(name + SHADOW_SUFFIX), baseShadow, gameData.getImages())
                                                    .setGraphics(new ScrModGraphics.Builder(files, set.getShadowFrames())
                                                                         .setHdAnimation(set.getShadowAnimation(AssetResolver.AssetSet.HD))
                                                                         .setHd2Animation(set.getShadowAnimation(AssetResolver.AssetSet.HD2))
                                                                         .build())
                                                    .build());
        boolean flyer = (gameData.getUnitTypes().getFlags(baseUnit) & UnitTypeFlags.FLYER) != 0;
        int script = graphics.addScript(createScript(gameData, baseScript, shadowImage, set.getShadowOffset(), animations, walkStep,
                                                     groundAttack.getInstructions(), airAttack.getInstructions(), deathSound, flyer));
        ScrAddedImage.Builder imageEntry = new ScrAddedImage.Builder(new ImageType(name), baseImage, gameData.getImages())
                .setValue(ImagesDat.ISCRIPT, script)
                .setGraphics(createGraphics(files, set));

        if (set.getOverlaySet() != null) {
            imageEntry.setOverlayOffsets(ScrImageTypeView.OVERLAY_ATTACK, ScrCrossoverOverlayOffsets.read(files.readFile(set.getOverlayOffsets())));
        }

        int image = graphics.addImage(imageEntry.build());
        int sprite = graphics.addSprite(new ScrAddedSprite.Builder(new SpriteType(name), baseSprite, gameData.getSprites())
                                                .setValue(SpritesDat.IMAGE, image)
                                                .setValue(SpritesDat.SELECTION_CIRCLE, gameData.getSpriteTypes().getSelectionCircle(circleSprite))
                                                .setValue(SpritesDat.SELECTION_CIRCLE_OFFSET, set.getSelectionCircleOffset())
                                                .setValue(SpritesDat.HEALTH_BAR_WIDTH, gameData.getSpriteTypes().getHealthBarWidth(circleSprite))
                                                .build());
        ScrAddedFlingy.Builder flingyEntry = new ScrAddedFlingy.Builder(new MotionProfile(name), baseFlingy, gameData.getFlingy())
                .setValue(FlingyDat.SPRITE, sprite);

        // The halt distance is the distance the unit brakes in from its top speed.
        if (topSpeed != BASE_TOP_SPEED) {
            int acceleration = gameData.getFlingyTypes().getAcceleration(baseFlingy);

            flingyEntry.setValue(FlingyDat.TOP_SPEED, topSpeed)
                       .setValue(FlingyDat.HALT_DISTANCE, topSpeed * topSpeed / (HALT_DISTANCE_ACCELERATION_FACTOR * acceleration));
        }

        int flingy = graphics.addFlingy(flingyEntry.build());
        int portrait = graphics.addPortrait(new ScrModPortrait.Builder(files, set.getIdleClips(AssetResolver.AssetSet.SD),
                                                                       set.getTalkingClips(AssetResolver.AssetSet.SD))
                                                    .setHd2(set.getIdleClips(AssetResolver.AssetSet.HD2),
                                                            set.getTalkingClips(AssetResolver.AssetSet.HD2))
                                                    .setHd(set.getIdleClips(AssetResolver.AssetSet.HD),
                                                           set.getTalkingClips(AssetResolver.AssetSet.HD))
                                                    .setIdleChances(idleChances)
                                                    .build());
        int commandIcon = graphics.addCommandIcon(createSheetFrame(files, set::getCommandIcon));
        int wireframe = graphics.addWireframe(new ScrModWireframe(createSheetFrame(files, set::getWireframe),
                                                                  new ScrModSheetFrame.Builder(files, set.getGroupWireframe()).build(),
                                                                  new ScrModSheetFrame.Builder(files, set.getTransportWireframe()).build()));

        return new ScrCrossoverUnitGraphics(flingy, portrait, commandIcon, wireframe);
    }

    /**
     * Registers an overlay image of an added unit from an art set without interface art ({@link ScrCrossoverArtSet#isUnit()} {@code false}), such as
     * a weapon's flame that the unit's attack creates ({@code imgoluselo}): an image after a native base image, drawn normally, with the set's
     * frames and a script that plays the base image's sound (the first sound instruction of its Init animation, if any), then the set's single
     * animation once in its play order, and ends.
     *
     * @param gameData  the installed data, before its first game copy
     * @param files     the files of the crossover's jar ({@link #createFiles})
     * @param set       the overlay's art set
     * @param name      the overlay's name, the debug name of its image
     * @param baseImage the native image whose values (turning with the unit, script header) and sound the overlay starts from
     * @return the image id of the overlay
     * @throws IOException if the crossover's graphics or the installed scripts cannot be read
     */
    public static int registerOverlay(@NotNull GameData gameData, @NotNull ScrModFiles files, @NotNull ScrCrossoverArtSet set, @NotNull String name,
                                      int baseImage) throws IOException {
        List<ScrCrossoverAnimation> animations = ScrCrossoverAnimation.readAll(files.readFile(set.getAnimations()));

        if (animations.size() != 1) {
            throw Exceptions.newIOException("The overlay set {0} has {1} animations, not one.", set.getName(), animations.size());
        }

        ScrCrossoverAnimation animation = animations.getFirst();
        int baseScript = gameData.getImageTypes().getIscript(baseImage);
        IscriptCode.Builder code = new IscriptCode.Builder();
        IscriptHeader header = gameData.getIscript().findHeader(baseScript);

        if (header != null) {
            header.getAnimations().stream()
                  .filter(entry -> entry.getTypeIndex() == ANIMATION_INIT)
                  .flatMap(entry -> entry.getInstructions().stream())
                  .filter(instruction -> Arrays.stream(SOUND_OPCODES).anyMatch(opcode -> opcode == instruction.getOpcode()))
                  .findFirst()
                  .ifPresent(sound -> code.instruction(sound.getOpcode(), sound.getOperands()));
        }

        for (int frame : animation.getPlayOrder()) {
            code.instruction(IscriptOpcodes.PLAYFRAM, animation.getFrameSetStart(frame))
                .instruction(IscriptOpcodes.WAIT, animation.getWait(frame));
        }

        ScrAddedGraphics graphics = gameData.getAddedGraphics();
        int script = graphics.addScript(new ScrAddedScript.Builder(baseScript, gameData.getIscript())
                                                .setAnimation(ANIMATION_INIT, code.instruction(IscriptOpcodes.END).build())
                                                .build());

        return graphics.addImage(new ScrAddedImage.Builder(new ImageType(name), baseImage, gameData.getImages())
                                         .setValue(ImagesDat.ISCRIPT, script)
                                         .setValue(ImagesDat.DRAW_FUNCTION, NORMAL_DRAW_FUNCTION)
                                         .setGraphics(createGraphics(files, set))
                                         .build());
    }

    /**
     * Finds the overlay image a native unit type's ground attack creates (its first {@code imgol} instruction), such as the Firebat's flame.
     *
     * @param gameData the installed data
     * @param unitType the native unit type
     * @return the image id
     * @throws IOException if the installed script's ground attack creates no overlay
     */
    public static int findAttackOverlayImage(@NotNull GameData gameData, @NotNull UnitType unitType) throws IOException {
        return findInstruction(gameData, ScrUnits.getNativeId(unitType), ANIMATION_GROUND_ATTACK_REPEAT, IscriptOpcodes.IMGOL).getOperands()[0];
    }

    /**
     * Creates the frame files of a set's image: its SD frames and its HD frame files.
     */
    private static @NotNull ScrModGraphics createGraphics(@NotNull ScrModFiles files, @NotNull ScrCrossoverArtSet set) {
        return new ScrModGraphics.Builder(files, set.getMainFrames())
                .setHdAnimation(set.getMainAnimation(AssetResolver.AssetSet.HD))
                .setHd2Animation(set.getMainAnimation(AssetResolver.AssetSet.HD2))
                .build();
    }

    /**
     * Creates the frame files of an interface sheet frame of every quality.
     */
    private static @NotNull ScrModSheetFrame createSheetFrame(@NotNull ScrModFiles files,
                                                              @NotNull Function<AssetResolver.@NotNull AssetSet, @NotNull String> paths) {
        return new ScrModSheetFrame.Builder(files, paths.apply(AssetResolver.AssetSet.SD))
                .setHd2(paths.apply(AssetResolver.AssetSet.HD2))
                .setHd(paths.apply(AssetResolver.AssetSet.HD))
                .build();
    }

    /**
     * Finds the first instruction of one of the opcodes in an animation of the installed script of a native unit's image.
     */
    private static @NotNull IscriptInstruction findInstruction(@NotNull GameData gameData, int unitType, int animation,
                                                               int @NotNull ... opcodes) throws IOException {
        for (IscriptInstruction instruction : findAnimation(gameData, unitType, animation).getInstructions()) {
            if (Arrays.stream(opcodes).anyMatch(opcode -> opcode == instruction.getOpcode())) {
                return instruction;
            }
        }

        throw Exceptions.newIOException("The installed script of unit type {0} has none of the opcodes {1} in animation {2}.", unitType,
                                        Arrays.toString(opcodes), animation);
    }

    /**
     * Finds an animation of the installed script of a native unit's image.
     */
    private static IscriptHeader.@NotNull Animation findAnimation(@NotNull GameData gameData, int unitType, int animation) throws IOException {
        int flingy = gameData.getUnitTypes().getFlingyType(unitType);
        int script = gameData.getImageTypes().getIscript(gameData.getSpriteTypes().getImage(gameData.getFlingyTypes().getSprite(flingy)));
        IscriptHeader header = gameData.getIscript().findHeader(script);

        if (header != null) {
            for (IscriptHeader.Animation entry : header.getAnimations()) {
                if (entry.getTypeIndex() == animation) {
                    return entry;
                }
            }
        }

        throw Exceptions.newIOException("The installed script {0} of unit type {1} has no animation {2}.", script, unitType, animation);
    }

    /**
     * Finds the attack of an animation of the installed script of a native unit's image: its first attack opcode ({@link #ATTACK_OPCODES}), after
     * the last sound instruction before it when there is one.
     */
    private static @NotNull List<@NotNull IscriptInstruction> findAttack(@NotNull GameData gameData, int unitType, int animation) throws IOException {
        IscriptInstruction sound = null;

        for (IscriptInstruction instruction : findAnimation(gameData, unitType, animation).getInstructions()) {
            if (Arrays.stream(SOUND_OPCODES).anyMatch(opcode -> opcode == instruction.getOpcode())) {
                sound = instruction;
            } else if (Arrays.stream(ATTACK_OPCODES).anyMatch(opcode -> opcode == instruction.getOpcode())) {
                return sound == null ? List.of(instruction) : List.of(sound, instruction);
            }
        }

        throw Exceptions.newIOException("The installed script of unit type {0} has no attack opcode in animation {1}.", unitType, animation);
    }

    private static @NotNull ScrAddedScript createScript(@NotNull GameData gameData, int baseScript, int shadowImage, int shadowOffset,
                                                        @NotNull List<@NotNull ScrCrossoverAnimation> animations, int walkStep,
                                                        @NotNull List<@NotNull IscriptInstruction> groundAttack,
                                                        @NotNull List<@NotNull IscriptInstruction> airAttack,
                                                        @NotNull IscriptInstruction deathSound, boolean flyer) throws IOException {
        ScrCrossoverAnimation idle = ScrCrossoverAnimation.find(animations, IDLE);
        ScrCrossoverAnimation walk = ScrCrossoverAnimation.find(animations, WALK);
        ScrCrossoverAnimation attack = ScrCrossoverAnimation.find(animations, ATTACK);
        ScrCrossoverAnimation death = ScrCrossoverAnimation.find(animations, DEATH);
        int repeatStep = flyer ? 0 : findAimStep(attack);
        IscriptCode init = appendIdle(new IscriptCode.Builder().instruction(IscriptOpcodes.IMGUL, shadowImage, 0, shadowOffset), idle).build();
        IscriptCode toIdle = appendIdle(new IscriptCode.Builder(), idle).build();
        IscriptCode groundAttackInit = appendIdle(appendAttack(new IscriptCode.Builder(), attack, 0, groundAttack, flyer), idle).build();
        IscriptCode groundAttackRepeat = appendIdle(appendAttack(new IscriptCode.Builder(), attack, repeatStep, groundAttack, flyer), idle).build();
        IscriptCode airAttackInit = appendIdle(appendAttack(new IscriptCode.Builder(), attack, 0, airAttack, flyer), idle).build();
        IscriptCode airAttackRepeat = appendIdle(appendAttack(new IscriptCode.Builder(), attack, repeatStep, airAttack, flyer), idle).build();

        return new ScrAddedScript.Builder(baseScript, gameData.getIscript()).setAnimation(ANIMATION_INIT, init)
                                                                            .setAnimation(ANIMATION_DEATH, createDeath(death, deathSound))
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_INIT, groundAttackInit)
                                                                            .setAnimation(ANIMATION_AIR_ATTACK_INIT, airAttackInit)
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_REPEAT, groundAttackRepeat)
                                                                            .setAnimation(ANIMATION_AIR_ATTACK_REPEAT, airAttackRepeat)
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_TO_IDLE, toIdle)
                                                                            .setAnimation(ANIMATION_AIR_ATTACK_TO_IDLE, toIdle)
                                                                            .setAnimation(ANIMATION_WALKING, createWalk(walk, walkStep))
                                                                            .setAnimation(ANIMATION_WALKING_TO_IDLE, toIdle)
                                                                            .build();
    }

    private static @NotNull IscriptCode.Builder appendIdle(IscriptCode.@NotNull Builder code, @NotNull ScrCrossoverAnimation idle) {
        code.label(LOOP_LABEL);

        for (int frame : idle.getPlayOrder()) {
            code.instruction(IscriptOpcodes.PLAYFRAM, idle.getFrameSetStart(frame))
                .instruction(IscriptOpcodes.WAIT, idle.getWait(frame));
        }

        return code.jump(LOOP_LABEL);
    }

    private static @NotNull IscriptCode createWalk(@NotNull ScrCrossoverAnimation walk, int walkStep) {
        IscriptCode.Builder code = new IscriptCode.Builder().label(LOOP_LABEL);

        for (int frame : walk.getPlayOrder()) {
            code.instruction(IscriptOpcodes.PLAYFRAM, walk.getFrameSetStart(frame));

            if (walkStep == 0) {
                code.instruction(IscriptOpcodes.WAIT, walk.getWait(frame));
                continue;
            }

            for (int wait = 0; wait < walk.getWait(frame); wait++) {
                code.instruction(IscriptOpcodes.MOVE, walkStep)
                    .instruction(IscriptOpcodes.WAIT, 1);
            }
        }

        return code.jump(LOOP_LABEL).build();
    }

    /**
     * Finds the step of the play order before the first action frame: the aim that the repeated attack starts from.
     */
    private static int findAimStep(@NotNull ScrCrossoverAnimation attack) throws IOException {
        int[] playOrder = attack.getPlayOrder();

        for (int step = 1; step < playOrder.length; step++) {
            if (attack.isActionFrame(playOrder[step])) {
                return step - 1;
            }
        }

        throw Exceptions.newIOException("The attack animation has no action frame after its first frame.");
    }

    /**
     * Appends the attack from a step of the play order: the frames before the first action frame, then, unbreakable, the remaining frames with the
     * attack's instructions ({@link Attack}) at the first action frame. A ground unit goes back to the aim frame and its repeat waits for the
     * weapon's cooldown there ({@code ignorerest}); a flyer goes on into the idle loop that follows, hovering through the cooldown.
     */
    private static @NotNull IscriptCode.Builder appendAttack(IscriptCode.@NotNull Builder code, @NotNull ScrCrossoverAnimation attack, int firstStep,
                                                             @NotNull List<@NotNull IscriptInstruction> weaponAttack, boolean flyer) throws IOException {
        int[] playOrder = attack.getPlayOrder();
        int aimStep = findAimStep(attack);
        boolean attacked = false;

        for (int step = firstStep; step < playOrder.length; step++) {
            int frame = playOrder[step];

            if (step == aimStep + 1) {
                code.instruction(IscriptOpcodes.NOBRKCODESTART);
            }

            code.instruction(IscriptOpcodes.PLAYFRAM, attack.getFrameSetStart(frame));

            if (!attacked && attack.isActionFrame(frame)) {
                for (IscriptInstruction instruction : weaponAttack) {
                    code.instruction(instruction.getOpcode(), instruction.getOperands());
                }

                attacked = true;
            }

            code.instruction(IscriptOpcodes.WAIT, attack.getWait(frame));
        }

        if (flyer) {
            return code.instruction(IscriptOpcodes.NOBRKCODEEND)
                       .instruction(IscriptOpcodes.GOTOREPEATATTK);
        }

        return code.instruction(IscriptOpcodes.PLAYFRAM, attack.getFrameSetStart(playOrder[aimStep]))
                   .instruction(IscriptOpcodes.NOBRKCODEEND)
                   .instruction(IscriptOpcodes.GOTOREPEATATTK)
                   .instruction(IscriptOpcodes.IGNOREREST);
    }

    private static @NotNull IscriptCode createDeath(@NotNull ScrCrossoverAnimation death, @NotNull IscriptInstruction deathSound) {
        IscriptCode.Builder code = new IscriptCode.Builder().instruction(deathSound.getOpcode(), deathSound.getOperands());

        for (int frame : death.getPlayOrder()) {
            code.instruction(IscriptOpcodes.PLAYFRAM, death.getFrameSetStart(frame))
                .instruction(IscriptOpcodes.WAIT, death.getWait(frame));
        }

        if (death.isHoldLastFrame()) {
            code.instruction(IscriptOpcodes.WAIT, DEATH_HOLD_WAIT);
        }

        return code.instruction(IscriptOpcodes.END).build();
    }


    private final int flingy;
    private final int portrait;
    private final int commandIcon;
    private final int wireframe;

    private ScrCrossoverUnitGraphics(int flingy, int portrait, int commandIcon, int wireframe) {
        this.flingy      = flingy;
        this.portrait    = portrait;
        this.commandIcon = commandIcon;
        this.wireframe   = wireframe;
    }

    /**
     * @return the unit's flingy entry, for {@code UnitsDat.FLINGY_TYPE}
     */
    public int getFlingy() {
        return this.flingy;
    }

    /**
     * @return the unit's portrait, for {@code UnitsDat.PORTRAIT}
     */
    public int getPortrait() {
        return this.portrait;
    }

    /**
     * @return the unit's command icon frame
     */
    public int getCommandIcon() {
        return this.commandIcon;
    }

    /**
     * @return the unit's wireframe frame
     */
    public int getWireframe() {
        return this.wireframe;
    }
}
