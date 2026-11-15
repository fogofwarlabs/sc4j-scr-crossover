package org.fogofwarlabs.sc4j.scrcrossover.convert;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds an image as red, green, blue and alpha bytes per pixel, row-major, with the few operations the art converter needs: reading a PNG file by
 * its stored samples (no color space conversion), cutting a rectangle, scaling down by a whole factor, drawing another image and writing a PNG file.
 */
final class RgbaImage {
    /**
     * The bytes per pixel: red, green, blue and alpha.
     */
    static final int CHANNELS = 4;
    static final int RED = 0;
    static final int GREEN = 1;
    static final int BLUE = 2;
    static final int ALPHA = 3;
    static final int MAXIMUM = 0xFF;

    private static final int GRAY_BANDS = 1;
    private static final int GRAY_ALPHA_BANDS = 2;
    private static final int RGB_BANDS = 3;
    private static final int PACKED_RED_SHIFT = 24;
    private static final int PACKED_GREEN_SHIFT = 16;
    private static final int PACKED_BLUE_SHIFT = 8;

    /**
     * Reads a PNG file: a gray image gives gray color, an image without alpha gives opaque pixels.
     *
     * @param file the PNG file
     * @return the image
     * @throws IOException if the file cannot be read or is not an image of one to four bands
     */
    static @NotNull RgbaImage read(@NotNull Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());

        if (image == null) {
            throw Exceptions.newIOException("Not an image: {0}", file);
        }

        Raster raster = image.getRaster();
        int bands = raster.getNumBands();

        if (bands < GRAY_BANDS || bands > CHANNELS) {
            throw Exceptions.newIOException("{0} has {1} bands.", file, bands);
        }

        int[] samples = raster.getPixels(0, 0, image.getWidth(), image.getHeight(), (int[]) null);
        RgbaImage result = new RgbaImage(image.getWidth(), image.getHeight());

        for (int pixel = 0; pixel < image.getWidth() * image.getHeight(); pixel++) {
            int source = pixel * bands;
            int target = pixel * CHANNELS;
            boolean gray = bands <= GRAY_ALPHA_BANDS;

            for (int channel = 0; channel < ALPHA; channel++) {
                result.pixels[target + channel] = (byte) samples[gray ? source : source + channel];
            }

            result.pixels[target + ALPHA] = (byte) (bands == GRAY_ALPHA_BANDS ? samples[source + 1]
                                                                              : bands == CHANNELS ? samples[source + ALPHA] : MAXIMUM);
        }

        return result;
    }

    /**
     * Creates a {@link RgbaImage} instance of one color.
     *
     * @param width  the width
     * @param height the height
     * @param rgba   the color, packed {@code 0xRRGGBBAA}
     * @return the created {@link RgbaImage} instance
     */
    static @NotNull RgbaImage fill(int width, int height, int rgba) {
        RgbaImage image = new RgbaImage(width, height);

        for (int pixel = 0; pixel < width * height; pixel++) {
            image.pixels[pixel * CHANNELS + RED]   = (byte) (rgba >>> PACKED_RED_SHIFT);
            image.pixels[pixel * CHANNELS + GREEN] = (byte) (rgba >>> PACKED_GREEN_SHIFT);
            image.pixels[pixel * CHANNELS + BLUE]  = (byte) (rgba >>> PACKED_BLUE_SHIFT);
            image.pixels[pixel * CHANNELS + ALPHA] = (byte) rgba;
        }

        return image;
    }


    private final int width;
    private final int height;
    private final byte @NotNull [] pixels;

    private RgbaImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw Exceptions.newIllegalArgumentException("Invalid image size: {0} by {1}", width, height);
        }

        this.width  = width;
        this.height = height;
        this.pixels = new byte[width * height * CHANNELS];
    }

    /**
     * @return the width
     */
    int getWidth() {
        return this.width;
    }

    /**
     * @return the height
     */
    int getHeight() {
        return this.height;
    }

    /**
     * @return the pixels, {@link #CHANNELS} bytes each, row-major; the array is the image's own
     */
    byte @NotNull [] getPixels() {
        return this.pixels;
    }

    /**
     * Returns a channel of a pixel.
     *
     * @param x       the column
     * @param y       the row
     * @param channel the channel, {@link #RED} through {@link #ALPHA}
     * @return the value, {@code 0..255}
     */
    int get(int x, int y, int channel) {
        return this.pixels[(y * this.width + x) * CHANNELS + channel] & MAXIMUM;
    }

    /**
     * Sets a channel of a pixel.
     *
     * @param x       the column
     * @param y       the row
     * @param channel the channel, {@link #RED} through {@link #ALPHA}
     * @param value   the value, {@code 0..255}
     */
    void set(int x, int y, int channel, int value) {
        this.pixels[(y * this.width + x) * CHANNELS + channel] = (byte) value;
    }

    /**
     * Cuts a rectangle; the parts outside the image take a fill color.
     *
     * @param left   the left column, may be outside the image
     * @param top    the top row, may be outside the image
     * @param width  the width
     * @param height the height
     * @param rgba   the fill color, packed {@code 0xRRGGBBAA}
     * @return the cut image
     */
    @NotNull RgbaImage cut(int left, int top, int width, int height, int rgba) {
        RgbaImage result = fill(width, height, rgba);

        result.draw(this, -left, -top);

        return result;
    }

    /**
     * Draws another image over this one, replacing the pixels it covers.
     *
     * @param source the image to draw
     * @param left   the column of the source's left edge
     * @param top    the row of the source's top edge
     */
    void draw(@NotNull RgbaImage source, int left, int top) {
        for (int y = Math.max(0, top); y < Math.min(this.height, top + source.height); y++) {
            int firstX = Math.max(0, left);
            int endX = Math.min(this.width, left + source.width);

            if (endX > firstX) {
                System.arraycopy(source.pixels, ((y - top) * source.width + firstX - left) * CHANNELS,
                                 this.pixels, (y * this.width + firstX) * CHANNELS,
                                 (endX - firstX) * CHANNELS);
            }
        }
    }

    /**
     * Scales the image down by a whole factor, averaging each square of pixels. With {@code premultiplied} the color is averaged weighted by
     * alpha (transparent pixels add no color), otherwise every channel is a plain average.
     *
     * @param factor        the factor, dividing the width and height
     * @param premultiplied whether the color is weighted by alpha
     * @return the scaled image
     * @throws IllegalArgumentException if the factor does not divide the size
     */
    @NotNull RgbaImage scaleDown(int factor, boolean premultiplied) {
        if (factor <= 0 || this.width % factor != 0 || this.height % factor != 0) {
            throw Exceptions.newIllegalArgumentException("{0} by {1} cannot be scaled down by {2}.", this.width, this.height, factor);
        }

        RgbaImage result = new RgbaImage(this.width / factor, this.height / factor);
        int samples = factor * factor;

        for (int y = 0; y < result.height; y++) {
            for (int x = 0; x < result.width; x++) {
                long[] sums = new long[CHANNELS];

                for (int sourceY = y * factor; sourceY < (y + 1) * factor; sourceY++) {
                    for (int sourceX = x * factor; sourceX < (x + 1) * factor; sourceX++) {
                        int alpha = this.get(sourceX, sourceY, ALPHA);

                        for (int channel = 0; channel < ALPHA; channel++) {
                            sums[channel] += (long) this.get(sourceX, sourceY, channel) * (premultiplied ? alpha : 1);
                        }

                        sums[ALPHA] += alpha;
                    }
                }

                for (int channel = 0; channel < ALPHA; channel++) {
                    long divisor = premultiplied ? sums[ALPHA] : samples;

                    result.set(x, y, channel, divisor == 0 ? 0 : (int) ((sums[channel] + divisor / 2) / divisor));
                }

                result.set(x, y, ALPHA, (int) ((sums[ALPHA] + samples / 2) / samples));
            }
        }

        return result;
    }

    /**
     * Writes the image as a PNG file of red, green and blue, with alpha if {@code alpha}.
     *
     * @param file  the PNG file
     * @param alpha whether the file keeps the alpha channel
     * @throws IOException if the file cannot be written
     */
    void writePng(@NotNull Path file, boolean alpha) throws IOException {
        BufferedImage image = new BufferedImage(this.width, this.height, alpha ? BufferedImage.TYPE_4BYTE_ABGR : BufferedImage.TYPE_3BYTE_BGR);
        WritableRaster raster = image.getRaster();
        int bands = alpha ? CHANNELS : RGB_BANDS;
        int[] samples = new int[this.width * this.height * bands];

        for (int pixel = 0; pixel < this.width * this.height; pixel++) {
            for (int channel = 0; channel < bands; channel++) {
                samples[pixel * bands + channel] = this.pixels[pixel * CHANNELS + channel] & MAXIMUM;
            }
        }

        raster.setPixels(0, 0, this.width, this.height, samples);
        Files.createDirectories(file.toAbsolutePath().getParent());

        if (!ImageIO.write(image, "png", file.toFile())) {
            throw Exceptions.newIOException("No PNG writer for {0}", file);
        }
    }
}
