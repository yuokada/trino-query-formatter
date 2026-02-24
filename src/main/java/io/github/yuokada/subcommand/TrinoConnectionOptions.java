package io.github.yuokada.subcommand;

import picocli.CommandLine;

/**
 * Picocli {@link CommandLine.ArgGroup} that bundles all Trino server connection options.
 *
 * <p>These options are only effective when {@code --server} is specified. When absent,
 * the remote validation phase (Phase 2) is skipped entirely.
 */
public class TrinoConnectionOptions {

    /**
     * Trino server host and port (e.g. {@code localhost:8080}).
     * When {@code null}, remote validation is disabled.
     */
    @CommandLine.Option(
        names = {"--server"},
        description = "Trino server host:port for remote EXPLAIN (TYPE VALIDATE) (e.g. localhost:8080)")
    private String server;

    /**
     * Trino user name. Defaults to the current OS user when not specified.
     */
    @CommandLine.Option(
        names = {"--server-user"},
        description = "Trino user name (default: current OS user)")
    private String user;

    /**
     * Password for Basic authentication.
     * Specify without a value (e.g. {@code --server-password}) to be prompted interactively,
     * avoiding exposure in shell history and process listings.
     * The {@code TRINO_PASSWORD} environment variable is the recommended alternative for CI/CD.
     */
    @CommandLine.Option(
        names = {"--server-password"},
        description = "Password for Basic authentication (prefer TRINO_PASSWORD env var)."
            + " Omit the value to be prompted interactively.",
        arity = "0..1",
        interactive = true)
    private String password;

    /**
     * Bearer access token for OAuth2 / JWT authentication.
     */
    @CommandLine.Option(
        names = {"--server-access-token"},
        description = "Bearer token for OAuth2/JWT authentication")
    private String accessToken;

    /**
     * Enables SSL/TLS for the connection.
     */
    @CommandLine.Option(
        names = {"--server-ssl"},
        defaultValue = "false",
        description = "Enable SSL/TLS for the Trino connection")
    private boolean ssl;

    /**
     * Disables SSL certificate verification (development use only).
     */
    @CommandLine.Option(
        names = {"--server-ssl-trust-all"},
        defaultValue = "false",
        description = "Disable SSL certificate verification (development only)")
    private boolean sslTrustAll;

    /**
     * Timeout in seconds for the EXPLAIN (TYPE VALIDATE) request.
     */
    @CommandLine.Option(
        names = {"--explain-timeout"},
        defaultValue = "30",
        description = "Timeout in seconds for EXPLAIN (TYPE VALIDATE) (default: 30)")
    private int explainTimeout;

    /**
     * Returns the effective server address, resolving from the environment variable
     * {@code TRINO_SERVER} when the option was not explicitly set.
     *
     * @return server host:port string, or {@code null} when absent
     */
    public String getServer() {
        if (this.server != null) {
            return this.server;
        }
        return System.getenv("TRINO_SERVER");
    }

    /**
     * Returns the effective user name, resolving from the environment variable
     * {@code TRINO_USER} or the current OS user name as a fallback.
     *
     * @return user name string (never null)
     */
    public String getUser() {
        if (this.user != null) {
            return this.user;
        }
        String envUser = System.getenv("TRINO_USER");
        if (envUser != null) {
            return envUser;
        }
        return System.getProperty("user.name");
    }

    /**
     * Returns the effective password, resolving from the environment variable
     * {@code TRINO_PASSWORD} when the option was not explicitly set.
     *
     * @return password string, or {@code null} when absent
     */
    public String getPassword() {
        if (this.password != null) {
            return this.password;
        }
        return System.getenv("TRINO_PASSWORD");
    }

    /**
     * Returns the effective Bearer access token, resolving from the environment variable
     * {@code TRINO_ACCESS_TOKEN} when the option was not explicitly set.
     *
     * @return access token string, or {@code null} when absent
     */
    public String getAccessToken() {
        if (this.accessToken != null) {
            return this.accessToken;
        }
        return System.getenv("TRINO_ACCESS_TOKEN");
    }

    /**
     * Returns whether SSL/TLS should be enabled.
     *
     * @return {@code true} when SSL is enabled
     */
    public boolean isSsl() {
        return this.ssl;
    }

    /**
     * Returns whether SSL certificate verification should be disabled.
     *
     * @return {@code true} when trust-all is enabled
     */
    public boolean isSslTrustAll() {
        return this.sslTrustAll;
    }

    /**
     * Returns the EXPLAIN timeout in seconds.
     *
     * @return timeout in seconds
     */
    public int getExplainTimeout() {
        return this.explainTimeout;
    }

    /**
     * Returns {@code true} when remote validation is configured (i.e. {@code --server} is set
     * or the {@code TRINO_SERVER} environment variable is present).
     *
     * @return {@code true} when a server address is available
     */
    public boolean isEnabled() {
        return this.getServer() != null && !this.getServer().isBlank();
    }

    // Package-private setters for use in tests only.

    void setServer(String server) {
        this.server = server;
    }

    void setUser(String user) {
        this.user = user;
    }

    void setPassword(String password) {
        this.password = password;
    }

    void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    void setSslTrustAll(boolean sslTrustAll) {
        this.sslTrustAll = sslTrustAll;
    }

    void setExplainTimeout(int explainTimeout) {
        this.explainTimeout = explainTimeout;
    }
}
