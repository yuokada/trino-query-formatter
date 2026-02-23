package io.github.yuokada;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.IVersionProvider;

/**
 * Provides CLI version information from {@code git.properties}.
 */
public class GitVersionProvider implements IVersionProvider {

    /**
     * Fallback value used when git metadata is unavailable.
     */
    private static final String UNKNOWN = "unknown";

    @Override
    public String[] getVersion() throws Exception {
        Properties properties = loadGitProperties();
        String appVersion = resolveApplicationVersion();
        String commitId = properties.getProperty("git.commit.id.abbrev", UNKNOWN);
        return new String[] {
            String.format("trino-query-formatter %s (%s)", appVersion, commitId)
        };
    }

    private static String resolveApplicationVersion() {
        try {
            return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class)
                .filter(value -> !value.isBlank())
                .orElse(UNKNOWN);
        } catch (RuntimeException ignored) {
            return UNKNOWN;
        }
    }

    private static Properties loadGitProperties() {
        Properties properties = new Properties();
        try (InputStream input = GitVersionProvider.class.getClassLoader()
            .getResourceAsStream("git.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Fall back to default "unknown" values when git.properties cannot be loaded.
        }
        return properties;
    }
}
