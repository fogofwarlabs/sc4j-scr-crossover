package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.base.json.JsonWriterUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the overlay offsets of an art set ({@link ScrCrossoverArtSet#getOverlayOffsets()}): for every frame of the unit's frames, in the
 * order of its frame files (frame set times {@link ScrCrossoverArtFiles#DIRECTIONS} plus the direction), the offset of the overlay image's root from
 * the image position in SD pixels, x right and y down. The unit's script creates the overlay at it ({@code imgoluselo}); a mirrored direction
 * negates x, as for an installed overlay file.
 */
public final class ScrCrossoverOverlayOffsets {
    private static final @NotNull String FRAMES_FIELD = "frames";
    private static final int VALUES_PER_FRAME = 2;

    /**
     * Reads the offsets of a converted file.
     *
     * @param contents the file contents
     * @return per frame, the x and y offset
     * @throws IOException if the contents are not an offsets file
     */
    public static int @NotNull [] @NotNull [] read(byte @NotNull [] contents) throws IOException {
        if (!(JsonReader.parse(contents) instanceof Map<?, ?> root) || !(root.get(FRAMES_FIELD) instanceof List<?> frames)) {
            throw Exceptions.newIOException("The overlay offsets file has no {0} list.", FRAMES_FIELD);
        }

        int[][] offsets = new int[frames.size()][];

        for (int frame = 0; frame < offsets.length; frame++) {
            if (!(frames.get(frame) instanceof List<?> pair) || pair.size() != VALUES_PER_FRAME || !(pair.get(0) instanceof Number x) ||
                !(pair.get(1) instanceof Number y)) {
                throw Exceptions.newIOException("Overlay frame {0} is not an x and y offset.", frame);
            }

            offsets[frame] = new int[]{x.intValue(), y.intValue()};
        }

        return offsets;
    }

    /**
     * Writes offsets as the contents of a converted file.
     *
     * @param offsets per frame, the x and y offset
     * @return the file contents, UTF-8 JSON
     */
    public static byte @NotNull [] write(int @NotNull [] @NotNull [] offsets) {
        List<Object> frames = new ArrayList<>();

        for (int[] offset : offsets) {
            frames.add(List.of((long) offset[0], (long) offset[1]));
        }

        Map<String, Object> root = new LinkedHashMap<>();

        root.put(FRAMES_FIELD, frames);

        return JsonWriterUtils.write(root).getBytes(StandardCharsets.UTF_8);
    }

    private ScrCrossoverOverlayOffsets() {
    }
}
