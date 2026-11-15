package org.fogofwarlabs.sc4j.scrcrossover.convert;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Describes one baked animation of {@code metadata.json} (format {@value #FORMAT}): its frame count, the frame size and pivot (the ground point
 * under the unit, from the frame's top-left corner) at the quality of the baked frames, shared by every frame and layer, the play order, the
 * duration of each frame, the frames with an {@code emissive} layer and the frames at which the unit acts.
 */
final class BakedAnimation {
    /**
     * The format tag of the metadata file this class reads.
     */
    static final @NotNull String FORMAT = "sc4j-bake-intermediate 1";

    private static final @NotNull String FORMAT_FIELD = "format";
    private static final @NotNull String QUALITY_FIELD = "quality";
    private static final @NotNull String DIRECTIONS_FIELD = "directions";
    private static final @NotNull String COUNT_FIELD = "count";
    private static final @NotNull String ANIMATIONS_FIELD = "animations";
    private static final @NotNull String FRAME_COUNT_FIELD = "frameCount";
    private static final @NotNull String FRAME_SIZE_FIELD = "frameSize";
    private static final @NotNull String PIVOT_FIELD = "pivot";
    private static final @NotNull String PLAY_ORDER_FIELD = "playOrder";
    private static final @NotNull String DURATION_FIELD = "durationMs";
    private static final @NotNull String LOOP_FIELD = "loop";
    private static final @NotNull String HOLD_LAST_FRAME_FIELD = "holdLastFrame";
    private static final @NotNull String EMISSIVE_FRAMES_FIELD = "emissiveFrames";
    private static final @NotNull String ACTION_FRAMES_FIELD = "actionFrames";
    private static final int POINT_VALUES = 2;

    /**
     * Reads the animations of a metadata file in their order in the file.
     *
     * @param metadata   the metadata file
     * @param directions the direction count the frames must have
     * @param quality    the quality the frames must be baked at, its folder name ({@code hd} or {@code hd2}): the key of the frame sizes and pivots
     * @return the animations
     * @throws IOException if the file cannot be read, has another format, quality or direction count, or a value is missing or out of range
     */
    static @NotNull List<@NotNull BakedAnimation> readAll(@NotNull Path metadata, int directions, @NotNull String quality) throws IOException {
        if (!(JsonReader.parse(Files.readAllBytes(metadata)) instanceof Map<?, ?> root) || !FORMAT.equals(root.get(FORMAT_FIELD))) {
            throw Exceptions.newIOException("{0} is not a bake metadata file of format {1}.", metadata, FORMAT);
        }

        if (!quality.equals(root.get(QUALITY_FIELD))) {
            throw Exceptions.newIOException("The frames of {0} are not baked at the quality {1}.", metadata, quality);
        }

        if (readInteger(readObject(root, DIRECTIONS_FIELD), COUNT_FIELD) != directions) {
            throw Exceptions.newIOException("The baked frames have another direction count than {0}.", directions);
        }

        Map<?, ?> entries = readObject(root, ANIMATIONS_FIELD);
        List<BakedAnimation> animations = new ArrayList<>();

        for (Object key : entries.keySet()) {
            String name = String.valueOf(key);
            Map<?, ?> fields = readObject(entries, name);
            int[] size = readIntegers(readObject(fields, FRAME_SIZE_FIELD), quality);
            int[] pivot = readIntegers(readObject(fields, PIVOT_FIELD), quality);

            if (size.length != POINT_VALUES || pivot.length != POINT_VALUES) {
                throw Exceptions.newIOException("The animation {0} has no frame size or pivot.", name);
            }

            animations.add(new BakedAnimation(name, readInteger(fields, FRAME_COUNT_FIELD), size[0], size[1], pivot[0], pivot[1],
                                              readIntegers(fields, PLAY_ORDER_FIELD), readIntegers(fields, DURATION_FIELD),
                                              Boolean.TRUE.equals(fields.get(LOOP_FIELD)), Boolean.TRUE.equals(fields.get(HOLD_LAST_FRAME_FIELD)),
                                              readOptionalIntegers(fields, EMISSIVE_FRAMES_FIELD),
                                              readOptionalIntegers(fields, ACTION_FRAMES_FIELD)));
        }

        return animations;
    }

    private static @NotNull Map<?, ?> readObject(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        if (!(fields.get(field) instanceof Map<?, ?> object)) {
            throw Exceptions.newIOException("The bake metadata value {0} is not an object.", field);
        }

        return object;
    }

    private static int readInteger(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        if (!(fields.get(field) instanceof Long value) || value < 0 || value > Integer.MAX_VALUE) {
            throw Exceptions.newIOException("The bake metadata value {0} is not a count.", field);
        }

        return value.intValue();
    }

    private static int @NotNull [] readOptionalIntegers(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        return fields.get(field) == null ? new int[0] : readIntegers(fields, field);
    }

    private static int @NotNull [] readIntegers(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        if (!(fields.get(field) instanceof List<?> values)) {
            throw Exceptions.newIOException("The bake metadata value {0} is not a list.", field);
        }

        int[] result = new int[values.size()];

        for (int index = 0; index < result.length; index++) {
            if (!(values.get(index) instanceof Long value) || value < 0 || value > Integer.MAX_VALUE) {
                throw Exceptions.newIOException("The bake metadata list {0} holds a value that is not a count.", field);
            }

            result[index] = value.intValue();
        }

        return result;
    }


    private final @NotNull String name;
    private final int frameCount;

    private final int frameWidth;
    private final int frameHeight;
    private final int pivotX;
    private final int pivotY;

    private final int @NotNull [] playOrder;
    private final int @NotNull [] durations;
    private final boolean loop;
    private final boolean holdLastFrame;
    private final int @NotNull [] emissiveFrames;
    private final int @NotNull [] actionFrames;

    private BakedAnimation(@NotNull String name, int frameCount, int frameWidth, int frameHeight, int pivotX, int pivotY,
                           int @NotNull [] playOrder, int @NotNull [] durations, boolean loop, boolean holdLastFrame,
                           int @NotNull [] emissiveFrames, int @NotNull [] actionFrames) throws IOException {
        if (frameCount == 0 || durations.length != frameCount) {
            throw Exceptions.newIOException("The animation {0} has {1} frames and {2} durations.", name, frameCount, durations.length);
        }

        this.name       = name;
        this.frameCount = frameCount;

        this.frameWidth  = frameWidth;
        this.frameHeight = frameHeight;
        this.pivotX      = pivotX;
        this.pivotY      = pivotY;

        this.playOrder      = playOrder;
        this.durations      = durations;
        this.loop           = loop;
        this.holdLastFrame  = holdLastFrame;
        this.emissiveFrames = emissiveFrames;
        this.actionFrames   = actionFrames;
    }

    /**
     * @return the name, the folder of its frames ({@code hd/<name>/})
     */
    @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the frames per direction
     */
    int getFrameCount() {
        return this.frameCount;
    }

    /**
     * @return the width of every frame, in pixels of the baked quality
     */
    int getFrameWidth() {
        return this.frameWidth;
    }

    /**
     * @return the height of every frame, in pixels of the baked quality
     */
    int getFrameHeight() {
        return this.frameHeight;
    }

    /**
     * @return the column of the ground point in every frame
     */
    int getPivotX() {
        return this.pivotX;
    }

    /**
     * @return the row of the ground point in every frame
     */
    int getPivotY() {
        return this.pivotY;
    }

    /**
     * @return the frames in the order they play
     */
    int @NotNull [] getPlayOrder() {
        return this.playOrder.clone();
    }

    /**
     * @param frame the frame
     * @return the duration of the frame, in milliseconds
     */
    int getDuration(int frame) {
        return this.durations[frame];
    }

    /**
     * @return {@code true} if the animation repeats, {@code false} otherwise
     */
    boolean isLoop() {
        return this.loop;
    }

    /**
     * @return {@code true} if the last frame stays when the animation ends, {@code false} otherwise
     */
    boolean isHoldLastFrame() {
        return this.holdLastFrame;
    }

    /**
     * @return the frames at which the unit acts (the attack hits)
     */
    int @NotNull [] getActionFrames() {
        return this.actionFrames.clone();
    }

    /**
     * @return {@code true} if a frame has an {@code emissive} layer, {@code false} otherwise
     */
    boolean hasEmissiveLayer() {
        return this.emissiveFrames.length > 0;
    }

    /**
     * @param frame the frame
     * @return {@code true} if the frame has an {@code emissive} layer, {@code false} otherwise
     */
    boolean hasEmissiveLayer(int frame) {
        for (int emissiveFrame : this.emissiveFrames) {
            if (emissiveFrame == frame) {
                return true;
            }
        }

        return false;
    }
}
