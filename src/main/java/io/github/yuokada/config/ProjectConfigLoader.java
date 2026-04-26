package io.github.yuokada.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loads project config defaults from YAML.
 */
public final class ProjectConfigLoader {

    /**
     * Default config filename.
     */
    public static final String CONFIG_FILENAME = ".trino-query-formatter.yml";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static Path workingDirectoryOverride;
    private static Path userHomeOverride;

    private ProjectConfigLoader() {
    }

    /**
     * Resolves and loads the project config file.
     *
     * @param explicitPath explicit CLI path, may be null or blank
     * @return loaded config, or null when no file exists
     * @throws ConfigException on missing explicit files or YAML parse errors
     */
    public static LoadedProjectConfig load(String explicitPath) throws ConfigException {
        Path configPath = resolveConfigPath(explicitPath);
        if (configPath == null) {
            return null;
        }
        try (InputStream input = Files.newInputStream(configPath)) {
            JsonNode root = YAML_MAPPER.readTree(input);
            List<String> warnings = new ArrayList<>();
            validateKeys(root, warnings);
            ProjectConfig config = root == null
                ? new ProjectConfig()
                : YAML_MAPPER.treeToValue(root, ProjectConfig.class);
            if (config.getAnalyze() == null) {
                config.setAnalyze(new AnalyzeConfig());
            }
            if (config.getFormat() == null) {
                config.setFormat(new FormatConfig());
            }
            if (config.getLint() == null) {
                config.setLint(new LintConfig());
            }
            return new LoadedProjectConfig(configPath, config, warnings);
        } catch (IOException e) {
            throw new ConfigException("Failed to load config file: " + configPath + ": "
                + e.getMessage(), e);
        }
    }

    private static Path resolveConfigPath(String explicitPath) throws ConfigException {
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath);
            if (!Files.exists(path)) {
                throw new ConfigException("Config file not found: " + explicitPath);
            }
            return path.toAbsolutePath().normalize();
        }

        Path cwdPath = currentWorkingDirectory().resolve(CONFIG_FILENAME);
        if (Files.exists(cwdPath)) {
            return cwdPath;
        }

        Path userHome = currentUserHome();
        if (userHome == null) {
            return null;
        }
        Path homePath = userHome.resolve(CONFIG_FILENAME);
        if (Files.exists(homePath)) {
            return homePath.toAbsolutePath().normalize();
        }
        return null;
    }

    /**
     * Overrides the working directory used for config discovery. Intended for tests.
     *
     * @param path override path, or null to clear
     */
    public static void setWorkingDirectoryOverride(Path path) {
        workingDirectoryOverride = path == null ? null : path.toAbsolutePath().normalize();
    }

    /**
     * Overrides the user home directory used for config discovery. Intended for tests.
     *
     * @param path override path, or null to clear
     */
    public static void setUserHomeOverride(Path path) {
        userHomeOverride = path == null ? null : path.toAbsolutePath().normalize();
    }

    private static Path currentWorkingDirectory() {
        if (workingDirectoryOverride != null) {
            return workingDirectoryOverride;
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path currentUserHome() {
        if (userHomeOverride != null) {
            return userHomeOverride;
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }
        return Path.of(userHome).toAbsolutePath().normalize();
    }

    private static void validateKeys(JsonNode root, List<String> warnings) {
        if (root == null || !root.isObject()) {
            return;
        }
        validateObjectKeys(root, "", warnings,
            Set.of("analyze", "format", "lint"));
        validateNested(root, "analyze", warnings,
            Set.of("format", "details", "validate-functions", "udf-catalog", "server"));
        validateNested(root, "format", warnings,
            Set.of("check", "diff", "keyword-case"));
        validateNested(root, "lint", warnings,
            Set.of("disable-rules", "enable-rules"));
    }

    private static void validateNested(JsonNode root, String fieldName, List<String> warnings,
        Set<String> allowed) {
        JsonNode node = root.get(fieldName);
        if (node != null && node.isObject()) {
            validateObjectKeys(node, fieldName + ".", warnings, allowed);
        }
    }

    private static void validateObjectKeys(JsonNode node, String prefix, List<String> warnings,
        Set<String> allowed) {
        node.fieldNames().forEachRemaining(fieldName -> {
            if (!allowed.contains(fieldName)) {
                warnings.add("Warning: unknown config key: " + prefix + fieldName);
            }
        });
    }
}
