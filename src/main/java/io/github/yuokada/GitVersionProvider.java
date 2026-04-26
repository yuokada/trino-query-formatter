package io.github.yuokada;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides CLI version information from {@code git.properties}.
 */
public class GitVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {
            String.format("trino-query-formatter %s", VersionMetadata.applicationVersion())
        };
    }
}
