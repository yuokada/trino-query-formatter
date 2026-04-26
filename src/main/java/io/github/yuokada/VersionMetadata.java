package io.github.yuokada;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Resolves version and runtime metadata for CLI version output.
 */
public final class VersionMetadata {

    /**
     * Fallback value used when metadata is unavailable.
     */
    public static final String UNKNOWN = "unknown";

    private VersionMetadata() {
    }

    /**
     * Returns the application version.
     *
     * @return application version or {@link #UNKNOWN}
     */
    public static String applicationVersion() {
        try {
            return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class)
                .filter(value -> !value.isBlank())
                .orElse(UNKNOWN);
        } catch (RuntimeException ignored) {
            return UNKNOWN;
        }
    }

    /**
     * Loads git metadata written at build time.
     *
     * @return git properties, possibly empty
     */
    public static Properties gitProperties() {
        Properties properties = new Properties();
        try (InputStream input = VersionMetadata.class.getClassLoader()
            .getResourceAsStream("git.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Return an empty properties object when build metadata is unavailable.
        }
        return properties;
    }

    /**
     * Reads a dependency version from Maven pom.properties on the classpath.
     *
     * @param groupId    dependency group ID
     * @param artifactId dependency artifact ID
     * @return dependency version or {@link #UNKNOWN}
     */
    public static String dependencyVersion(String groupId, String artifactId) {
        String resourcePath = String.format("META-INF/maven/%s/%s/pom.properties", groupId, artifactId);
        Properties properties = new Properties();
        try (InputStream input = VersionMetadata.class.getClassLoader()
            .getResourceAsStream(resourcePath)) {
            if (input == null) {
                return UNKNOWN;
            }
            properties.load(input);
            return properties.getProperty("version", UNKNOWN);
        } catch (IOException ignored) {
            return UNKNOWN;
        }
    }
}
