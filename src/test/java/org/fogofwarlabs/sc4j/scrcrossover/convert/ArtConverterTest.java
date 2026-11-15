package org.fogofwarlabs.sc4j.scrcrossover.convert;

import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsGrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverTestResources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Tests the added units' graphics that the art converter wrote into the built jar of SC:R Crossover for every art set ({@code ScrCrossoverArtSet},
 * each with a {@code metadata.json} in its folder of {@code third-party}) against the set's baked frames: the frame files of the unit or overlay (six
 * layers, seven with an emissive frame) and of a unit's shadow hold one canvas-placed frame per direction of every frame set, inside the canvas and
 * aligned for block compression, for the baked quality and every lower HD quality; the SD images hold one canvas per frame; the animations
 * follow the metadata's order, play order, durations and action frames; a unit with an overlay has one overlay offset per frame; and the jar
 * carries each unit set's NOTICE and license files but none of the baked frames.
 */
final class ArtConverterTest {
    private static final @NotNull String THIRD_PARTY = "third-party/";
    private static final @NotNull String METADATA = "metadata.json";
    private static final @NotNull List<@NotNull String> MAIN_LAYERS = List.of("diffuse", "bright", "teamcolor", "emissive", "normal", "specular",
                                                                              "ao_depth");
    private static final @NotNull String EMISSIVE = "emissive";
    private static final @NotNull List<@NotNull String> HD_QUALITIES = List.of("hd", "hd2");
    private static final @NotNull List<@NotNull String> QUALITIES = List.of("sd", "hd2", "hd");
    private static final @NotNull List<@NotNull String> CLIPS = List.of("Fid00", "Fid01", "Fid02", "Fid03", "Tlk00", "Tlk01", "Tlk02");
    private static final @NotNull List<@NotNull String> UNIT_SETS = List.of("unity-chan", "myriarch");
    /**
     * The overlay sets (an overlay image's frames, no shadow and no interface art) and the unit set whose overlay offsets place each.
     */
    private static final @NotNull Map<@NotNull String, @NotNull String> OVERLAY_SETS = Map.of("myriarch/fire", "myriarch");
    private static final @NotNull String OVERLAY_OFFSETS = "overlay-offsets.json";
    private static final int DIRECTIONS = 17;
    private static final int UNITS_PER_SD_PIXEL = 4;
    private static final int CANVAS_PLACED = 1;
    private static final int HD2_BLOCK_UNITS = 8;
    private static final int GAME_FRAME_MILLISECONDS = 42;
    private static final int MAXIMUM_GRP_SIZE = 255;
    /**
     * The folder depth of a set's metadata under the art folder: a unit set's folder or an overlay set's folder inside it.
     */
    private static final int METADATA_DEPTH = 3;
    /**
     * The bands of the SD colors (red, green, blue and alpha) and of the SD masks, and the channels of a decoded texture.
     */
    private static final int COLOR_BANDS = 4;
    private static final int MASK_BANDS = 3;
    private static final int RED_CHANNEL = 0;
    private static final int GREEN_CHANNEL = 1;
    private static final int BLUE_CHANNEL = 2;

    static @NotNull Stream<@NotNull String> listArtSets() {
        return Stream.concat(UNIT_SETS.stream(), OVERLAY_SETS.keySet().stream()).sorted();
    }

    static @NotNull Stream<@NotNull String> listUnitSets() {
        return UNIT_SETS.stream();
    }

    /**
     * Checks that channels of a decoded layer texture are zero everywhere.
     */
    private static void checkClearChannels(@Nullable DdsFile texture, @NotNull String layer, int @NotNull ... channels) {
        Assertions.assertNotNull(texture, layer);

        byte[] pixels = texture.decodeRgba();

        for (int pixel = 0; pixel < pixels.length; pixel += ImageFileWriterUtils.RGBA_BYTES) {
            for (int channel : channels) {
                if (pixels[pixel + channel] != 0) {
                    Assertions.fail(layer + ": channel " + channel + " of pixel " + pixel / ImageFileWriterUtils.RGBA_BYTES + " is not zero");
                }
            }
        }
    }

    /**
     * Checks a converted interface sheet: one frame of the source image's size with the texture scale in its version word.
     */
    private static void checkSheet(byte @NotNull [] contents, @NotNull Path source, int scale) throws IOException {
        DdsGrpFile sheet = DdsGrpFile.parse(contents);
        BufferedImage image = ImageIO.read(source.toFile());

        Assertions.assertEquals(1, sheet.getFrameCount(), source.toString());
        Assertions.assertEquals(ImageFileWriterUtils.DDS_GRP_SCALED_VERSION | scale, sheet.getVersion(), source.toString());
        Assertions.assertEquals(image.getWidth(), sheet.getFrameWidth(0), source.toString());
        Assertions.assertEquals(image.getHeight(), sheet.getFrameHeight(0), source.toString());
        Assertions.assertEquals(image.getWidth(), sheet.getFrame(0).getWidth(), source.toString());
    }

    /**
     * Checks a frame file: its scale, layers and frame count, and that every frame is canvas-placed inside the canvas with a position and size
     * in whole compressed blocks of the HD2 texture.
     */
    private static void checkAnimation(byte @NotNull [] contents, int scale, @NotNull List<@NotNull String> layers, int frameCount,
                                       int canvasUnits) throws IOException {
        AnimFile file = AnimFile.parse(contents);
        AnimFile.Entry entry = file.getEntry(0);
        List<String> names = new ArrayList<>();

        for (int layer = 0; layer < file.getLayerCount(); layer++) {
            names.add(file.getLayerName(layer));
        }

        Assertions.assertEquals(scale, file.getScale());
        Assertions.assertEquals(layers, names);
        Assertions.assertEquals(1, file.getEntryCount());
        Assertions.assertEquals(frameCount, entry.getFrameCount());
        Assertions.assertEquals(canvasUnits, entry.getWidth());
        Assertions.assertEquals(canvasUnits, entry.getHeight());

        for (int frame = 0; frame < frameCount; frame++) {
            Assertions.assertEquals(CANVAS_PLACED, entry.getFrameFlags(frame));
            Assertions.assertTrue(entry.getFrameXOffset(frame) >= 0 && entry.getFrameXOffset(frame) + entry.getFrameWidth(frame) <= canvasUnits);
            Assertions.assertTrue(entry.getFrameYOffset(frame) >= 0 && entry.getFrameYOffset(frame) + entry.getFrameHeight(frame) <= canvasUnits);
            Assertions.assertEquals(0, entry.getFrameX(frame) % HD2_BLOCK_UNITS);
            Assertions.assertEquals(0, entry.getFrameY(frame) % HD2_BLOCK_UNITS);
            Assertions.assertEquals(0, entry.getFrameWidth(frame) % HD2_BLOCK_UNITS);
            Assertions.assertEquals(0, entry.getFrameHeight(frame) % HD2_BLOCK_UNITS);
        }
    }

    private static @NotNull String getGraphics(@NotNull String set) {
        return THIRD_PARTY + set + "/graphics/";
    }

    private static boolean hasEmissiveFrame(@NotNull Map<?, ?> metadata) {
        return ((Map<?, ?>) metadata.get("animations")).values().stream()
                                                       .anyMatch(animation -> ((Map<?, ?>) animation).get("emissiveFrames") != null);
    }

    private static int readFrameSetCount(@NotNull Map<?, ?> metadata) {
        int frameSets = 0;

        for (Object animation : ((Map<?, ?>) metadata.get("animations")).values()) {
            frameSets += ((Long) ((Map<?, ?>) animation).get("frameCount")).intValue();
        }

        return frameSets;
    }

    private static @NotNull Map<?, ?> readMetadata(@NotNull String set) throws IOException {
        return parseObject(Files.readAllBytes(ScrCrossoverTestResources.getArtFolder().resolve(set).resolve(METADATA)));
    }

    private static @NotNull Map<?, ?> parseObject(byte @NotNull [] contents) throws IOException {
        return Assertions.assertInstanceOf(Map.class, JsonReader.parse(contents));
    }

    private static byte @NotNull [] readEntry(@NotNull JarFile jar, @NotNull String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);

        Assertions.assertNotNull(entry, name);

        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }


    @Test
    void every_art_set_of_the_crossover_is_converted() throws IOException {
        List<String> folders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(ScrCrossoverTestResources.getArtFolder(), METADATA_DEPTH)) {
            files.filter(file -> file.getFileName().toString().equals(METADATA))
                 .forEach(file -> folders.add(ScrCrossoverTestResources.getArtFolder().relativize(file.getParent()).toString().replace('\\', '/')));
        }

        Assertions.assertEquals(listArtSets().toList(), folders.stream().sorted().toList());
    }

    @ParameterizedTest
    @MethodSource("listArtSets")
    void the_unit_frame_files_hold_every_baked_frame_on_the_canvas(@NotNull String set) throws IOException {
        Map<?, ?> metadata = readMetadata(set);
        int frameCount = readFrameSetCount(metadata) * DIRECTIONS;
        String bakedQuality = (String) metadata.get("quality");
        List<String> layers = hasEmissiveFrame(metadata) ? MAIN_LAYERS : MAIN_LAYERS.stream().filter(layer -> !layer.equals(EMISSIVE)).toList();
        List<String> qualities = HD_QUALITIES.subList(HD_QUALITIES.indexOf(bakedQuality), HD_QUALITIES.size());
        String graphics = getGraphics(set);

        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            AnimFile.Entry baked = AnimFile.parse(readEntry(jar, graphics + "main-" + bakedQuality + ".anim")).getEntry(0);

            Assertions.assertTrue(baked.getWidth() / UNITS_PER_SD_PIXEL <= MAXIMUM_GRP_SIZE, set);

            for (String quality : HD_QUALITIES) {
                Assertions.assertEquals(qualities.contains(quality), jar.getJarEntry(graphics + "main-" + quality + ".anim") != null,
                                        set + " " + quality);
            }

            for (String quality : qualities) {
                int scale = quality.equals("hd") ? 4 : 2;
                AnimFile.Entry entry = AnimFile.parse(readEntry(jar, graphics + "main-" + quality + ".anim")).getEntry(0);

                checkAnimation(readEntry(jar, graphics + "main-" + quality + ".anim"), scale, layers, frameCount, baked.getWidth());

                if (UNIT_SETS.contains(set)) {
                    checkAnimation(readEntry(jar, graphics + "shadow-" + quality + ".anim"), scale, List.of("diffuse"), frameCount, baked.getWidth());
                } else {
                    Assertions.assertNull(jar.getJarEntry(graphics + "shadow-" + quality + ".anim"), set);
                }

                for (int frame = 0; frame < frameCount; frame++) {
                    Assertions.assertEquals(baked.getFrameXOffset(frame), entry.getFrameXOffset(frame));
                    Assertions.assertEquals(baked.getFrameYOffset(frame), entry.getFrameYOffset(frame));
                    Assertions.assertEquals(baked.getFrameWidth(frame), entry.getFrameWidth(frame));
                    Assertions.assertEquals(baked.getFrameHeight(frame), entry.getFrameHeight(frame));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("listArtSets")
    void the_lighting_layers_use_the_channel_layout_of_the_installed_frames(@NotNull String set) throws IOException {
        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            for (String quality : HD_QUALITIES) {
                JarEntry file = jar.getJarEntry(getGraphics(set) + "main-" + quality + ".anim");

                if (file == null) {
                    continue;
                }

                AnimFile animation = AnimFile.parse(readEntry(jar, file.getName()));
                AnimFile.Entry entry = animation.getEntry(0);

                // The renderer reads the normal's x from red and y from alpha, the AO from green and the depth from alpha.
                checkClearChannels(entry.getLayerTexture(animation.findLayer("normal")), file.getName() + " normal", GREEN_CHANNEL, BLUE_CHANNEL);
                checkClearChannels(entry.getLayerTexture(animation.findLayer("ao_depth")), file.getName() + " ao_depth", RED_CHANNEL, BLUE_CHANNEL);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("listArtSets")
    void the_sd_images_hold_one_canvas_per_frame(@NotNull String set) throws IOException {
        int frameSets = readFrameSetCount(readMetadata(set));
        String graphics = getGraphics(set);

        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            String bakedQuality = (String) readMetadata(set).get("quality");
            int canvas = AnimFile.parse(readEntry(jar, graphics + "main-" + bakedQuality + ".anim")).getEntry(0).getWidth() / UNITS_PER_SD_PIXEL;
            BufferedImage colors = ImageIO.read(new ByteArrayInputStream(readEntry(jar, graphics + "sd-colors.png")));
            BufferedImage masks = ImageIO.read(new ByteArrayInputStream(readEntry(jar, graphics + "sd-masks.png")));

            Assertions.assertEquals(DIRECTIONS * canvas, colors.getWidth());
            Assertions.assertEquals(frameSets * canvas, colors.getHeight());
            Assertions.assertEquals(COLOR_BANDS, colors.getRaster().getNumBands());
            Assertions.assertEquals(colors.getWidth(), masks.getWidth());
            Assertions.assertEquals(colors.getHeight(), masks.getHeight());
            Assertions.assertEquals(MASK_BANDS, masks.getRaster().getNumBands());
        }
    }

    @ParameterizedTest
    @MethodSource("listArtSets")
    void the_animations_follow_the_metadata(@NotNull String set) throws IOException {
        Map<?, ?> baked = (Map<?, ?>) readMetadata(set).get("animations");
        List<?> animations;

        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            animations = (List<?>) parseObject(readEntry(jar, getGraphics(set) + "animations.json")).get("animations");
        }

        List<String> names = new ArrayList<>();
        long firstFrameSet = 0;

        Assertions.assertNotNull(animations);
        Assertions.assertEquals(baked.size(), animations.size());

        for (Object entry : animations) {
            Map<?, ?> animation = (Map<?, ?>) entry;
            Map<?, ?> source = (Map<?, ?>) baked.get(animation.get("name"));
            List<?> durations = (List<?>) source.get("durationMs");
            List<?> waits = (List<?>) animation.get("waits");

            names.add((String) animation.get("name"));
            Assertions.assertEquals(firstFrameSet, animation.get("firstFrameSet"));
            Assertions.assertEquals(source.get("frameCount"), animation.get("frameCount"));
            Assertions.assertEquals(source.get("playOrder"), animation.get("playOrder"));
            Assertions.assertEquals(source.get("loop"), animation.get("loop"));
            Assertions.assertEquals(source.get("actionFrames") == null ? List.of() : source.get("actionFrames"), animation.get("actionFrames"));
            Assertions.assertEquals(durations.size(), waits.size());

            for (int frame = 0; frame < durations.size(); frame++) {
                Assertions.assertEquals(Math.max(1, Math.round((Long) durations.get(frame) / (float) GAME_FRAME_MILLISECONDS)),
                                        ((Long) waits.get(frame)).intValue());
            }

            firstFrameSet += (Long) animation.get("frameCount");
        }

        Assertions.assertEquals(new ArrayList<>(baked.keySet()), names);

        if (UNIT_SETS.contains(set)) {
            Assertions.assertFalse(((List<?>) ((Map<?, ?>) animations.get(names.indexOf("attack"))).get("actionFrames")).isEmpty(), set);
        }
    }

    @ParameterizedTest
    @MethodSource("listUnitSets")
    void a_unit_with_an_overlay_has_one_overlay_offset_per_frame(@NotNull String set) throws IOException {
        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            JarEntry file = jar.getJarEntry(getGraphics(set) + OVERLAY_OFFSETS);

            if (!OVERLAY_SETS.containsValue(set)) {
                Assertions.assertNull(file, set);

                return;
            }

            Assertions.assertNotNull(file, set);

            List<?> frames = (List<?>) parseObject(readEntry(jar, file.getName())).get("frames");

            Assertions.assertEquals(readFrameSetCount(readMetadata(set)) * DIRECTIONS, frames.size());

            for (Object frame : frames) {
                for (Object value : (List<?>) frame) {
                    long offset = (Long) value;

                    Assertions.assertTrue(offset >= Byte.MIN_VALUE && offset <= Byte.MAX_VALUE, set + ": " + frame);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("listUnitSets")
    void the_interface_art_is_converted_for_every_quality(@NotNull String set) throws IOException {
        Path art = ScrCrossoverTestResources.getArtFolder().resolve(set);
        String folder = THIRD_PARTY + set + "/";

        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile())) {
            for (String quality : QUALITIES) {
                int scale = quality.equals("sd") ? 1 : quality.equals("hd2") ? 2 : 4;

                checkSheet(readEntry(jar, folder + "interface/cmdicon-" + quality + ".dds.grp"),
                           art.resolve("icon").resolve(quality).resolve("cmdicon.png"), scale);
                checkSheet(readEntry(jar, folder + "interface/wirefram-" + quality + ".dds.grp"),
                           art.resolve("wireframe").resolve(quality).resolve("wirefram.png"), scale);

                for (String clip : CLIPS) {
                    Assertions.assertArrayEquals(Files.readAllBytes(art.resolve("portrait").resolve(quality).resolve(clip + ".webm")),
                                                 readEntry(jar, folder + "portrait/" + quality + "/" + clip + ".webm"), quality + "/" + clip);
                }
            }

            for (String view : List.of("grpwire", "tranwire")) {
                checkSheet(readEntry(jar, folder + "interface/" + view + "-sd.dds.grp"),
                           art.resolve("wireframe").resolve("sd").resolve(view + ".png"), 1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("listUnitSets")
    void the_jar_carries_the_notice_and_licenses_but_no_baked_frame(@NotNull String set) throws IOException {
        Path art = ScrCrossoverTestResources.getArtFolder().resolve(set);
        String folder = THIRD_PARTY + set + "/";

        try (JarFile jar = new JarFile(ScrCrossoverTestResources.getModJar().toFile());
             Stream<Path> licenses = Files.walk(art.resolve("license"))) {
            List<String> names = jar.stream().filter(entry -> !entry.isDirectory()).map(JarEntry::getName)
                                    .filter(name -> name.startsWith(folder)).toList();

            Assertions.assertArrayEquals(Files.readAllBytes(art.resolve("NOTICE.txt")), readEntry(jar, folder + "NOTICE.txt"));
            Assertions.assertTrue(names.stream().anyMatch(name -> name.startsWith(folder + "license/")), names::toString);

            for (Path license : licenses.filter(Files::isRegularFile).toList()) {
                String name = folder + art.relativize(license).toString().replace('\\', '/');

                Assertions.assertArrayEquals(Files.readAllBytes(license), readEntry(jar, name), name);
            }

            List<String> graphics = listArtSets().filter(other -> other.equals(set) || other.startsWith(set + "/")).map(ArtConverterTest::getGraphics)
                                                 .toList();

            for (String name : names) {
                Assertions.assertTrue(name.equals(folder + "NOTICE.txt") || name.startsWith(folder + "license/") ||
                                      graphics.stream().anyMatch(name::startsWith) ||
                                      name.startsWith(folder + "interface/") && name.endsWith(".dds.grp") ||
                                      name.startsWith(folder + "portrait/") && name.endsWith(".webm"),
                                      () -> "an art entry beyond the NOTICE, the licenses and the converted graphics: " + name);
                Assertions.assertFalse(name.endsWith(".png") && graphics.stream().noneMatch(name::startsWith) &&
                                       !name.startsWith(folder + "license/"), name);
                Assertions.assertFalse(graphics.stream().anyMatch(name::startsWith) && name.contains("/hd"), name);
            }
        }
    }
}
