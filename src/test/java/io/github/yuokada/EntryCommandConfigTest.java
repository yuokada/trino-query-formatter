package io.github.yuokada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.config.ProjectConfigLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryCommandConfigTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final String originalUserDir = System.getProperty("user.dir");
    private final String originalUserHome = System.getProperty("user.home");

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreState() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setProperty("user.dir", originalUserDir);
        System.setProperty("user.home", originalUserHome);
        ProjectConfigLoader.setWorkingDirectoryOverride(null);
        ProjectConfigLoader.setUserHomeOverride(null);
    }

    @Test
    void analyzeUsesConfigFromCurrentDirectory(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Path configFile = tempDir.resolve(".trino-query-formatter.yml");
        Files.writeString(sqlFile, "SELECT * FROM foo;");
        Files.writeString(configFile, """
            analyze:
              format: json
              details: full
            """);
        ProjectConfigLoader.setWorkingDirectoryOverride(tempDir);

        int exit = EntryCommand.newCommandLine().execute("analyze", sqlFile.toString());

        assertEquals(0, exit);
        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("\"findings\""));
        assertTrue(out.contains("\"hint\""));
    }

    @Test
    void analyzeUsesBuiltInDefaultsWhenNoConfigExists(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Files.writeString(sqlFile, "SELECT * FROM catalog1.schema.tbl1;");
        ProjectConfigLoader.setWorkingDirectoryOverride(tempDir);

        int exit = EntryCommand.newCommandLine().execute("analyze", sqlFile.toString());

        assertEquals(0, exit);
        assertTrue(outContent.toString(StandardCharsets.UTF_8).contains("Catalogs: [catalog1]"));
    }

    @Test
    void analyzeUsesConfigFromHomeDirectory(@TempDir Path tempDir) throws IOException {
        Path homeDir = tempDir.resolve("home");
        Path workDir = tempDir.resolve("work");
        Path sqlFile = workDir.resolve("query.sql");
        Files.createDirectories(homeDir);
        Files.createDirectories(workDir);
        Files.writeString(sqlFile, "SELECT * FROM foo;");
        Files.writeString(homeDir.resolve(".trino-query-formatter.yml"), """
            analyze:
              format: json
            """);
        ProjectConfigLoader.setUserHomeOverride(homeDir);
        ProjectConfigLoader.setWorkingDirectoryOverride(workDir);

        int exit = EntryCommand.newCommandLine().execute("analyze", sqlFile.toString());

        assertEquals(0, exit);
        assertTrue(outContent.toString(StandardCharsets.UTF_8).contains("\"queryType\""));
    }

    @Test
    void explicitConfigOverridesSearchPath(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Path configFile = tempDir.resolve("custom.yml");
        Files.writeString(sqlFile, "select * from foo;");
        Files.writeString(configFile, """
            format:
              keyword-case: lower
            """);

        int exit = EntryCommand.newCommandLine().execute(
            "--config", configFile.toString(), "format", sqlFile.toString());

        assertEquals(0, exit);
        assertTrue(outContent.toString(StandardCharsets.UTF_8).contains("select"));
    }

    @Test
    void cliWinsOverConfig(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Path configFile = tempDir.resolve(".trino-query-formatter.yml");
        Files.writeString(sqlFile, "SELECT * FROM catalog1.schema.tbl1;");
        Files.writeString(configFile, """
            analyze:
              format: json
            """);
        ProjectConfigLoader.setWorkingDirectoryOverride(tempDir);

        int exit = EntryCommand.newCommandLine().execute(
            "analyze", "--format", "text", sqlFile.toString());

        assertEquals(0, exit);
        assertTrue(outContent.toString(StandardCharsets.UTF_8).contains("Catalogs: [catalog1]"));
    }

    @Test
    void unknownConfigKeysWarnButDoNotAbort(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Path configFile = tempDir.resolve(".trino-query-formatter.yml");
        Files.writeString(sqlFile, "SELECT * FROM foo;");
        Files.writeString(configFile, """
            analyze:
              format: json
              unexpected: true
            """);
        ProjectConfigLoader.setWorkingDirectoryOverride(tempDir);

        int exit = EntryCommand.newCommandLine().execute("analyze", sqlFile.toString());

        assertEquals(0, exit);
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("Warning: unknown config key: analyze.unexpected"));
    }

    @Test
    void missingExplicitConfigReturnsError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Files.writeString(sqlFile, "SELECT * FROM foo;");

        int exit = EntryCommand.newCommandLine().execute(
            "--config", tempDir.resolve("missing.yml").toString(), "analyze", sqlFile.toString());

        assertEquals(2, exit);
        assertTrue(errContent.toString(StandardCharsets.UTF_8).contains("Config file not found"));
    }

    @Test
    void invalidYamlConfigReturnsError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("query.sql");
        Path configFile = tempDir.resolve("broken.yml");
        Files.writeString(sqlFile, "SELECT * FROM foo;");
        Files.writeString(configFile, "analyze: [");

        int exit = EntryCommand.newCommandLine().execute(
            "--config", configFile.toString(), "analyze", sqlFile.toString());

        assertEquals(2, exit);
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("Failed to load config file"));
    }
}
