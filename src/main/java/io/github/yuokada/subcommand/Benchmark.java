package io.github.yuokada.subcommand;

import io.github.yuokada.api.AnalyzerApi;
import io.github.yuokada.api.FormatterApi;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.github.yuokada.core.QueryAnalysisResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * Benchmark subcommand for large SQL performance measurements.
 */
@CommandLine.Command(
    name = "benchmark",
    description = "Measure processing time and memory for large SQL.")
public class Benchmark implements Callable<Integer> {

    /**
     * Number of UNION ALL segments used to generate a large SQL query.
     */
    @CommandLine.Option(names = {"--segments"}, defaultValue = "500",
        description = "Number of UNION ALL segments in generated SQL.")
    private int segments = 500;

    /**
     * Warm-up iterations.
     */
    @CommandLine.Option(names = {"--warmup"}, defaultValue = "3",
        description = "Warm-up iteration count.")
    private int warmup = 3;

    /**
     * Measured iterations.
     */
    @CommandLine.Option(names = {"--iterations"}, defaultValue = "10",
        description = "Measured iteration count.")
    private int iterations = 10;

    /**
     * Benchmark mode.
     */
    @CommandLine.Option(names = {"--mode"}, defaultValue = "both",
        description = "Mode: format|analyze|both.")
    private String mode = "both";

    @Override
    public Integer call() {
        if (this.segments < 1 || this.warmup < 0 || this.iterations < 1) {
            System.err.println("Error: invalid benchmark parameters");
            return ExitCodes.ERROR;
        }

        Mode benchmarkMode;
        try {
            benchmarkMode = Mode.valueOf(this.mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: --mode must be one of format|analyze|both");
            return ExitCodes.ERROR;
        }

        String sql = generateLargeSql(this.segments);
        System.out.println("SQL size (chars): " + sql.length());
        System.out.println("Warm-up         : " + this.warmup);
        System.out.println("Iterations      : " + this.iterations);
        System.out.println("Mode            : " + benchmarkMode.name().toLowerCase(Locale.ROOT));

        for (int i = 0; i < this.warmup; i++) {
            runOnce(sql, benchmarkMode);
        }

        List<Long> nanos = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();
        long beforeUsed = usedMemoryBytes(runtime);
        for (int i = 0; i < this.iterations; i++) {
            long start = System.nanoTime();
            runOnce(sql, benchmarkMode);
            long elapsed = System.nanoTime() - start;
            nanos.add(elapsed);
        }
        long afterUsed = usedMemoryBytes(runtime);

        long min = nanos.stream().mapToLong(v -> v).min().orElse(0L);
        long max = nanos.stream().mapToLong(v -> v).max().orElse(0L);
        double avg = nanos.stream().mapToLong(v -> v).average().orElse(0.0);
        long p95 = percentile(nanos, 95);

        System.out.printf(Locale.ROOT, "Time avg (ms)   : %.3f%n", nanosToMillis(avg));
        System.out.printf(Locale.ROOT, "Time min (ms)   : %.3f%n", nanosToMillis(min));
        System.out.printf(Locale.ROOT, "Time p95 (ms)   : %.3f%n", nanosToMillis(p95));
        System.out.printf(Locale.ROOT, "Time max (ms)   : %.3f%n", nanosToMillis(max));
        System.out.printf(Locale.ROOT, "Mem delta (MiB) : %.3f%n",
            bytesToMib(afterUsed - beforeUsed));
        return ExitCodes.OK;
    }

    private static void runOnce(String sql, Mode mode) {
        if (mode == Mode.FORMAT || mode == Mode.BOTH) {
            FormatterApi.formatStatement(sql, KeywordCase.UPPER, 2);
        }
        if (mode == Mode.ANALYZE || mode == Mode.BOTH) {
            QueryAnalysisResult result =
                AnalyzerApi.analyzeStatement(sql, null, null, null, null);
            if (result.getParseError() != null) {
                throw new IllegalStateException("Benchmark SQL parse error: " + result.getParseError());
            }
        }
    }

    private static String generateLargeSql(int segments) {
        StringBuilder sb = new StringBuilder();
        sb.append("WITH base AS (\n")
            .append("  SELECT id, customer_id, amount, category\n")
            .append("  FROM orders\n")
            .append(")\n");
        sb.append("SELECT b.id, c.name, b.amount,\n")
            .append("  CASE WHEN b.amount > 1000 THEN 'large' ELSE 'small' END AS bucket\n")
            .append("FROM base b\n")
            .append("JOIN customers c ON (b.customer_id = c.id)\n")
            .append("WHERE b.category IN ('A', 'B')\n")
            .append("  AND b.id IN (\n")
            .append("    SELECT o.id\n")
            .append("    FROM orders o\n")
            .append("    WHERE o.amount > 10\n")
            .append("  )\n")
            .append("  AND (\n");
        for (int i = 0; i < segments; i++) {
            if (i > 0) {
                sb.append("    OR ");
            } else {
                sb.append("    ");
            }
            sb.append("b.id = ").append(i + 1).append("\n");
        }
        sb.append("  )\n");
        return sb.toString();
    }

    private static long usedMemoryBytes(Runtime runtime) {
        runtime.gc();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return total - free;
    }

    private static long percentile(List<Long> nanos, int p) {
        List<Long> sorted = nanos.stream().sorted().toList();
        int index = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        int bounded = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(bounded);
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static double bytesToMib(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    /**
     * Benchmark mode enum.
     */
    private enum Mode {
        FORMAT,
        ANALYZE,
        BOTH
    }
}
