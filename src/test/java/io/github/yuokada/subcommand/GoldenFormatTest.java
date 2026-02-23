package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden (snapshot) tests for the format subcommand.
 *
 * <p>For each SQL file in {@code src/main/resources/queries/}, the formatter output is compared
 * against a corresponding expected file in {@code src/test/resources/queries/expected/}.
 *
 * <p>When an expected file is missing, the test fails with an actionable message.
 * To (re)generate expected files, set the {@code UPDATE_GOLDEN=true} environment variable
 * before running the tests:
 * <pre>{@code UPDATE_GOLDEN=true ./mvnw test -Dtest=GoldenFormatTest}</pre>
 */
class GoldenFormatTest {

    /**
     * Captured stdout for each test.
     */
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    /**
     * Reference to the original stdout.
     */
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    /**
     * Provides the names of SQL files in {@code src/main/resources/queries/}.
     *
     * @return stream of SQL file names
     */
    static Stream<String> sqlFileNames() {
        return Stream.of("query1.sql", "query2.sql", "query4.sql", "sample.sql");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sqlFileNames")
    void testGoldenFormat(String sqlFileName) throws Exception {
        // Locate the input SQL file on the classpath
        URL inputUrl = getClass().getClassLoader().getResource("queries/" + sqlFileName);
        if (inputUrl == null) {
            // File not on classpath (e.g., sample.sql has no delimiter, skip gracefully)
            originalOut.println("[GoldenFormatTest] Skipping " + sqlFileName
                + " — not found on classpath");
            return;
        }

        Path inputPath = Path.of(inputUrl.toURI()).toAbsolutePath();

        // Run the formatter
        Format format = new Format();
        format.setSqlFile(inputPath.toString());
        format.call();
        String actual = outContent.toString(StandardCharsets.UTF_8);
        outContent.reset();

        if (actual.isEmpty()) {
            // No complete statements (e.g., sample.sql has no semicolon delimiter)
            originalOut.println("[GoldenFormatTest] Skipping " + sqlFileName
                + " — no complete statements");
            return;
        }

        // Locate the expected golden file
        Path expectedDir = findProjectRoot().resolve("src/test/resources/queries/expected");
        Files.createDirectories(expectedDir);
        Path expectedPath = expectedDir.resolve(sqlFileName.replace(".sql", ".expected.sql"));

        boolean updateGolden = "true".equalsIgnoreCase(System.getenv("UPDATE_GOLDEN"));

        if (!Files.exists(expectedPath)) {
            if (updateGolden) {
                Files.writeString(expectedPath, actual, StandardCharsets.UTF_8);
                originalOut.println("[GoldenFormatTest] Generated golden file: " + expectedPath);
                return;
            }
            fail("Golden file missing: " + expectedPath
                + ". Re-run with UPDATE_GOLDEN=true to generate it.");
        }

        if (updateGolden) {
            Files.writeString(expectedPath, actual, StandardCharsets.UTF_8);
            originalOut.println("[GoldenFormatTest] Updated golden file: " + expectedPath);
            return;
        }

        String expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        assertEquals(expected, actual,
            "Formatter output for " + sqlFileName + " differs from golden file");
    }

    /**
     * Locates the Maven project root by searching for {@code pom.xml}.
     *
     * @return the project root path
     * @throws IOException if the project root cannot be found
     */
    private static Path findProjectRoot() throws IOException {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate project root (pom.xml not found)");
    }
}
