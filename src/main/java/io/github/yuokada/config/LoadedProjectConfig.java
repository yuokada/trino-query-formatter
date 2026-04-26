package io.github.yuokada.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Loaded config along with origin metadata.
 */
public class LoadedProjectConfig {

    /**
     * Resolved config file path.
     */
    private final Path path;

    /**
     * Parsed config.
     */
    private final ProjectConfig config;

    /**
     * Non-fatal config warnings.
     */
    private final List<String> warnings;

    /**
     * Creates a loaded config descriptor.
     *
     * @param path     config file path
     * @param config   parsed config
     * @param warnings warnings collected while parsing
     */
    public LoadedProjectConfig(Path path, ProjectConfig config, List<String> warnings) {
        this.path = path;
        this.config = config;
        this.warnings = warnings;
    }

    public Path getPath() {
        return this.path;
    }

    public ProjectConfig getConfig() {
        return this.config;
    }

    public List<String> getWarnings() {
        return this.warnings;
    }

    /**
     * Resolves a path relative to the config file location.
     *
     * @param value raw path value
     * @return absolute or normalized path
     */
    public Path resolvePath(String value) {
        Path pathValue = Path.of(value);
        if (pathValue.isAbsolute()) {
            return pathValue;
        }
        return this.path.getParent().resolve(pathValue).normalize();
    }
}
