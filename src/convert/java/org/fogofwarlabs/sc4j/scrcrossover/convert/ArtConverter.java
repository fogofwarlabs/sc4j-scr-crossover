package org.fogofwarlabs.sc4j.scrcrossover.convert;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverAnimation;
import org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverArtFiles;
import org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverArtSet;
import org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverOverlayOffsets;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Converts the baked frames of the art sets of SC:R Crossover ({@link ScrCrossoverArtSet}) into the added units' graphics of its jar
 * ({@link ScrCrossoverArtFiles}) at build time. The input of a set is its folder {@code third-party/<name>}: {@code metadata.json}
 * ({@link BakedAnimation}) and per animation, direction and frame the PNG files of the color, the team color mask, the ground shadow and the lighting
 * layers, at the set's baked quality (in the folder {@code hd} or {@code hd2}). The output folder receives the set's files:
 * <ul>
 *     <li>the unit's frame files of the baked quality and of every lower HD quality with the layers {@code diffuse} (the color), {@code bright}
 *     (the albedo), {@code teamcolor} (the mask), {@code emissive} (only when a baked frame has one), {@code normal}, {@code specular} and
 *     {@code ao_depth}, the team color area of {@code diffuse} and {@code bright} turned light gray by its brightness ({@link #TEAM_GRAY_GAIN}) so
 *     that the renderer tints it with the player color; {@code normal} and {@code ao_depth} in the channel layout of the installed unit frames:
 *     the renderer reads the normal's x from red and its y from alpha, and the AO from green and the depth from alpha, and the installed frames
 *     keep the other channels black. The bake's viewing copies in those channels are cleared, because a DXT5 color block shares one index per
 *     pixel between red, green and blue: uncorrelated copies of y and z next to x would cost the x values their steps and leave visible block
 *     edges under lighting;</li>
 *     <li>the shadow's frame files of the same qualities, black with the baked shadow's alpha times {@link #SHADOW_STRENGTH_GAIN}, the ground
 *     point at the shadow image's position ({@link ScrCrossoverArtSet#getShadowOffset()} below the unit's);</li>
 *     <li>the SD frames in true color and their masks, the baked frames scaled down to one pixel per SD pixel, for the palette quantization when
 *     the mod loads;</li>
 *     <li>the animations ({@link ScrCrossoverAnimation}): the frame sets of each animation in the metadata's order, its play order, each frame's
 *     duration in game frames of {@value #GAME_FRAME_MILLISECONDS} milliseconds (the fastest game speed), and its action frames;</li>
 *     <li>the overlay offsets ({@link ScrCrossoverOverlayOffsets}) when the set's folder has them ({@value #OVERLAY_OFFSETS}: per animation, frame
 *     and direction the overlay's root in SD pixels from the image position), in the order of the frame files.</li>
 * </ul>
 * An overlay image's set ({@link ScrCrossoverArtSet#isUnit()} {@code false}) has no shadow and no interface art.
 * Frame {@code n} of the files is direction {@code n % 17} of frame set {@code n / 17}. Every baked frame is cut to the box of its visible pixels,
 * widened to whole compressed blocks of the HD2 texture so that no block of any written texture holds two frames, and placed by its offset in the
 * set's canvas ({@link ImageFileWriterUtils#ANIM_FRAME_CANVAS_PLACED}). The output depends on the input files only.
 * <p>
 * Arguments: the folder of the art sets ({@code third-party}) and the output folder; every art set is converted.
 */
public final class ArtConverter {
    /**
     * The gain of the brightness of a team color pixel: the light gray that replaces its baked color.
     */
    public static final double TEAM_GRAY_GAIN = 1.4;
    /**
     * The gain of the baked shadow's alpha (at most half opacity): the renderer applies its own shadow strength to the HD shadow's alpha.
     */
    public static final int SHADOW_STRENGTH_GAIN = 2;
    /**
     * The duration of a game frame at the fastest game speed, in milliseconds.
     */
    public static final int GAME_FRAME_MILLISECONDS = 42;
    /**
     * The largest side of an atlas texture, in texels: an atlas packed higher than this is made wider instead.
     */
    public static final int MAXIMUM_ATLAS_SIZE = 16384;

    private static final int ARGUMENT_COUNT = 2;
    private static final @NotNull String METADATA = "metadata.json";
    private static final @NotNull String OVERLAY_OFFSETS = "overlay-offsets.json";
    private static final @NotNull String OFFSET_ANIMATIONS_FIELD = "animations";
    private static final @NotNull String FRAME_NAME = "d%02d_f%02d";
    private static final @NotNull String PNG_EXTENSION = ".png";
    private static final @NotNull String MASK_SUFFIX = "_mask";
    private static final @NotNull String SHADOW_SUFFIX = "_shadow";
    private static final @NotNull String ICON_FOLDER = "icon";
    private static final @NotNull String COMMAND_ICON_FILE = "cmdicon.png";
    private static final @NotNull String WIREFRAME_FOLDER = "wireframe";
    private static final @NotNull String WIREFRAME_FILE = "wirefram.png";
    private static final @NotNull String GROUP_WIREFRAME_FILE = "grpwire.png";
    private static final @NotNull String TRANSPORT_WIREFRAME_FILE = "tranwire.png";
    private static final @NotNull String PORTRAIT_FOLDER = "portrait";

    private static final @NotNull String DIFFUSE = "diffuse";
    private static final @NotNull String BRIGHT = "bright";
    private static final @NotNull String TEAM_COLOR = "teamcolor";
    private static final @NotNull String EMISSIVE = "emissive";
    private static final @NotNull String NORMAL = "normal";
    private static final @NotNull String SPECULAR = "specular";
    private static final @NotNull String AO_DEPTH = "ao_depth";
    /**
     * The layers of the unit's frame files, in file order; {@code emissive} only when a baked frame has one.
     */
    private static final @NotNull List<@NotNull String> MAIN_LAYERS = List.of(DIFFUSE, BRIGHT, TEAM_COLOR, EMISSIVE, NORMAL, SPECULAR, AO_DEPTH);
    /**
     * The layers whose alpha is coverage: they are scaled down weighted by alpha and compressed with the color range of their visible pixels.
     */
    private static final @NotNull List<@NotNull String> COVERAGE_LAYERS = List.of(DIFFUSE, BRIGHT, EMISSIVE);

    /**
     * The value of a layer outside the visible pixels, packed {@code 0xRRGGBBAA}: transparent, a flat normal facing the viewer, no specular,
     * no occlusion at ground depth (the renderer's default layers, in the installed frames' channel layout).
     */
    private static final int TRANSPARENT = 0x00000000;
    private static final int FLAT_NORMAL = 0x80000080;
    private static final int NO_SPECULAR = 0x000000FF;
    private static final int NO_OCCLUSION = 0x00FF0000;
    private static final int OPAQUE_BLACK = 0x000000FF;

    /**
     * The elements of a frame's box: left, top, width and height in baked texels.
     */
    private static final int BOX_LEFT = 0;
    private static final int BOX_TOP = 1;
    private static final int BOX_WIDTH = 2;
    private static final int BOX_HEIGHT = 3;
    /**
     * The elements of an atlas position and of an overlay offset: x and y.
     */
    private static final int POSITION_X = 0;
    private static final int POSITION_Y = 1;
    private static final int POSITION_LENGTH = 2;

    private static final int HD_SCALE = AssetResolver.AssetSet.HD.getScale();
    private static final int HD2_SCALE = AssetResolver.AssetSet.HD2.getScale();
    /**
     * The width of the HD atlas, in HD texels; an atlas of a lower quality starts as wide in SD pixels.
     */
    private static final int ATLAS_WIDTH = 2048;
    private static final int BLOCK_SIZE = 4;
    private static final double RED_LUMINANCE = 0.299;
    private static final double GREEN_LUMINANCE = 0.587;
    private static final double BLUE_LUMINANCE = 0.114;

    /**
     * One frame of a frame file: its layers cut to its box, and the box's offset in the canvas in frame units.
     */
    private static final class CanvasFrame {
        private final @NotNull Map<@NotNull String, @NotNull RgbaImage> layers;
        private final int canvasX;
        private final int canvasY;

        private CanvasFrame(@NotNull Map<@NotNull String, @NotNull RgbaImage> layers, int canvasX, int canvasY) {
            this.layers  = layers;
            this.canvasX = canvasX;
            this.canvasY = canvasY;
        }
    }

    /**
     * Converts every art set.
     *
     * @param arguments the folder of the art sets and the output folder
     * @throws IOException if a file cannot be read or written, or the baked frames do not match their metadata
     */
    public static void main(@NotNull String @NotNull [] arguments) throws IOException {
        if (arguments.length != ARGUMENT_COUNT) {
            throw Exceptions.newIllegalArgumentException("Usage: ArtConverter <art set folder> <output folder>");
        }

        for (ScrCrossoverArtSet set : ScrCrossoverArtSet.values()) {
            convert(set, Path.of(arguments[0]).resolve(set.getName()), Path.of(arguments[1]));
        }
    }

    /**
     * Converts the art folder of a set into the set's files under an output folder.
     *
     * @param set    the art set
     * @param art    the set's art folder, holding {@code metadata.json}, the frame folder of its baked quality, {@code portrait/},
     *               {@code wireframe/} and {@code icon/}
     * @param output the output folder, the jar root of the written files
     * @throws IOException if a file cannot be read or written, or the baked frames do not match their metadata
     */
    public static void convert(@NotNull ScrCrossoverArtSet set, @NotNull Path art, @NotNull Path output) throws IOException {
        new ArtConverter(set, art, output).convert();
    }

    /**
     * Writes an image as a single-frame {@code .dds.grp} sheet (DXT5, alpha as coverage).
     */
    private static void writeSheetFrame(@NotNull Path source, int scale, @NotNull Path target) throws IOException {
        RgbaImage image = RgbaImage.read(source);

        if (image.getWidth() % BLOCK_SIZE != 0 || image.getHeight() % BLOCK_SIZE != 0) {
            throw Exceptions.newIOException("{0} is not a whole number of {1} pixel blocks.", source, BLOCK_SIZE);
        }

        byte[] texture = ImageFileWriterUtils.encodeDxt5(image.getWidth(), image.getHeight(), image.getPixels(), true);

        Files.createDirectories(target.getParent());
        Files.write(target, ImageFileWriterUtils.encodeDdsGrp(scale, List.of(texture)));
    }

    /**
     * Copies the clips {@code 0} to {@code count - 1} of a name prefix to a target path prefix.
     */
    private static void copyClips(@NotNull Path folder, @NotNull String name, int count, @NotNull Path target) throws IOException {
        Files.createDirectories(target.getParent());

        for (int clip = 0; clip < count; clip++) {
            Path source = folder.resolve(name + clip + ScrModPortrait.CLIP_EXTENSION);

            if (!Files.isRegularFile(source)) {
                throw Exceptions.newIOException("The portrait clip {0} is missing.", source);
            }

            Files.copy(source, target.resolveSibling(target.getFileName() + Integer.toString(clip) + ScrModPortrait.CLIP_EXTENSION),
                       StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Turns the team color area light gray: each pixel moves towards the gray of its brightness times {@link #TEAM_GRAY_GAIN} by its mask value.
     */
    private static void tintTeamArea(@NotNull RgbaImage image, @NotNull RgbaImage mask) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int weight = mask.get(x, y, RgbaImage.RED);

                if (weight == 0) {
                    continue;
                }

                double luminance = RED_LUMINANCE * image.get(x, y, RgbaImage.RED) + GREEN_LUMINANCE * image.get(x, y, RgbaImage.GREEN) +
                                   BLUE_LUMINANCE * image.get(x, y, RgbaImage.BLUE);
                int gray = (int) Math.min(RgbaImage.MAXIMUM, Math.round(luminance * TEAM_GRAY_GAIN));

                for (int channel = 0; channel < RgbaImage.ALPHA; channel++) {
                    int value = image.get(x, y, channel);

                    image.set(x, y, channel, value + Math.round((gray - value) * weight / (float) RgbaImage.MAXIMUM));
                }
            }
        }
    }

    /**
     * Sets channels the renderer does not read to zero, as the installed frames hold them.
     */
    private static @NotNull RgbaImage clearChannels(@NotNull RgbaImage image, int @NotNull ... channels) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int channel : channels) {
                    image.set(x, y, channel, 0);
                }
            }
        }

        return image;
    }

    private static void strengthenShadow(@NotNull RgbaImage shadow) {
        for (int y = 0; y < shadow.getHeight(); y++) {
            for (int x = 0; x < shadow.getWidth(); x++) {
                for (int channel = 0; channel < RgbaImage.ALPHA; channel++) {
                    shadow.set(x, y, channel, 0);
                }

                shadow.set(x, y, RgbaImage.ALPHA, Math.min(RgbaImage.MAXIMUM, shadow.get(x, y, RgbaImage.ALPHA) * SHADOW_STRENGTH_GAIN));
            }
        }
    }

    private static int alignUp(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    private static int getDefaultValue(@NotNull String layer) {
        return switch (layer) {
            case NORMAL -> FLAT_NORMAL;
            case SPECULAR -> NO_SPECULAR;
            case AO_DEPTH -> NO_OCCLUSION;
            default -> TRANSPARENT;
        };
    }

    /**
     * Places frames of a size in rows of an atlas of a width: the position of each frame, then the atlas height in the last element.
     */
    private static @NotNull List<int @NotNull []> packAtlas(@NotNull List<@NotNull CanvasFrame> frames, @NotNull String layer,
                                                            int factor, int atlasWidth) {
        List<int[]> positions = new ArrayList<>();
        int x = 0;
        int y = 0;
        int rowHeight = 0;

        for (CanvasFrame frame : frames) {
            RgbaImage first = frame.layers.get(layer);
            int width = first.getWidth() / factor;
            int height = first.getHeight() / factor;

            if (x + width > atlasWidth) {
                x         = 0;
                y += rowHeight;
                rowHeight = 0;
            }

            positions.add(new int[]{x, y});
            x += width;
            rowHeight = Math.max(rowHeight, height);
        }

        positions.add(new int[]{atlasWidth, alignUp(y + rowHeight, BLOCK_SIZE)});

        return positions;
    }


    private final @NotNull ScrCrossoverArtSet set;
    private final @NotNull Path art;
    private final @NotNull Path output;

    /**
     * The texels per SD pixel of the baked frames.
     */
    private final int bakedScale;
    /**
     * The frame units per baked texel.
     */
    private final int unitsPerBakedTexel;
    /**
     * The alignment of every baked frame's box and atlas position, in baked texels: one compressed block of the HD2 texture.
     */
    private final int frameAlignment;
    private final int canvasUnits;
    private final int groundXUnits;
    private final int groundYUnits;
    private final int shadowGroundYUnits;

    private ArtConverter(@NotNull ScrCrossoverArtSet set, @NotNull Path art, @NotNull Path output) {
        this.set    = set;
        this.art    = art;
        this.output = output;

        this.bakedScale         = set.getBakedQuality().getScale();
        this.unitsPerBakedTexel = ImageFileWriterUtils.ANIM_UNIT_SCALE / this.bakedScale;
        this.frameAlignment     = BLOCK_SIZE * this.bakedScale / HD2_SCALE;
        this.canvasUnits        = set.getSdCanvasSize() * ImageFileWriterUtils.ANIM_UNIT_SCALE;
        this.groundXUnits       = this.canvasUnits / 2;
        this.groundYUnits       = this.canvasUnits / 2 + set.getGroundOffset() * ImageFileWriterUtils.ANIM_UNIT_SCALE;
        this.shadowGroundYUnits = this.groundYUnits - set.getShadowOffset() * ImageFileWriterUtils.ANIM_UNIT_SCALE;
    }

    private void convert() throws IOException {
        String bakedFolder = ScrCrossoverArtFiles.getQualityFolder(this.set.getBakedQuality());
        List<BakedAnimation> baked = BakedAnimation.readAll(this.art.resolve(METADATA), ScrCrossoverArtFiles.DIRECTIONS, bakedFolder);
        int frameSets = baked.stream().mapToInt(BakedAnimation::getFrameCount).sum();
        int canvasSize = this.set.getSdCanvasSize();
        RgbaImage sdColors = RgbaImage.fill(ScrCrossoverArtFiles.DIRECTIONS * canvasSize, frameSets * canvasSize, TRANSPARENT);
        RgbaImage sdMasks = RgbaImage.fill(ScrCrossoverArtFiles.DIRECTIONS * canvasSize, frameSets * canvasSize, OPAQUE_BLACK);
        boolean emissive = baked.stream().anyMatch(BakedAnimation::hasEmissiveLayer);
        List<String> mainLayers = MAIN_LAYERS.stream().filter(layer -> emissive || !layer.equals(EMISSIVE)).toList();
        List<CanvasFrame> mainFrames = new ArrayList<>();
        List<CanvasFrame> shadowFrames = new ArrayList<>();
        List<ScrCrossoverAnimation> animations = new ArrayList<>();
        int frameSet = 0;

        for (BakedAnimation animation : baked) {
            int[] waits = new int[animation.getFrameCount()];

            for (int frame = 0; frame < animation.getFrameCount(); frame++) {
                for (int direction = 0; direction < ScrCrossoverArtFiles.DIRECTIONS; direction++) {
                    this.convertFrame(this.art.resolve(bakedFolder).resolve(animation.getName()), animation, frame, direction, frameSet, emissive,
                                      sdColors, sdMasks, mainFrames, shadowFrames);
                }

                waits[frame] = Math.max(1, Math.round(animation.getDuration(frame) / (float) GAME_FRAME_MILLISECONDS));
                frameSet++;
            }

            animations.add(new ScrCrossoverAnimation(animation.getName(), frameSet - animation.getFrameCount(), animation.getFrameCount(),
                                                     animation.getPlayOrder(), waits, animation.isLoop(), animation.isHoldLastFrame(),
                                                     animation.getActionFrames()));
        }

        Files.createDirectories(this.output.resolve(this.set.getGraphicsFolder()));

        for (AssetResolver.AssetSet quality : List.of(AssetResolver.AssetSet.HD, AssetResolver.AssetSet.HD2)) {
            int scale = quality.getScale();

            if (scale <= this.bakedScale) {
                Files.write(this.output.resolve(this.set.getMainAnimation(quality)), this.encodeAnimation(mainFrames, mainLayers, scale));

                if (this.set.isUnit()) {
                    Files.write(this.output.resolve(this.set.getShadowAnimation(quality)),
                                this.encodeAnimation(shadowFrames, List.of(DIFFUSE), scale));
                }
            }
        }

        sdColors.writePng(this.output.resolve(this.set.getSdColors()), true);
        sdMasks.writePng(this.output.resolve(this.set.getSdMasks()), false);
        Files.write(this.output.resolve(this.set.getAnimations()), ScrCrossoverAnimation.writeAll(animations));

        if (Files.exists(this.art.resolve(OVERLAY_OFFSETS))) {
            Files.write(this.output.resolve(this.set.getOverlayOffsets()), ScrCrossoverOverlayOffsets.write(this.readOverlayOffsets(baked)));
        }

        if (this.set.isUnit()) {
            this.convertInterface();
        }
    }

    /**
     * Reads the set's overlay offsets ({@value #OVERLAY_OFFSETS}) in the order of the frame files: per animation of the metadata, frame and
     * direction.
     */
    private int @NotNull [] @NotNull [] readOverlayOffsets(@NotNull List<@NotNull BakedAnimation> baked) throws IOException {
        Path file = this.art.resolve(OVERLAY_OFFSETS);

        if (!(JsonReader.parse(Files.readAllBytes(file)) instanceof Map<?, ?> root) ||
            !(root.get(OFFSET_ANIMATIONS_FIELD) instanceof Map<?, ?> byAnimation)) {
            throw Exceptions.newIOException("{0} has no {1} object.", file, OFFSET_ANIMATIONS_FIELD);
        }

        List<int[]> offsets = new ArrayList<>();

        for (BakedAnimation animation : baked) {
            if (!(byAnimation.get(animation.getName()) instanceof List<?> frames) || frames.size() != animation.getFrameCount()) {
                throw Exceptions.newIOException("{0} does not hold the {1} frames of the animation {2}.", file, animation.getFrameCount(),
                                                animation.getName());
            }

            for (Object frame : frames) {
                if (!(frame instanceof List<?> directions) || directions.size() != ScrCrossoverArtFiles.DIRECTIONS) {
                    throw Exceptions.newIOException("A frame of {0} in {1} does not hold {2} directions.", animation.getName(), file,
                                                    ScrCrossoverArtFiles.DIRECTIONS);
                }

                for (Object direction : directions) {
                    if (!(direction instanceof List<?> pair) || pair.size() != POSITION_LENGTH || !(pair.get(POSITION_X) instanceof Number x) ||
                        !(pair.get(POSITION_Y) instanceof Number y)) {
                        throw Exceptions.newIOException("An offset of {0} in {1} is not an x and y offset.", animation.getName(), file);
                    }

                    offsets.add(new int[]{x.intValue(), y.intValue()});
                }
            }
        }

        return offsets.toArray(int[][]::new);
    }

    /**
     * Converts the unit's interface art of every quality: the command icon and the wireframe strips (PNG) into single-frame {@code .dds.grp}
     * sheets with the quality's texture scale, and copies the portrait clips ({@code .webm}), which already have the format of the installed
     * clips.
     */
    private void convertInterface() throws IOException {
        Files.createDirectories(this.output.resolve(this.set.getInterfaceFolder()));

        for (AssetResolver.AssetSet quality : AssetResolver.AssetSet.values()) {
            String folder = ScrCrossoverArtFiles.getQualityFolder(quality);
            int scale = quality.getScale();
            Path portraits = this.art.resolve(PORTRAIT_FOLDER).resolve(folder);

            writeSheetFrame(this.art.resolve(ICON_FOLDER).resolve(folder).resolve(COMMAND_ICON_FILE), scale,
                            this.output.resolve(this.set.getCommandIcon(quality)));
            writeSheetFrame(this.art.resolve(WIREFRAME_FOLDER).resolve(folder).resolve(WIREFRAME_FILE), scale,
                            this.output.resolve(this.set.getWireframe(quality)));
            copyClips(portraits, ScrCrossoverArtFiles.IDLE_CLIP_NAME, ScrCrossoverArtFiles.IDLE_CLIP_COUNT,
                      this.output.resolve(this.set.getIdleClips(quality)));
            copyClips(portraits, ScrCrossoverArtFiles.TALKING_CLIP_NAME, ScrCrossoverArtFiles.TALKING_CLIP_COUNT,
                      this.output.resolve(this.set.getTalkingClips(quality)));
        }

        Path sdWireframes = this.art.resolve(WIREFRAME_FOLDER).resolve(ScrCrossoverArtFiles.getQualityFolder(AssetResolver.AssetSet.SD));

        writeSheetFrame(sdWireframes.resolve(GROUP_WIREFRAME_FILE), 1, this.output.resolve(this.set.getGroupWireframe()));
        writeSheetFrame(sdWireframes.resolve(TRANSPORT_WIREFRAME_FILE), 1, this.output.resolve(this.set.getTransportWireframe()));
    }

    /**
     * Reads the files of one baked frame and adds it to the SD images and to the unit's and the shadow's frames.
     */
    private void convertFrame(@NotNull Path folder, @NotNull BakedAnimation animation, int frame, int direction, int frameSet, boolean emissive,
                              @NotNull RgbaImage sdColors, @NotNull RgbaImage sdMasks,
                              @NotNull List<@NotNull CanvasFrame> mainFrames, @NotNull List<@NotNull CanvasFrame> shadowFrames) throws IOException {
        String name = String.format(Locale.ROOT, FRAME_NAME, direction, frame);
        RgbaImage color = this.readFrame(folder, name, "", animation);
        RgbaImage mask = this.readFrame(folder, name, MASK_SUFFIX, animation);
        RgbaImage shadow = this.readFrame(folder, name, SHADOW_SUFFIX, animation);
        RgbaImage bright = this.readFrame(folder, name, "_" + BRIGHT, animation);
        Map<String, RgbaImage> layers = new LinkedHashMap<>();
        List<RgbaImage> visible = new ArrayList<>(List.of(color, bright));

        tintTeamArea(color, mask);
        tintTeamArea(bright, mask);
        strengthenShadow(shadow);

        layers.put(DIFFUSE, color);
        layers.put(BRIGHT, bright);
        layers.put(TEAM_COLOR, mask);

        if (emissive) {
            RgbaImage glow = animation.hasEmissiveLayer(frame) ? this.readFrame(folder, name, "_" + EMISSIVE, animation)
                                                               : RgbaImage.fill(animation.getFrameWidth(), animation.getFrameHeight(), TRANSPARENT);

            layers.put(EMISSIVE, glow);
            visible.add(glow);
        }

        layers.put(NORMAL, clearChannels(this.readFrame(folder, name, "_" + NORMAL, animation), RgbaImage.GREEN, RgbaImage.BLUE));
        layers.put(SPECULAR, this.readFrame(folder, name, "_" + SPECULAR, animation));
        layers.put(AO_DEPTH, clearChannels(this.readFrame(folder, name, "_" + AO_DEPTH, animation), RgbaImage.RED, RgbaImage.BLUE));

        mainFrames.add(this.cutFrame(layers, this.findBox(visible.toArray(RgbaImage[]::new)), animation, this.groundYUnits));
        shadowFrames.add(this.cutFrame(Map.of(DIFFUSE, shadow), this.findBox(shadow), animation, this.shadowGroundYUnits));

        // The SD frame: the baked frame scaled down to SD on its canvas cell, with the ground point at the canvas's ground point.
        int canvasSize = this.set.getSdCanvasSize();
        int cellLeft = direction * canvasSize + canvasSize / 2 - animation.getPivotX() / this.bakedScale;
        int cellTop = frameSet * canvasSize + canvasSize / 2 + this.set.getGroundOffset() - animation.getPivotY() / this.bakedScale;
        int shadowTop = cellTop - this.set.getShadowOffset();
        int cellBottom = (frameSet + 1) * canvasSize;
        RgbaImage sdMask = mask.scaleDown(this.bakedScale, false);
        RgbaImage sdShadow = shadow.scaleDown(this.bakedScale, false);

        sdColors.draw(color.scaleDown(this.bakedScale, true), cellLeft, cellTop);

        for (int y = 0; y < sdMask.getHeight(); y++) {
            for (int x = 0; x < sdMask.getWidth(); x++) {
                sdMasks.set(cellLeft + x, cellTop + y, ScrCrossoverArtFiles.TEAM_MASK_CHANNEL, sdMask.get(x, y, RgbaImage.RED));
            }
        }

        // The SD shadow: its ground point at the shadow image's position, the cell's ground point raised by the shadow offset.
        for (int y = 0; y < sdShadow.getHeight(); y++) {
            if (shadowTop + y < frameSet * canvasSize || shadowTop + y >= cellBottom) {
                continue;
            }

            for (int x = 0; x < sdShadow.getWidth(); x++) {
                sdMasks.set(cellLeft + x, shadowTop + y, ScrCrossoverArtFiles.SHADOW_MASK_CHANNEL, sdShadow.get(x, y, RgbaImage.ALPHA));
            }
        }
    }

    private @NotNull RgbaImage readFrame(@NotNull Path folder, @NotNull String name, @NotNull String suffix,
                                         @NotNull BakedAnimation animation) throws IOException {
        Path file = folder.resolve(name + suffix + PNG_EXTENSION);
        RgbaImage image = RgbaImage.read(file);

        if (image.getWidth() != animation.getFrameWidth() || image.getHeight() != animation.getFrameHeight() ||
            animation.getFrameWidth() % this.bakedScale != 0 || animation.getFrameHeight() % this.bakedScale != 0 ||
            animation.getPivotX() % this.bakedScale != 0 || animation.getPivotY() % this.bakedScale != 0) {
            throw Exceptions.newIOException("{0} does not have the frame size of the animation {1}, or that size or its pivot is not a multiple of {2}.",
                                            file, animation.getName(), this.bakedScale);
        }

        return image;
    }

    /**
     * Finds the box of the pixels that are visible in any of the images, widened to multiples of the frame alignment: left, top, width, height.
     * A frame without visible pixels gets the aligned box around its first pixel.
     */
    private int @NotNull [] findBox(@NotNull RgbaImage @NotNull ... images) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = 0;
        int bottom = 0;

        for (RgbaImage image : images) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (image.get(x, y, RgbaImage.ALPHA) != 0) {
                        left   = Math.min(left, x);
                        top    = Math.min(top, y);
                        right  = Math.max(right, x + 1);
                        bottom = Math.max(bottom, y + 1);
                    }
                }
            }
        }

        if (left == Integer.MAX_VALUE) {
            return new int[]{0, 0, this.frameAlignment, this.frameAlignment};
        }

        left = Math.floorDiv(left, this.frameAlignment) * this.frameAlignment;
        top  = Math.floorDiv(top, this.frameAlignment) * this.frameAlignment;

        return new int[]{left, top, alignUp(right - left, this.frameAlignment), alignUp(bottom - top, this.frameAlignment)};
    }

    /**
     * Cuts every layer of a frame to its box and finds the box's offset in the canvas: the ground point (the pivot) sits at a canvas row, the
     * canvas's ground point for the unit's frames and the shadow image's position for the shadow's.
     */
    private @NotNull CanvasFrame cutFrame(@NotNull Map<@NotNull String, @NotNull RgbaImage> layers, int @NotNull [] box,
                                          @NotNull BakedAnimation animation, int groundY) throws IOException {
        int canvasX = this.groundXUnits + (box[BOX_LEFT] - animation.getPivotX()) * this.unitsPerBakedTexel;
        int canvasY = groundY + (box[BOX_TOP] - animation.getPivotY()) * this.unitsPerBakedTexel;

        if (canvasX < 0 || canvasY < 0 || canvasX + box[BOX_WIDTH] * this.unitsPerBakedTexel > this.canvasUnits ||
            canvasY + box[BOX_HEIGHT] * this.unitsPerBakedTexel > this.canvasUnits) {
            throw Exceptions.newIOException("A frame of the animation {0} does not fit the canvas of {1} units.",
                                            animation.getName(), this.canvasUnits);
        }

        Map<String, RgbaImage> cut = new LinkedHashMap<>();

        for (Map.Entry<String, RgbaImage> layer : layers.entrySet()) {
            cut.put(layer.getKey(),
                    layer.getValue().cut(box[BOX_LEFT], box[BOX_TOP], box[BOX_WIDTH], box[BOX_HEIGHT], getDefaultValue(layer.getKey())));
        }

        return new CanvasFrame(cut, canvasX, canvasY);
    }

    /**
     * Encodes frames as a single-entry ANIM file of a scale: the frames packed in rows into one atlas per layer (DXT5), each frame record with
     * its atlas position, canvas offset and size in frame units. The atlas is {@link #ATLAS_WIDTH} HD texels wide at the scale, and twice as wide
     * as often as its packed height exceeds {@link #MAXIMUM_ATLAS_SIZE}.
     */
    private byte @NotNull [] encodeAnimation(@NotNull List<@NotNull CanvasFrame> frames, @NotNull List<@NotNull String> layerNames, int scale) {
        int factor = this.bakedScale / scale;
        int unitsPerTexel = ImageFileWriterUtils.ANIM_UNIT_SCALE / scale;
        int atlasWidth = ATLAS_WIDTH * scale / HD_SCALE;
        List<int[]> positions = packAtlas(frames, layerNames.getFirst(), factor, atlasWidth);

        while (positions.getLast()[POSITION_Y] > MAXIMUM_ATLAS_SIZE && atlasWidth < MAXIMUM_ATLAS_SIZE) {
            atlasWidth *= 2;
            positions = packAtlas(frames, layerNames.getFirst(), factor, atlasWidth);
        }

        int atlasHeight = positions.getLast()[POSITION_Y];
        Map<String, byte[]> textures = new LinkedHashMap<>();

        for (String layer : layerNames) {
            RgbaImage atlas = RgbaImage.fill(atlasWidth, atlasHeight, getDefaultValue(layer));
            boolean coverage = COVERAGE_LAYERS.contains(layer);

            for (int frame = 0; frame < frames.size(); frame++) {
                RgbaImage image = frames.get(frame).layers.get(layer);

                atlas.draw(factor == 1 ? image : image.scaleDown(factor, coverage),
                           positions.get(frame)[POSITION_X], positions.get(frame)[POSITION_Y]);
            }

            textures.put(layer, ImageFileWriterUtils.encodeDxt5(atlasWidth, atlasHeight, atlas.getPixels(), coverage));
        }

        List<int[]> records = new ArrayList<>();

        for (int frame = 0; frame < frames.size(); frame++) {
            CanvasFrame canvasFrame = frames.get(frame);
            RgbaImage first = canvasFrame.layers.get(layerNames.getFirst());

            records.add(new int[]{positions.get(frame)[POSITION_X] * unitsPerTexel, positions.get(frame)[POSITION_Y] * unitsPerTexel,
                                  canvasFrame.canvasX, canvasFrame.canvasY,
                                  first.getWidth() / factor * unitsPerTexel, first.getHeight() / factor * unitsPerTexel,
                                  ImageFileWriterUtils.ANIM_FRAME_CANVAS_PLACED});
        }

        return ImageFileWriterUtils.encodeAnim(scale, this.canvasUnits, this.canvasUnits, records, textures);
    }
}
