package io.github.yuokada.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal unified-diff generator backed by a Longest Common Subsequence algorithm.
 *
 * <p>Produces standard unified-diff output ({@code ---}/{@code +++}/{@code @@} hunks)
 * with optional ANSI colour (red for deletions, green for additions, cyan for hunk headers).
 *
 * <p>The LCS DP table is O(n × m) in time and space, which is acceptable for the SQL
 * files this tool targets (typically a few hundred lines).
 */
public final class UnifiedDiff {

    /** ANSI red — used for deleted lines and the {@code ---} header. */
    private static final String ANSI_RED = "\033[31m";

    /** ANSI green — used for inserted lines and the {@code +++} header. */
    private static final String ANSI_GREEN = "\033[32m";

    /** ANSI cyan — used for {@code @@} hunk headers. */
    private static final String ANSI_CYAN = "\033[36m";

    /** ANSI reset sequence. */
    private static final String ANSI_RESET = "\033[0m";

    private UnifiedDiff() {
    }

    /**
     * Computes a unified diff between {@code original} and {@code revised} line lists.
     *
     * @param original     original lines
     * @param revised      revised lines
     * @param fromLabel    label shown on the {@code ---} header line
     * @param toLabel      label shown on the {@code +++} header line
     * @param contextLines number of unchanged context lines to show around each change
     * @param colorize     when {@code true}, ANSI colour codes are emitted
     * @return unified-diff string, or an empty string when there are no differences
     */
    public static String compute(
        List<String> original,
        List<String> revised,
        String fromLabel,
        String toLabel,
        int contextLines,
        boolean colorize) {

        List<Edit> edits = buildEdits(original, revised);
        List<int[]> ranges = buildHunkRanges(edits, contextLines);
        if (ranges.isEmpty()) {
            return "";
        }

        // Pre-compute 1-based original/revised line numbers for each edit position.
        int[] origLineAt = new int[edits.size()];
        int[] revLineAt = new int[edits.size()];
        int ol = 1;
        int rl = 1;
        for (int i = 0; i < edits.size(); i++) {
            origLineAt[i] = ol;
            revLineAt[i] = rl;
            int k = edits.get(i).kind;
            if (k == 0) {
                ol++;
                rl++;
            } else if (k == -1) {
                ol++;
            } else {
                rl++;
            }
        }

        StringBuilder sb = new StringBuilder();
        String fromHeader = "--- " + fromLabel;
        String toHeader = "+++ " + toLabel;
        if (colorize) {
            sb.append(ANSI_RED).append(fromHeader).append(ANSI_RESET).append('\n');
            sb.append(ANSI_GREEN).append(toHeader).append(ANSI_RESET).append('\n');
        } else {
            sb.append(fromHeader).append('\n');
            sb.append(toHeader).append('\n');
        }

        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];
            List<Edit> hunk = edits.subList(start, end + 1);

            int oStart = origLineAt[start];
            int rStart = revLineAt[start];
            int oCount = 0;
            int rCount = 0;
            for (Edit e : hunk) {
                if (e.kind == 0 || e.kind == -1) {
                    oCount++;
                }
                if (e.kind == 0 || e.kind == 1) {
                    rCount++;
                }
            }

            String hunkHeader =
                "@@ -" + oStart + "," + oCount + " +" + rStart + "," + rCount + " @@";
            if (colorize) {
                sb.append(ANSI_CYAN).append(hunkHeader).append(ANSI_RESET).append('\n');
            } else {
                sb.append(hunkHeader).append('\n');
            }

            for (Edit e : hunk) {
                String prefix = e.kind == -1 ? "-" : e.kind == 1 ? "+" : " ";
                String line = prefix + e.text;
                if (colorize && e.kind == -1) {
                    sb.append(ANSI_RED).append(line).append(ANSI_RESET);
                } else if (colorize && e.kind == 1) {
                    sb.append(ANSI_GREEN).append(line).append(ANSI_RESET);
                } else {
                    sb.append(line);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Builds hunk ranges as {@code [start, end]} index pairs into the edit list.
     * Adjacent or overlapping ranges are merged.
     *
     * @param edits       full edit list
     * @param ctx         number of context lines to include around each change
     * @return list of {@code [start, end]} pairs
     */
    private static List<int[]> buildHunkRanges(List<Edit> edits, int ctx) {
        List<Integer> changes = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            if (edits.get(i).kind != 0) {
                changes.add(i);
            }
        }
        if (changes.isEmpty()) {
            return List.of();
        }
        List<int[]> ranges = new ArrayList<>();
        for (int ci : changes) {
            int s = Math.max(0, ci - ctx);
            int e = Math.min(edits.size() - 1, ci + ctx);
            if (!ranges.isEmpty()) {
                int[] last = ranges.get(ranges.size() - 1);
                if (s <= last[1] + 1) {
                    last[1] = Math.max(last[1], e);
                    continue;
                }
            }
            ranges.add(new int[]{s, e});
        }
        return ranges;
    }

    /**
     * Builds a line-level edit list using the Longest Common Subsequence DP algorithm.
     * Each {@link Edit} has kind {@code 0} (equal), {@code -1} (delete from original),
     * or {@code +1} (insert into revised).
     *
     * @param a original lines
     * @param b revised lines
     * @return ordered list of edits describing the transformation from {@code a} to {@code b}
     */
    private static List<Edit> buildEdits(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        List<Edit> result = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a.get(i - 1).equals(b.get(j - 1))) {
                result.add(new Edit(0, a.get(i - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(new Edit(1, b.get(j - 1)));
                j--;
            } else {
                result.add(new Edit(-1, a.get(i - 1)));
                i--;
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * A single line-level edit operation.
     */
    private static final class Edit {

        /**
         * Edit kind: {@code 0} = equal, {@code -1} = delete, {@code +1} = insert.
         */
        final int kind;

        /**
         * The line text (without any diff prefix).
         */
        final String text;

        /**
         * @param kind edit kind
         * @param text line text
         */
        Edit(int kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }
}
