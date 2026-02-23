package io.github.yuokada;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class EntryCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    @Test
    void formatSubcommand_viaCommandLine(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("format.sql");
        Files.writeString(sqlFile, "select * from foo;");

        int exit = new CommandLine(new EntryCommand()).execute("format", sqlFile.toString());

        String expected = """
            SELECT *
            FROM
              foo
            ;""".trim();
        assertEquals(0, exit);
        assertEquals(expected, outContent.toString().trim());
    }

    @Test
    void analyzeSubcommand_viaCommandLine(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("analyze.sql");
        Files.writeString(sqlFile, "SELECT * FROM catalog1.schema.tbl1;");

        int exit = new CommandLine(new EntryCommand()).execute("analyze", sqlFile.toString());

        String expected = """
            =========================
            Catalogs: [catalog1]
            """.trim();
        assertEquals(0, exit);
        assertEquals(expected, outContent.toString().trim());
    }

    @Test
    void helpFlag_printsUsage() {
        int exit = new CommandLine(new EntryCommand()).execute("--help");
        String out = outContent.toString();
        // Usage text contains subcommands and description
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("Usage:"));
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("format"));
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("analyze"));
        org.junit.jupiter.api.Assertions.assertEquals(0, exit);
    }

    @Test
    void versionFlag_printsVersion() {
        int exit = new CommandLine(new EntryCommand()).execute("-V");
        String out = outContent.toString();
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("0.1"));
        org.junit.jupiter.api.Assertions.assertEquals(0, exit);
    }

    @Test
    void format_fromStdin_viaCommandLine() {
        String sql = "select * from foo;select * from bar;\n";
        System.setIn(
            new ByteArrayInputStream(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        int exit = new CommandLine(new EntryCommand()).execute("format");

        String expected = """
            SELECT *
            FROM
              foo
            ;
            SELECT *
            FROM
              bar
            ;""".trim();
        assertEquals(0, exit);
        assertEquals(expected, outContent.toString().trim());
    }

    @Test
    void analyze_fromStdin_viaCommandLine() {
        String sql = "SELECT * FROM catalog1.schema.tbl1; SELECT 1;\n";
        System.setIn(
            new ByteArrayInputStream(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        int exit = new CommandLine(new EntryCommand()).execute("analyze");

        assertEquals(1, exit);
        assertEquals("", outContent.toString().trim());
        assertEquals(
            "analyze supports exactly one query; found multiple statements",
            errContent.toString().trim());
    }

    @Test
    void analyze_multipleQueriesFromFile_viaCommandLine(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("analyze_multi.sql");
        Files.writeString(sqlFile, "SELECT * FROM catalog1.schema.tbl1; SELECT 1;");

        int exit = new CommandLine(new EntryCommand()).execute("analyze", sqlFile.toString());

        assertEquals(1, exit);
        assertEquals("", outContent.toString().trim());
        assertEquals(
            "analyze supports exactly one query; found multiple statements",
            errContent.toString().trim());
    }
}
