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

class FormatDirectoryModeTest {

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
    void directoryCheckSummaryAndExitCode(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("clean.sql"), "SELECT *\nFROM\n  foo\n;");
        Files.writeString(tempDir.resolve("warn.sql"), "select * from foo;");
        Files.writeString(tempDir.resolve("error.sql"), "SELEC * FROM foo;");

        Format format = new Format();
        format.setDirPath(tempDir.toString());
        format.setCheck(true);
        format.setSummary(true);

        int exit = format.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertEquals(2, exit);
        assertTrue(out.contains("Files analyzed : 3"));
        assertTrue(out.contains("  Clean        : 1"));
        assertTrue(out.contains("  Warnings     : 1"));
        assertTrue(out.contains("  Errors       : 1"));
        assertTrue(out.contains("Exit code: 2"));
        assertTrue(err.contains("Would reformat"));
        assertTrue(err.contains("failed to process error.sql"));
    }

    @Test
    void directoryExcludeSkipsMatchedFiles(@TempDir Path tempDir) throws IOException {
        Path skipDir = tempDir.resolve("skip");
        Files.createDirectories(skipDir);
        Files.writeString(tempDir.resolve("clean.sql"), "SELECT *\nFROM\n  foo\n;");
        Files.writeString(skipDir.resolve("warn.sql"), "select * from foo;");

        Format format = new Format();
        format.setDirPath(tempDir.toString());
        format.setCheck(true);
        format.setExcludePatterns(List.of("skip/**"));
        format.setSummary(true);

        int exit = format.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit);
        assertTrue(out.contains("Files analyzed : 1"));
        assertFalse(errContent.toString(StandardCharsets.UTF_8).contains("Would reformat"));
    }

    @Test
    void directoryModeRequiresCheckOrDiff(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("query.sql"), "SELECT 1;");

        Format format = new Format();
        format.setDirPath(tempDir.toString());

        int exit = format.call();

        assertEquals(2, exit);
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("--dir mode requires --check or --diff"));
    }

    @Test
    void parallelDirectoryCheckOutputMatchesSequential(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a_clean.sql"), "SELECT *\nFROM\n  foo\n;");
        Files.writeString(tempDir.resolve("b_warn.sql"), "select * from foo;");
        Files.writeString(tempDir.resolve("c_error.sql"), "SELEC * FROM foo;");

        RunResult sequential = runDirectoryCheck(tempDir, 1);
        RunResult parallel = runDirectoryCheck(tempDir, 2);

        assertEquals(sequential.exitCode(), parallel.exitCode());
        assertEquals(sequential.stdout(), parallel.stdout());
        assertEquals(sequential.stderr(), parallel.stderr());
    }

    @Test
    void parallelDirectoryDiffOutputMatchesSequential(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a_clean.sql"), "SELECT *\nFROM\n  foo\n;");
        Files.writeString(tempDir.resolve("b_warn.sql"), "select * from foo;");

        RunResult sequential = runDirectoryDiff(tempDir, 1);
        RunResult parallel = runDirectoryDiff(tempDir, 2);

        assertEquals(sequential.exitCode(), parallel.exitCode());
        assertEquals(sequential.stdout(), parallel.stdout());
        assertEquals(sequential.stderr(), parallel.stderr());
    }

    private RunResult runDirectoryCheck(Path dir, int parallelism) throws IOException {
        outContent.reset();
        errContent.reset();
        Format format = new Format();
        format.setDirPath(dir.toString());
        format.setCheck(true);
        format.setSummary(true);
        format.setDirectoryParallelismOverride(parallelism);

        int exit = format.call();

        return new RunResult(
            exit,
            outContent.toString(StandardCharsets.UTF_8),
            errContent.toString(StandardCharsets.UTF_8));
    }

    private RunResult runDirectoryDiff(Path dir, int parallelism) throws IOException {
        outContent.reset();
        errContent.reset();
        Format format = new Format();
        format.setDirPath(dir.toString());
        format.setDiff(true);
        format.setDirectoryParallelismOverride(parallelism);

        int exit = format.call();

        return new RunResult(
            exit,
            outContent.toString(StandardCharsets.UTF_8),
            errContent.toString(StandardCharsets.UTF_8));
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }
}
