package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.api.AnalyzerApi;
import io.trino.sql.parser.SqlParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryRegressionTest {

    private static final int DEFAULT_MAX_MIB = 64;
    private static final int CORPUS_SIZE = 500;

    @Test
    void parseAndAnalyzeCorpusStaysWithinHeapDeltaLimit() {
        Runtime runtime = Runtime.getRuntime();
        List<String> corpus = generateCorpus(CORPUS_SIZE);
        SqlParser parser = new SqlParser();
        long maxBytes = maxBytes();

        long before = usedMemoryBytes(runtime);
        for (String sql : corpus) {
            parser.createStatement(sql);
            QueryAnalysisResult result = AnalyzerApi.analyzeStatement(sql, null, null, null, null);
            assertTrue(result.getParseError() == null, result.getParseError());
        }
        long after = usedMemoryBytes(runtime);

        long delta = Math.max(0L, after - before);
        assertTrue(withinLimit(delta, maxBytes),
            "Heap delta " + toMib(delta) + " MiB exceeded limit " + toMib(maxBytes) + " MiB");
    }

    @Test
    void gateRejectsDeliberateHundredMibSpike() {
        long maxBytes = 64L * 1024L * 1024L;
        long deliberateSpike = 100L * 1024L * 1024L;

        assertFalse(withinLimit(deliberateSpike, maxBytes));
    }

    private static List<String> generateCorpus(int count) {
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            statements.add("SELECT orderkey, custkey, totalprice FROM orders WHERE orderkey = "
                + (i + 1));
        }
        return statements;
    }

    private static long maxBytes() {
        String value = System.getProperty("memory.regression.maxMiB");
        int mib = DEFAULT_MAX_MIB;
        if (value != null && !value.isBlank()) {
            mib = Integer.parseInt(value);
        }
        return mib * 1024L * 1024L;
    }

    private static long usedMemoryBytes(Runtime runtime) {
        runtime.gc();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return total - free;
    }

    private static boolean withinLimit(long deltaBytes, long maxBytes) {
        return deltaBytes <= maxBytes;
    }

    private static long toMib(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
