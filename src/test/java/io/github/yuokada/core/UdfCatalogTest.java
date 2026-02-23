package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link UdfCatalog} YAML loading and {@link UdfDefinition} arity logic.
 */
class UdfCatalogTest {

    // ---- YAML loading -------------------------------------------------------

    @Test
    void testLoad_exactArity(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 2\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        assertEquals(1, catalog.size());
        assertTrue(catalog.containsKey("my_fn"));
        assertEquals(2, catalog.get("my_fn").arity);
        assertNull(catalog.get("my_fn").minArgs);
    }

    @Test
    void testLoad_minArgs(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: variadic_fn\n    minArgs: 1\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        UdfDefinition def = catalog.get("variadic_fn");
        assertNotNull(def);
        assertNull(def.arity);
        assertEquals(1, def.minArgs);
        assertNull(def.maxArgs);
    }

    @Test
    void testLoad_minAndMaxArgs(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml,
            "functions:\n  - name: bounded_fn\n    minArgs: 2\n    maxArgs: 4\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        UdfDefinition def = catalog.get("bounded_fn");
        assertNotNull(def);
        assertEquals(2, def.minArgs);
        assertEquals(4, def.maxArgs);
    }

    @Test
    void testLoad_noArityConstraint(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml,
            "functions:\n  - name: unconstrained_fn\n    description: \"known UDF\"\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        UdfDefinition def = catalog.get("unconstrained_fn");
        assertNotNull(def);
        assertFalse(def.hasArityConstraint(), "Should have no arity constraint");
    }

    @Test
    void testLoad_keyIsLowercase(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: My_ETL_Transform\n    arity: 1\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        assertTrue(catalog.containsKey("my_etl_transform"),
            "Key must be lowercased");
        assertFalse(catalog.containsKey("My_ETL_Transform"),
            "Original-case key must not be present");
    }

    @Test
    void testLoad_multipleEntries(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml,
            "functions:\n"
                + "  - name: fn_a\n    arity: 1\n"
                + "  - name: fn_b\n    minArgs: 2\n"
                + "  - name: fn_c\n    description: \"no arity\"\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);

        assertEquals(3, catalog.size());
        assertEquals(1, catalog.get("fn_a").arity);
        assertEquals(2, catalog.get("fn_b").minArgs);
        assertFalse(catalog.get("fn_c").hasArityConstraint());
    }

    @Test
    void testLoad_yamlComment(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml,
            "# Project UDFs\n"
                + "functions:\n"
                + "  - name: my_fn  # inline comment\n"
                + "    arity: 3\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);
        assertEquals(1, catalog.size());
        assertEquals(3, catalog.get("my_fn").arity);
    }

    @Test
    void testLoad_emptyFile(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n");

        Map<String, UdfDefinition> catalog = UdfCatalog.load(yaml);
        assertTrue(catalog.isEmpty(), "Empty functions list should produce empty map");
    }

    // ---- UdfDefinition arity logic ------------------------------------------

    @Test
    void testIsArityValid_exactMatch() {
        UdfDefinition def = new UdfDefinition();
        def.arity = 2;
        assertTrue(def.isArityValid(2));
        assertFalse(def.isArityValid(1));
        assertFalse(def.isArityValid(3));
    }

    @Test
    void testIsArityValid_minOnly() {
        UdfDefinition def = new UdfDefinition();
        def.minArgs = 1;
        assertTrue(def.isArityValid(1));
        assertTrue(def.isArityValid(5));
        assertFalse(def.isArityValid(0));
    }

    @Test
    void testIsArityValid_minAndMax() {
        UdfDefinition def = new UdfDefinition();
        def.minArgs = 2;
        def.maxArgs = 4;
        assertFalse(def.isArityValid(1));
        assertTrue(def.isArityValid(2));
        assertTrue(def.isArityValid(4));
        assertFalse(def.isArityValid(5));
    }

    @Test
    void testIsArityValid_noConstraint() {
        UdfDefinition def = new UdfDefinition();
        // All calls are valid when no constraint is set
        assertTrue(def.isArityValid(0));
        assertTrue(def.isArityValid(100));
    }

    @Test
    void testExpectedArityDescription_exact() {
        UdfDefinition def = new UdfDefinition();
        def.arity = 3;
        assertEquals("3", def.expectedArityDescription());
    }

    @Test
    void testExpectedArityDescription_minOnly() {
        UdfDefinition def = new UdfDefinition();
        def.minArgs = 1;
        assertEquals("at least 1", def.expectedArityDescription());
    }

    @Test
    void testExpectedArityDescription_maxOnly() {
        UdfDefinition def = new UdfDefinition();
        def.maxArgs = 5;
        assertEquals("at most 5", def.expectedArityDescription());
    }

    @Test
    void testExpectedArityDescription_minAndMax() {
        UdfDefinition def = new UdfDefinition();
        def.minArgs = 2;
        def.maxArgs = 4;
        assertEquals("between 2 and 4", def.expectedArityDescription());
    }

    @Test
    void testExpectedArityDescription_noConstraint() {
        UdfDefinition def = new UdfDefinition();
        assertNull(def.expectedArityDescription(), "No constraint should return null");
    }

    @Test
    void testHasArityConstraint() {
        UdfDefinition def = new UdfDefinition();
        assertFalse(def.hasArityConstraint());
        def.arity = 1;
        assertTrue(def.hasArityConstraint());
    }
}
