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
 * Describes one animation of the added unit's frames: its frame sets in the frame files ({@link ScrCrossoverArtFiles}), the order the frames play
 * in, how many game frames each frame stays, and the frames that act (the attack's muzzle flash). The crossover's art converter writes the animations
 * of the baked frames into the file named by {@link ScrCrossoverArtSet#getAnimations()}; the unit's registration reads them to build its script.
 * <p>
 * A frame set holds {@link ScrCrossoverArtFiles#DIRECTIONS} directions, so frame {@code n} of the animation starts at frame
 * {@code (firstFrameSet + n) * DIRECTIONS} of the frame files.
 */
public final class ScrCrossoverAnimation {
    private static final @NotNull String ANIMATIONS_FIELD = "animations";
    private static final @NotNull String NAME_FIELD = "name";
    private static final @NotNull String FIRST_FRAME_SET_FIELD = "firstFrameSet";
    private static final @NotNull String FRAME_COUNT_FIELD = "frameCount";
    private static final @NotNull String PLAY_ORDER_FIELD = "playOrder";
    private static final @NotNull String WAITS_FIELD = "waits";
    private static final @NotNull String LOOP_FIELD = "loop";
    private static final @NotNull String HOLD_LAST_FRAME_FIELD = "holdLastFrame";
    private static final @NotNull String ACTION_FRAMES_FIELD = "actionFrames";

    /**
     * Reads the animations of the file named by {@link ScrCrossoverArtSet#getAnimations()}.
     *
     * @param contents the file contents
     * @return the animations in frame set order
     * @throws IOException if the contents are not the animations file or a value is out of range
     */
    public static @NotNull List<@NotNull ScrCrossoverAnimation> readAll(byte @NotNull [] contents) throws IOException {
        if (!(JsonReader.parse(contents) instanceof Map<?, ?> root) || !(root.get(ANIMATIONS_FIELD) instanceof List<?> entries)) {
            throw Exceptions.newIOException("The animations file has no {0} list.", ANIMATIONS_FIELD);
        }

        List<ScrCrossoverAnimation> animations = new ArrayList<>();

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> fields) || !(fields.get(NAME_FIELD) instanceof String name)) {
                throw Exceptions.newIOException("An animation has no {0}.", NAME_FIELD);
            }

            animations.add(new ScrCrossoverAnimation(name, readInteger(fields, FIRST_FRAME_SET_FIELD), readInteger(fields, FRAME_COUNT_FIELD),
                                                     readIntegers(fields, PLAY_ORDER_FIELD), readIntegers(fields, WAITS_FIELD),
                                                     Boolean.TRUE.equals(fields.get(LOOP_FIELD)),
                                                     Boolean.TRUE.equals(fields.get(HOLD_LAST_FRAME_FIELD)),
                                                     readIntegers(fields, ACTION_FRAMES_FIELD)));
        }

        return animations;
    }

    /**
     * Writes animations as the contents of the file named by {@link ScrCrossoverArtSet#getAnimations()}.
     *
     * @param animations the animations in frame set order
     * @return the file contents, UTF-8 JSON
     */
    public static byte @NotNull [] writeAll(@NotNull List<@NotNull ScrCrossoverAnimation> animations) {
        List<Object> entries = new ArrayList<>();

        for (ScrCrossoverAnimation animation : animations) {
            Map<String, Object> fields = new LinkedHashMap<>();

            fields.put(NAME_FIELD, animation.name);
            fields.put(FIRST_FRAME_SET_FIELD, (long) animation.firstFrameSet);
            fields.put(FRAME_COUNT_FIELD, (long) animation.frameCount);
            fields.put(PLAY_ORDER_FIELD, toList(animation.playOrder));
            fields.put(WAITS_FIELD, toList(animation.waits));
            fields.put(LOOP_FIELD, animation.loop);
            fields.put(HOLD_LAST_FRAME_FIELD, animation.holdLastFrame);
            fields.put(ACTION_FRAMES_FIELD, toList(animation.actionFrames));
            entries.add(fields);
        }

        Map<String, Object> root = new LinkedHashMap<>();

        root.put(ANIMATIONS_FIELD, entries);

        return JsonWriterUtils.write(root).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Finds an animation by name.
     *
     * @param animations the animations
     * @param name       the name, such as {@code idle}
     * @return the animation
     * @throws IOException if no animation has the name
     */
    public static @NotNull ScrCrossoverAnimation find(@NotNull List<@NotNull ScrCrossoverAnimation> animations, @NotNull String name)
    throws IOException {
        for (ScrCrossoverAnimation animation : animations) {
            if (animation.name.equals(name)) {
                return animation;
            }
        }

        throw Exceptions.newIOException("The animations file has no animation {0}.", name);
    }

    private static int readInteger(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        if (!(fields.get(field) instanceof Long value) || value < 0 || value > Integer.MAX_VALUE) {
            throw Exceptions.newIOException("The animation value {0} is not a count.", field);
        }

        return value.intValue();
    }

    private static int @NotNull [] readIntegers(@NotNull Map<?, ?> fields, @NotNull String field) throws IOException {
        if (!(fields.get(field) instanceof List<?> values)) {
            throw Exceptions.newIOException("The animation value {0} is not a list.", field);
        }

        int[] result = new int[values.size()];

        for (int index = 0; index < result.length; index++) {
            if (!(values.get(index) instanceof Long value) || value < 0 || value > Integer.MAX_VALUE) {
                throw Exceptions.newIOException("The animation list {0} holds a value that is not a count.", field);
            }

            result[index] = value.intValue();
        }

        return result;
    }

    private static void checkFrame(@NotNull String name, int frame, int frameCount) {
        if (frame < 0 || frame >= frameCount) {
            throw Exceptions.newIllegalArgumentException("The animation {0} has no frame {1}.", name, frame);
        }
    }

    private static @NotNull List<@NotNull Long> toList(int @NotNull [] values) {
        List<Long> list = new ArrayList<>();

        for (int value : values) {
            list.add((long) value);
        }

        return list;
    }


    private final @NotNull String name;
    private final int firstFrameSet;
    private final int frameCount;

    private final int @NotNull [] playOrder;
    private final int @NotNull [] waits;
    private final boolean loop;
    private final boolean holdLastFrame;
    private final int @NotNull [] actionFrames;

    /**
     * Creates a {@link ScrCrossoverAnimation} instance.
     *
     * @param name          the name, such as {@code idle}
     * @param firstFrameSet the frame set of the animation's first frame
     * @param frameCount    the frames of the animation, one frame set each
     * @param playOrder     the frames in the order they play
     * @param waits         the game frames each frame stays, by frame
     * @param loop          whether the animation repeats
     * @param holdLastFrame whether the last frame stays when the animation ends
     * @param actionFrames  the frames at which the unit acts (the attack hits)
     * @throws IllegalArgumentException if the first frame set is negative, the animation has no frame or no played frame, the waits do not cover
     *                                  every frame or a played or acting frame does not exist
     */
    public ScrCrossoverAnimation(@NotNull String name, int firstFrameSet, int frameCount, int @NotNull [] playOrder, int @NotNull [] waits,
                                 boolean loop, boolean holdLastFrame, int @NotNull [] actionFrames) {
        if (firstFrameSet < 0 || frameCount <= 0 || waits.length != frameCount || playOrder.length == 0) {
            throw Exceptions.newIllegalArgumentException("Invalid animation {0}: frame set {1}, {2} frames, {3} waits", name, firstFrameSet,
                                                         frameCount, waits.length);
        }

        for (int frame : playOrder) {
            checkFrame(name, frame, frameCount);
        }

        for (int frame : actionFrames) {
            checkFrame(name, frame, frameCount);
        }

        this.name          = name;
        this.firstFrameSet = firstFrameSet;
        this.frameCount    = frameCount;

        this.playOrder     = playOrder.clone();
        this.waits         = waits.clone();
        this.loop          = loop;
        this.holdLastFrame = holdLastFrame;
        this.actionFrames  = actionFrames.clone();
    }

    /**
     * @return the name, such as {@code idle}
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the frames in the order they play
     */
    public int @NotNull [] getPlayOrder() {
        return this.playOrder.clone();
    }

    /**
     * Returns the game frames a frame stays.
     *
     * @param frame the frame of the animation
     * @return the game frames, at least {@code 1}
     * @throws IndexOutOfBoundsException if the animation has no such frame
     */
    public int getWait(int frame) {
        return this.waits[frame];
    }

    /**
     * @return {@code true} if the last frame stays when the animation ends, {@code false} otherwise
     */
    public boolean isHoldLastFrame() {
        return this.holdLastFrame;
    }

    /**
     * @param frame the frame of the animation
     * @return {@code true} if the unit acts at the frame (the attack hits), {@code false} otherwise
     */
    public boolean isActionFrame(int frame) {
        for (int actionFrame : this.actionFrames) {
            if (actionFrame == frame) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the first frame of the frame files that shows a frame of the animation: the frame of direction {@code 0} (north) of its frame set,
     * as the script's {@code playfram} takes it.
     *
     * @param frame the frame of the animation
     * @return the frame in the frame files
     * @throws IllegalArgumentException if the animation has no such frame
     */
    public int getFrameSetStart(int frame) {
        checkFrame(this.name, frame, this.frameCount);

        return (this.firstFrameSet + frame) * ScrCrossoverArtFiles.DIRECTIONS;
    }
}
