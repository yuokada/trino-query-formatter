package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatIssueValidationTest {

    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUpStreams() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    @Test
    void missingSqlFileReturnsError() throws Exception {
        Format format = new Format();
        format.setSqlFile("missing.sql");

        assertEquals(2, format.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8).contains("SQL file not found"));
    }

    @Test
    void fileAndStdinTogetherReturnError(@TempDir Path tempDir) throws Exception {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "select id from foo;");
        System.setIn(new ByteArrayInputStream("select 1;".getBytes(StandardCharsets.UTF_8)));

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());

        assertEquals(2, format.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("provide either <file> or stdin, not both"));
    }
}
