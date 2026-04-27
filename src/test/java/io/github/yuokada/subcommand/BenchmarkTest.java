package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.EntryCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BenchmarkTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    void benchmark_runsAndPrintsMetrics() {
        int exit = EntryCommand.newCommandLine().execute(
            "benchmark",
            "--segments", "5",
            "--warmup", "0",
            "--iterations", "1",
            "--mode", "format");

        String out = outContent.toString();
        assertEquals(0, exit);
        assertTrue(out.contains("SQL size"), "Expected SQL size output");
        assertTrue(out.contains("Time avg"), "Expected timing output");
        assertTrue(out.contains("Mem delta"), "Expected memory output");
    }
}
