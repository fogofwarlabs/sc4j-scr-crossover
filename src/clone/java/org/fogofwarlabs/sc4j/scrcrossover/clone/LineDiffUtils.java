package org.fogofwarlabs.sc4j.scrcrossover.clone;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the difference of two texts line by line in the unified diff form: a {@code ---} and a {@code +++} line naming the two texts, then
 * hunks with {@value #CONTEXT_LINES} unchanged lines around each change. The lines are matched by their longest common subsequence after the
 * common first and last lines are set aside, so the work grows with the changed part only.
 */
public final class LineDiffUtils {
    /**
     * The unchanged lines shown before and after a change.
     */
    public static final int CONTEXT_LINES = 3;

    private static final char UNCHANGED_LINE = ' ';
    private static final char REMOVED_LINE = '-';
    private static final char ADDED_LINE = '+';

    /**
     * Writes the difference of two texts.
     *
     * @param fromName the name of the first text, for the {@code ---} line
     * @param from     the lines of the first text
     * @param toName   the name of the second text, for the {@code +++} line
     * @param to       the lines of the second text
     * @return the difference, each line ended by {@code \n}; only the two name lines if the texts are equal
     */
    public static @NotNull String formatUnified(@NotNull String fromName, @NotNull List<@NotNull String> from,
                                                @NotNull String toName, @NotNull List<@NotNull String> to) {
        List<String> lines = compare(from, to);
        StringBuilder builder = new StringBuilder();
        int index = 0;

        builder.append("--- ").append(fromName).append('\n');
        builder.append("+++ ").append(toName).append('\n');

        while (index < lines.size()) {
            if (lines.get(index).charAt(0) == UNCHANGED_LINE) {
                index++;
                continue;
            }

            int start = Math.max(0, index - CONTEXT_LINES);
            int end = index;

            // A hunk goes on while the next change is at most twice the context away.
            while (end < lines.size()) {
                int next = end;

                while (next < lines.size() && lines.get(next).charAt(0) == UNCHANGED_LINE) {
                    next++;
                }

                if (next == lines.size() || next - end > 2 * CONTEXT_LINES) {
                    end = Math.min(lines.size(), end + CONTEXT_LINES);
                    break;
                }

                while (next < lines.size() && lines.get(next).charAt(0) != UNCHANGED_LINE) {
                    next++;
                }

                end = next;
            }

            appendHunk(builder, lines, start, end);
            index = end;
        }

        return builder.toString();
    }

    /**
     * Lists the lines of both texts in order, each prefixed with its kind: unchanged, removed from the first text or added in the second.
     */
    private static @NotNull List<@NotNull String> compare(@NotNull List<@NotNull String> from, @NotNull List<@NotNull String> to) {
        int prefix = 0;

        while (prefix < from.size() && prefix < to.size() && from.get(prefix).equals(to.get(prefix))) {
            prefix++;
        }

        int suffix = 0;

        while (suffix < from.size() - prefix && suffix < to.size() - prefix &&
               from.get(from.size() - 1 - suffix).equals(to.get(to.size() - 1 - suffix))) {
            suffix++;
        }

        int fromCount = from.size() - prefix - suffix;
        int toCount = to.size() - prefix - suffix;
        // The length of the longest common subsequence of the remaining lines from each pair of positions to their ends.
        int[][] common = new int[fromCount + 1][toCount + 1];

        for (int fromIndex = fromCount - 1; fromIndex >= 0; fromIndex--) {
            for (int toIndex = toCount - 1; toIndex >= 0; toIndex--) {
                common[fromIndex][toIndex] = from.get(prefix + fromIndex).equals(to.get(prefix + toIndex))
                                             ? common[fromIndex + 1][toIndex + 1] + 1
                                             : Math.max(common[fromIndex + 1][toIndex], common[fromIndex][toIndex + 1]);
            }
        }

        List<String> lines = new ArrayList<>(from.size() + toCount);

        for (int index = 0; index < prefix; index++) {
            lines.add(UNCHANGED_LINE + from.get(index));
        }

        int fromIndex = 0;
        int toIndex = 0;

        while (fromIndex < fromCount || toIndex < toCount) {
            if (fromIndex < fromCount && toIndex < toCount && from.get(prefix + fromIndex).equals(to.get(prefix + toIndex))) {
                lines.add(UNCHANGED_LINE + from.get(prefix + fromIndex));
                fromIndex++;
                toIndex++;
            } else if (toIndex == toCount || fromIndex < fromCount && common[fromIndex + 1][toIndex] >= common[fromIndex][toIndex + 1]) {
                lines.add(REMOVED_LINE + from.get(prefix + fromIndex));
                fromIndex++;
            } else {
                lines.add(ADDED_LINE + to.get(prefix + toIndex));
                toIndex++;
            }
        }

        for (int index = from.size() - suffix; index < from.size(); index++) {
            lines.add(UNCHANGED_LINE + from.get(index));
        }

        return lines;
    }

    private static void appendHunk(@NotNull StringBuilder builder, @NotNull List<@NotNull String> lines, int start, int end) {
        int fromBefore = 0;
        int toBefore = 0;
        int fromCount = 0;
        int toCount = 0;

        for (int index = 0; index < end; index++) {
            char kind = lines.get(index).charAt(0);
            boolean inHunk = index >= start;

            if (kind != ADDED_LINE) {
                fromBefore += inHunk ? 0 : 1;
                fromCount += inHunk ? 1 : 0;
            }

            if (kind != REMOVED_LINE) {
                toBefore += inHunk ? 0 : 1;
                toCount += inHunk ? 1 : 0;
            }
        }

        // An empty side names the line before the hunk, as unified diffs do.
        builder.append("@@ -").append(fromBefore + (fromCount > 0 ? 1 : 0)).append(',').append(fromCount)
               .append(" +").append(toBefore + (toCount > 0 ? 1 : 0)).append(',').append(toCount).append(" @@\n");

        for (String line : lines.subList(start, end)) {
            builder.append(line).append('\n');
        }
    }

    private LineDiffUtils() {
    }
}
