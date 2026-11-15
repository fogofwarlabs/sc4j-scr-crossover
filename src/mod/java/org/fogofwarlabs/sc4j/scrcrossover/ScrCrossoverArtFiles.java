package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Reads the added units' graphics from the crossover's jar and makes the SD frames that the jar cannot hold. The crossover's art converter writes the
 * files of every art set ({@link ScrCrossoverArtSet}) at build time from its baked frames: the HD frame files of the unit and of its shadow, the SD
 * frames in true color with their team color and shadow masks, and the animations ({@link ScrCrossoverAnimation}); and the unit's interface art: its
 * command icon and wireframes as single-frame {@code .dds.grp} sheets, and its portrait clips, per quality.
 * <p>
 * SD frames hold palette indices of the installation's tileset palettes, which the repository and the jar may not hold, so this class makes the
 * two GRP files of each set ({@link ScrCrossoverArtSet#getMainFrames()} and {@link ScrCrossoverArtSet#getShadowFrames()}) when they are read (the
 * added images load their frames once, {@code ScrModGraphics.loadFrames}, so the bytes are not kept here): a pixel
 * with alpha of at least {@link #OPAQUE_ALPHA} becomes a team color index {@code 8..15} by its brightness where the team mask is set, and
 * otherwise the nearest color among the indices that every tileset palette holds with the same color and that no palette cycles
 * ({@code 16..247}). A shadow pixel is set where the shadow mask reaches {@link #SHADOW_THRESHOLD}. The same installation always gives the same
 * frames.
 * <p>
 * Every frame of a set is placed on one canvas of the set's SD canvas size (HD: four times that in frame units) centered on the image position,
 * with the unit's ground point the set's ground offset below the image position.
 */
public final class ScrCrossoverArtFiles implements ScrModFiles {
    /**
     * The name prefix of the idle clips: clip {@code n} is this, {@code n} and {@code .webm}.
     */
    public static final @NotNull String IDLE_CLIP_NAME = "Fid0";
    /**
     * The name prefix of the talking clips.
     */
    public static final @NotNull String TALKING_CLIP_NAME = "Tlk0";
    /**
     * The number of idle clips.
     */
    public static final int IDLE_CLIP_COUNT = 4;
    /**
     * The number of talking clips.
     */
    public static final int TALKING_CLIP_COUNT = 3;

    /**
     * The directions of a frame set, north through south clockwise; the renderer mirrors the other fifteen.
     */
    public static final int DIRECTIONS = 17;
    /**
     * The channel of the SD masks ({@link ScrCrossoverArtSet#getSdMasks()}) that holds the team color mask.
     */
    public static final int TEAM_MASK_CHANNEL = 0;
    /**
     * The channel of the SD masks that holds the shadow's strength.
     */
    public static final int SHADOW_MASK_CHANNEL = 1;
    /**
     * The lowest alpha of an opaque SD pixel.
     */
    public static final int OPAQUE_ALPHA = 128;
    /**
     * The lowest team color mask value of a team color SD pixel.
     */
    public static final int TEAM_THRESHOLD = 128;
    /**
     * The lowest shadow strength of an SD shadow pixel.
     */
    public static final int SHADOW_THRESHOLD = 128;

    private static final int FIRST_TEAM_INDEX = 8;
    private static final int TEAM_SHADES = 8;
    private static final int SHADOW_INDEX = 1;
    /**
     * The first index of the unit colors; {@code 0..15} are transparent, cycled or the team color.
     */
    private static final int FIRST_UNIT_INDEX = 16;
    /**
     * The end of the unit colors: tileset palettes cycle {@code 248..254}.
     */
    private static final int UNIT_INDEX_END = 248;
    private static final int PALETTE_SIZE = 256;
    private static final int PALETTE_ENTRY_BYTES = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int RGBA_CHANNELS = 4;
    private static final int RED_CHANNEL = 0;
    private static final int GREEN_CHANNEL = 1;
    private static final int BLUE_CHANNEL = 2;
    private static final int ALPHA_CHANNEL = 3;
    private static final int RED_WEIGHT = 2;
    private static final int GREEN_WEIGHT = 4;
    private static final int BLUE_WEIGHT = 3;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final double RED_LUMINANCE = 0.299;
    private static final double GREEN_LUMINANCE = 0.587;
    private static final double BLUE_LUMINANCE = 0.114;

    /**
     * Returns the folder name of a quality in the art files: {@code sd}, {@code hd2} or {@code hd}.
     *
     * @param quality the asset quality
     * @return the folder name
     */
    public static @NotNull String getQualityFolder(AssetResolver.@NotNull AssetSet quality) {
        return quality.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds the unit colors: the indices {@code 16..247} whose color every palette holds.
     */
    private static int @NotNull [] findUnitColors(@NotNull List<byte @NotNull []> palettes) throws IOException {
        if (palettes.isEmpty()) {
            throw Exceptions.newIOException("The SD frames need at least one tileset palette.");
        }

        List<Integer> indices = new ArrayList<>();

        for (byte[] palette : palettes) {
            if (palette.length != PALETTE_SIZE * PALETTE_ENTRY_BYTES) {
                throw Exceptions.newIOException("A tileset palette holds {0} bytes, not {1}.", palette.length, PALETTE_SIZE * PALETTE_ENTRY_BYTES);
            }
        }

        for (int index = FIRST_UNIT_INDEX; index < UNIT_INDEX_END; index++) {
            int color = readPaletteColor(palettes.getFirst(), index);
            boolean shared = true;

            for (byte[] palette : palettes) {
                shared &= readPaletteColor(palette, index) == color;
            }

            if (shared) {
                indices.add(index);
            }
        }

        if (indices.isEmpty()) {
            throw Exceptions.newIOException("The tileset palettes share no unit color.");
        }

        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int readPaletteColor(byte @NotNull [] palette, int index) {
        int offset = index * PALETTE_ENTRY_BYTES;

        return (palette[offset + RED_CHANNEL] & BYTE_MASK) << RED_SHIFT | (palette[offset + GREEN_CHANNEL] & BYTE_MASK) << GREEN_SHIFT |
               palette[offset + BLUE_CHANNEL] & BYTE_MASK;
    }

    /**
     * Finds the team color index of a pixel by its brightness: {@code 8} is the brightest shade, {@code 15} the darkest.
     */
    private static int findTeamIndex(int @NotNull [] color) {
        double luminance = RED_LUMINANCE * color[RED_CHANNEL] + GREEN_LUMINANCE * color[GREEN_CHANNEL] + BLUE_LUMINANCE * color[BLUE_CHANNEL];

        return FIRST_TEAM_INDEX + (int) Math.round((BYTE_MASK - luminance) * (TEAM_SHADES - 1) / BYTE_MASK);
    }

    private static @NotNull Raster readImage(byte @NotNull [] contents, @NotNull String path, int bands, int canvasSize) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(contents));

        if (image == null || image.getRaster().getNumBands() != bands || image.getWidth() != DIRECTIONS * canvasSize ||
            image.getHeight() % canvasSize != 0) {
            throw Exceptions.newIOException("{0} is not an SD frame image of {1} bands.", path, bands);
        }

        return image.getRaster();
    }


    private final @NotNull ScrModFiles files;

    private final byte @NotNull [] palette;
    private final int @NotNull [] unitColors;
    private final @NotNull Map<@NotNull Integer, @NotNull Integer> nearestColors;

    /**
     * Creates a {@link ScrCrossoverArtFiles} instance over the files of the crossover's jar.
     *
     * @param files    the files of the jar
     * @param palettes the tileset palettes of the installation ({@code .wpe}: {@code 256} entries of red, green, blue and an unused byte)
     * @throws IOException if there is no palette, a palette has another size or the palettes share no unit color
     */
    public ScrCrossoverArtFiles(@NotNull ScrModFiles files, @NotNull List<byte @NotNull []> palettes) throws IOException {
        this.files = files;

        this.palette       = palettes.isEmpty() ? new byte[0] : palettes.getFirst().clone();
        this.unitColors    = findUnitColors(palettes);
        this.nearestColors = new HashMap<>();
    }

    /**
     * Reads a file: the SD frame files of every art set ({@link ScrCrossoverArtSet#getMainFrames()} and {@link ScrCrossoverArtSet#getShadowFrames()})
     * are made from the jar's SD images, every other file is the jar's.
     *
     * @param path the path relative to the jar root
     * @return the file contents
     * @throws IOException if the path is invalid or the file cannot be read or made
     */
    @Override
    public synchronized byte @NotNull [] readFile(@NotNull String path) throws IOException {
        ScrModFiles.checkPath(path);

        for (ScrCrossoverArtSet set : ScrCrossoverArtSet.values()) {
            boolean main = path.equals(set.getMainFrames());

            if (main || path.equals(set.getShadowFrames())) {
                return this.createFrames(set, !main);
            }
        }

        return this.files.readFile(path);
    }

    private byte @NotNull [] createFrames(@NotNull ScrCrossoverArtSet set, boolean shadow) throws IOException {
        int canvasSize = set.getSdCanvasSize();
        Raster colors = readImage(this.files.readFile(set.getSdColors()), set.getSdColors(), RGBA_CHANNELS, canvasSize);
        Raster masks = readImage(this.files.readFile(set.getSdMasks()), set.getSdMasks(), RGBA_CHANNELS - 1, canvasSize);

        if (colors.getHeight() != masks.getHeight()) {
            throw Exceptions.newIOException("The SD colors and masks of {0} hold different frame counts.", set.getName());
        }

        List<byte[]> canvases = new ArrayList<>();
        int[] color = new int[RGBA_CHANNELS];
        int[] mask = new int[RGBA_CHANNELS - 1];

        for (int top = 0; top < colors.getHeight(); top += canvasSize) {
            for (int left = 0; left < colors.getWidth(); left += canvasSize) {
                byte[] canvas = new byte[canvasSize * canvasSize];

                for (int y = 0; y < canvasSize; y++) {
                    for (int x = 0; x < canvasSize; x++) {
                        masks.getPixel(left + x, top + y, mask);

                        if (shadow) {
                            canvas[y * canvasSize + x] = (byte) (mask[SHADOW_MASK_CHANNEL] >= SHADOW_THRESHOLD ? SHADOW_INDEX : 0);
                            continue;
                        }

                        colors.getPixel(left + x, top + y, color);

                        if (color[ALPHA_CHANNEL] >= OPAQUE_ALPHA) {
                            canvas[y * canvasSize + x] = (byte) (mask[TEAM_MASK_CHANNEL] >= TEAM_THRESHOLD ? findTeamIndex(color)
                                                                                                           : this.findNearestIndex(color));
                        }
                    }
                }

                canvases.add(canvas);
            }
        }

        return ImageFileWriterUtils.encodeGrp(canvasSize, canvasSize, canvases);
    }

    private int findNearestIndex(int @NotNull [] color) {
        int packed = color[RED_CHANNEL] << RED_SHIFT | color[GREEN_CHANNEL] << GREEN_SHIFT | color[BLUE_CHANNEL];
        Integer cached = this.nearestColors.get(packed);

        if (cached != null) {
            return cached;
        }

        int nearest = this.unitColors[0];
        long nearestDistance = Long.MAX_VALUE;

        for (int index : this.unitColors) {
            int entry = readPaletteColor(this.palette, index);
            long red = (entry >>> RED_SHIFT) - color[RED_CHANNEL];
            long green = (entry >>> GREEN_SHIFT & BYTE_MASK) - color[GREEN_CHANNEL];
            long blue = (entry & BYTE_MASK) - color[BLUE_CHANNEL];
            long distance = RED_WEIGHT * red * red + GREEN_WEIGHT * green * green + BLUE_WEIGHT * blue * blue;

            if (distance < nearestDistance) {
                nearest         = index;
                nearestDistance = distance;
            }
        }

        this.nearestColors.put(packed, nearest);

        return nearest;
    }
}
