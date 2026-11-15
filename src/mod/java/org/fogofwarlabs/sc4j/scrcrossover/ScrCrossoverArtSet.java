package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An art set of SC:R Crossover: the baked frames and interface art of one added unit, or the baked frames of an overlay image of a unit, in
 * {@code third-party/<name>/} of the repository, and the files the art converter makes from them under {@code third-party/<name>/} of the jar,
 * with the NOTICE and license files of the unit's folder. Each set has its own SD canvas, pivot, shadow offset and baked quality; the jar paths
 * of its files are given here, and {@link ScrCrossoverArtFiles} makes its SD frames.
 */
public enum ScrCrossoverArtSet {
    /**
     * The Unity-chan infantry unit: frames baked at HD (scale {@code 4}) on a canvas of {@code 128} SD pixels, the ground point under its feet
     * {@code 9} pixels below the image position, the shadow on it.
     */
    UNITY_CHAN("unity-chan", 128, 9, 0, 9, AssetResolver.AssetSet.HD, true),
    /**
     * The flying giant dragon Myriarch: frames baked at HD2 (scale {@code 2}), which the HD renderer also draws, on the largest canvas a GRP
     * frame holds that is a multiple of four, {@code 252} SD pixels; the body flies around the image position and its ground point lies
     * {@code 42} pixels below it, where a native air unit's shadow lies ({@code imgul} shadow {@code 0 42} of the Mutalisk, Guardian and
     * Battlecruiser), so its shadow image is drawn {@code 42} pixels below the image position; the selection circle lies under the body.
     */
    MYRIARCH("myriarch", 252, 42, 42, 16, AssetResolver.AssetSet.HD2, true),
    /**
     * The Myriarch's fire breath, an overlay image of its attack: frames baked at HD2 on the same canvas, the pivot (the breath's root at the
     * mouth) at the image position, which the attack's overlay offsets ({@link #MYRIARCH}, {@link #getOverlayOffsets()}) put at the mouth; no
     * shadow and no interface art. Its frames are the project's own render and ship in the Myriarch's folder.
     */
    MYRIARCH_FIRE("myriarch/fire", 252, 0, 0, 0, AssetResolver.AssetSet.HD2, false);

    private static final @NotNull String THIRD_PARTY_FOLDER = "third-party/";
    private static final @NotNull String GRAPHICS_FOLDER = "graphics/";
    private static final @NotNull String INTERFACE_FOLDER = "interface/";
    private static final @NotNull String PORTRAIT_FOLDER = "portrait/";
    private static final @NotNull String ANIMATION_EXTENSION = ".anim";

    /**
     * Finds the set of a name.
     *
     * @param name the name, the folder of the set
     * @return the set
     * @throws IllegalArgumentException if no set has the name
     */
    public static @NotNull ScrCrossoverArtSet of(@NotNull String name) {
        for (ScrCrossoverArtSet set : values()) {
            if (set.name.equals(name)) {
                return set;
            }
        }

        throw Exceptions.newIllegalArgumentException("No art set is named {0}.", name);
    }


    private final @NotNull String name;
    private final int sdCanvasSize;
    private final int groundOffset;
    private final int shadowOffset;
    private final int selectionCircleOffset;
    private final AssetResolver.@NotNull AssetSet bakedQuality;
    private final boolean unit;

    ScrCrossoverArtSet(@NotNull String name, int sdCanvasSize, int groundOffset, int shadowOffset, int selectionCircleOffset,
                       AssetResolver.@NotNull AssetSet bakedQuality, boolean unit) {
        this.name                  = name;
        this.sdCanvasSize          = sdCanvasSize;
        this.groundOffset          = groundOffset;
        this.shadowOffset          = shadowOffset;
        this.selectionCircleOffset = selectionCircleOffset;
        this.bakedQuality          = bakedQuality;
        this.unit                  = unit;
    }

    /**
     * @return the name: the set's folder under {@code third-party/} of the repository and of the jar
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the side of the SD canvas of every frame, in pixels, at most the GRP limit of {@code 255}
     */
    public int getSdCanvasSize() {
        return this.sdCanvasSize;
    }

    /**
     * @return the distance of the baked frames' pivot (the unit's ground point, or the overlay's root) below the image position, in SD pixels
     */
    public int getGroundOffset() {
        return this.groundOffset;
    }

    /**
     * @return the distance of the shadow image below the image position, in SD pixels: the shadow frames' ground point is the shadow image's
     * position, which the unit's script places this far below its own ({@code imgul})
     */
    public int getShadowOffset() {
        return this.shadowOffset;
    }

    /**
     * @return the distance of the selection circle below the image position, in SD pixels ({@code sprites.dat} Y Position)
     */
    public int getSelectionCircleOffset() {
        return this.selectionCircleOffset;
    }

    /**
     * @return {@code true} if the set is a unit's, with a shadow and interface art, {@code false} if it is an overlay image's frames only
     */
    public boolean isUnit() {
        return this.unit;
    }

    /**
     * @return the quality of the baked frames, {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}: the frame files are made
     * for it and for every lower HD quality, and the higher qualities draw its file
     */
    public AssetResolver.@NotNull AssetSet getBakedQuality() {
        return this.bakedQuality;
    }

    /**
     * @return the jar folder of the set, holding its NOTICE and license files
     */
    public @NotNull String getFolder() {
        return THIRD_PARTY_FOLDER + this.name + "/";
    }

    /**
     * @return the jar folder of the unit's graphics
     */
    public @NotNull String getGraphicsFolder() {
        return this.getFolder() + GRAPHICS_FOLDER;
    }

    /**
     * Returns the unit's frame file of an HD quality: layers {@code diffuse}, {@code bright}, {@code teamcolor}, {@code emissive} (only when a
     * baked frame has one), {@code normal}, {@code specular} and {@code ao_depth}. A quality above the baked quality draws the baked quality's
     * file.
     *
     * @param quality {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}
     * @return the jar path
     */
    public @NotNull String getMainAnimation(AssetResolver.@NotNull AssetSet quality) {
        return this.getGraphicsFolder() + "main-" + ScrCrossoverArtFiles.getQualityFolder(this.getFileQuality(quality)) + ANIMATION_EXTENSION;
    }

    /**
     * Returns the frame file of the unit's shadow of an HD quality: layer {@code diffuse}, black with the shadow's strength in alpha.
     *
     * @param quality {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}
     * @return the jar path
     */
    public @NotNull String getShadowAnimation(AssetResolver.@NotNull AssetSet quality) {
        return this.getGraphicsFolder() + "shadow-" + ScrCrossoverArtFiles.getQualityFolder(this.getFileQuality(quality)) + ANIMATION_EXTENSION;
    }

    /**
     * @return the SD frames in true color: a PNG of {@link ScrCrossoverArtFiles#DIRECTIONS} canvases per row, one row per frame set, red, green, blue
     * and alpha
     */
    public @NotNull String getSdColors() {
        return this.getGraphicsFolder() + "sd-colors.png";
    }

    /**
     * @return the masks of the SD frames, laid out as {@link #getSdColors()}: the team color mask in red and the shadow's strength in green
     */
    public @NotNull String getSdMasks() {
        return this.getGraphicsFolder() + "sd-masks.png";
    }

    /**
     * @return the animations of the frames ({@link ScrCrossoverAnimation#readAll})
     */
    public @NotNull String getAnimations() {
        return this.getGraphicsFolder() + "animations.json";
    }

    /**
     * @return the set of the overlay image the unit's attack creates at its overlay offsets ({@link #getOverlayOffsets()}), or {@code null} if the
     * unit has none
     */
    public @Nullable ScrCrossoverArtSet getOverlaySet() {
        return this == MYRIARCH ? MYRIARCH_FIRE : null;
    }

    /**
     * @return the overlay offsets of the frames, when the set has an overlay set ({@link #getOverlaySet()}): the root of the overlay image per
     * frame of the unit's frames ({@link ScrCrossoverOverlayOffsets})
     */
    public @NotNull String getOverlayOffsets() {
        return this.getGraphicsFolder() + "overlay-offsets.json";
    }

    /**
     * @return the SD frames of the unit as a GRP file, made by {@link ScrCrossoverArtFiles} from {@link #getSdColors()} and {@link #getSdMasks()}
     */
    public @NotNull String getMainFrames() {
        return this.getGraphicsFolder() + "main.grp";
    }

    /**
     * @return the SD frames of the unit's shadow as a GRP file, made by {@link ScrCrossoverArtFiles} from {@link #getSdMasks()}
     */
    public @NotNull String getShadowFrames() {
        return this.getGraphicsFolder() + "shadow.grp";
    }

    /**
     * @return the jar folder of the unit's command icon and wireframes
     */
    public @NotNull String getInterfaceFolder() {
        return this.getFolder() + INTERFACE_FOLDER;
    }

    /**
     * Returns the command icon sheet of a quality.
     *
     * @param quality the asset quality
     * @return the jar path
     */
    public @NotNull String getCommandIcon(AssetResolver.@NotNull AssetSet quality) {
        return this.getInterfaceFolder() + "cmdicon-" + ScrCrossoverArtFiles.getQualityFolder(quality) + ".dds.grp";
    }

    /**
     * Returns the single-selection wireframe strip of a quality.
     *
     * @param quality the asset quality
     * @return the jar path
     */
    public @NotNull String getWireframe(AssetResolver.@NotNull AssetSet quality) {
        return this.getInterfaceFolder() + "wirefram-" + ScrCrossoverArtFiles.getQualityFolder(quality) + ".dds.grp";
    }

    /**
     * @return the SD group-selection wireframe strip; the HD qualities draw every view from the single-selection strip
     */
    public @NotNull String getGroupWireframe() {
        return this.getInterfaceFolder() + "grpwire-sd.dds.grp";
    }

    /**
     * @return the SD cargo wireframe strip
     */
    public @NotNull String getTransportWireframe() {
        return this.getInterfaceFolder() + "tranwire-sd.dds.grp";
    }

    /**
     * @return the jar folder of the unit's portrait clips, one folder per quality
     */
    public @NotNull String getPortraitFolder() {
        return this.getFolder() + PORTRAIT_FOLDER;
    }

    /**
     * Returns the path prefix of the idle portrait clips of a quality.
     *
     * @param quality the asset quality
     * @return the jar path prefix
     */
    public @NotNull String getIdleClips(AssetResolver.@NotNull AssetSet quality) {
        return this.getPortraitFolder() + ScrCrossoverArtFiles.getQualityFolder(quality) + "/" + ScrCrossoverArtFiles.IDLE_CLIP_NAME;
    }

    /**
     * Returns the path prefix of the talking portrait clips of a quality.
     *
     * @param quality the asset quality
     * @return the jar path prefix
     */
    public @NotNull String getTalkingClips(AssetResolver.@NotNull AssetSet quality) {
        return this.getPortraitFolder() + ScrCrossoverArtFiles.getQualityFolder(quality) + "/" + ScrCrossoverArtFiles.TALKING_CLIP_NAME;
    }

    /**
     * Returns the quality whose frame file an HD quality draws: the quality itself, or the baked quality when it is higher.
     */
    private AssetResolver.@NotNull AssetSet getFileQuality(AssetResolver.@NotNull AssetSet quality) {
        return quality.getScale() > this.bakedQuality.getScale() ? this.bakedQuality : quality;
    }
}
