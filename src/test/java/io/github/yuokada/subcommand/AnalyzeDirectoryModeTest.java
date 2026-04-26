package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeDirectoryModeTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void directorySummaryTextAndExitCode(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("clean.sql"), "SELECT id FROM foo;");
        Files.writeString(tempDir.resolve("warn.sql"), "SELECT * FROM foo ORDER BY 1;");
        Files.writeString(tempDir.resolve("error.sql"), "DELETE FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setDirPath(tempDir.toString());
        analyze.setSummary(true);

        int exit = analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertEquals(2, exit);
        assertTrue(out.contains("Files analyzed : 3"));
        assertTrue(out.contains("  Clean        : 1"));
        assertTrue(out.contains("  Warnings     : 1"));
        assertTrue(out.contains("  Errors       : 1"));
        assertTrue(out.contains("Exit code: 2"));
        assertTrue(out.contains("W001"));
        assertTrue(out.contains("E001"));
    }

    @Test
    void directoryJsonSummaryAndExclude(@TempDir Path tempDir) throws IOException {
        Path skipDir = tempDir.resolve("skip");
        Files.createDirectories(skipDir);
        Files.writeString(tempDir.resolve("warn.sql"), "SELECT * FROM foo ORDER BY 1;");
        Files.writeString(skipDir.resolve("error.sql"), "DELETE FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setDirPath(tempDir.toString());
        analyze.setFormat("json");
        analyze.setSummary(true);
        analyze.setExcludePatterns(List.of("skip/**"));

        int exit = analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertEquals(1, exit);
        assertTrue(out.startsWith("["));
        assertTrue(out.contains("\"summary\""));
        assertTrue(out.contains("\"warn.sql\""));
        assertFalse(out.contains("skip/error.sql"));
    }
}
