package io.github.yuokada.core;

import io.github.yuokada.subcommand.TrinoConnectionOptions;
import io.trino.client.ClientSession;
import io.trino.client.ErrorLocation;
import io.trino.client.OkHttpUtil;
import io.trino.client.QueryError;
import io.trino.client.StatementClient;
import io.trino.client.StatementClientFactory;
import java.io.Closeable;
import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * Wraps {@code io.trino:trino-client} to execute
 * {@code EXPLAIN (TYPE VALIDATE)} against a remote Trino server.
 *
 * <p>Usage:
 * <pre>{@code
 * try (TrinoExplainClient client = TrinoExplainClient.create(opts, catalog, schema)) {
 *     RemoteValidationResult result = client.validate(sql);
 * }
 * }</pre>
 */
public final class TrinoExplainClient implements Closeable {

    private final OkHttpClient httpClient;
    private final ClientSession session;

    private TrinoExplainClient(OkHttpClient httpClient, ClientSession session) {
        this.httpClient = httpClient;
        this.session = session;
    }

    /**
     * Creates a {@code TrinoExplainClient} configured from the given connection options.
     *
     * <p>The {@code catalog} and {@code schema} parameters are forwarded as the session's
     * default catalog/schema so that unqualified table references resolve correctly during
     * {@code EXPLAIN (TYPE VALIDATE)}.
     *
     * @param opts    connection options (server, user, auth, SSL)
     * @param catalog default catalog for the session (may be {@code null})
     * @param schema  default schema for the session (may be {@code null})
     * @return a configured client ready to execute validate requests
     * @throws IllegalArgumentException if the server address produces an invalid URI
     */
    public static TrinoExplainClient create(
            TrinoConnectionOptions opts, String catalog, String schema) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Authentication
        String token = opts.getAccessToken();
        String password = opts.getPassword();
        boolean hasAuth = false;
        if (token != null && !token.isBlank()) {
            builder.addInterceptor(OkHttpUtil.tokenAuth(token));
            hasAuth = true;
        } else if (password != null && !password.isBlank()) {
            builder.addInterceptor(OkHttpUtil.basicAuth(opts.getUser(), password));
            hasAuth = true;
        }

        // SSL
        if (opts.isSslTrustAll()) {
            if (opts.isSsl()) {
                // Both --server-ssl and --server-ssl-trust-all supplied:
                // trust-all takes precedence and disables certificate verification.
                System.err.println("[remote-validation] WARNING: --server-ssl-trust-all disables "
                    + "certificate verification; --server-ssl is redundant when trust-all is set.");
            }
            OkHttpUtil.setupInsecureSsl(builder);
        }

        // Warn when credentials are sent over plain HTTP (no TLS).
        // --server-ssl or --server-ssl-trust-all must be set to protect credentials.
        if (hasAuth && !opts.isSsl() && !opts.isSslTrustAll()) {
            System.err.println("[remote-validation] WARNING: authentication credentials are being "
                + "sent over plain HTTP. Use --server-ssl to enable TLS.");
        }

        // Timeout
        long timeoutSec = opts.getExplainTimeout();
        builder.connectTimeout(timeoutSec, TimeUnit.SECONDS);
        builder.readTimeout(timeoutSec, TimeUnit.SECONDS);
        builder.writeTimeout(timeoutSec, TimeUnit.SECONDS);

        OkHttpClient httpClient = builder.build();

        // Build the server URI. The scheme is determined by --server-ssl / --server-ssl-trust-all.
        String server = opts.getServer();
        String scheme = (opts.isSsl() || opts.isSslTrustAll()) ? "https" : "http";
        URI serverUri;
        try {
            serverUri = URI.create(scheme + "://" + server);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid Trino server URI: " + scheme + "://" + server, e);
        }

        ClientSession clientSession = ClientSession.builder()
            .server(serverUri)
            .user(Optional.ofNullable(opts.getUser()))
            .catalog(catalog)
            .schema(schema)
            .timeZone(ZoneId.systemDefault())
            .locale(Locale.getDefault())
            .build();

        return new TrinoExplainClient(httpClient, clientSession);
    }

    /**
     * Executes {@code EXPLAIN (TYPE VALIDATE) <sql>} on the remote Trino server.
     *
     * <p>On success ({@code Valid = true}), returns {@link RemoteValidationResult#valid()}.
     * On failure, converts {@link QueryError} fields into {@link LintFinding} entries and
     * returns {@link RemoteValidationResult#failed(List)}.
     * On internal server error or unexpected state, returns {@link RemoteValidationResult#skipped()}
     * and emits a warning to stderr.
     *
     * <p>The {@code sql} parameter must be the original statement without any {@code EXPLAIN}
     * prefix. Passing a statement that already starts with {@code EXPLAIN} would produce a
     * malformed query.
     *
     * @param sql the original SQL statement (must not be {@code null} or start with EXPLAIN)
     * @return validation result
     * @throws IllegalArgumentException if {@code sql} is {@code null}
     */
    public RemoteValidationResult validate(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("sql must not be null");
        }
        String validateSql = "EXPLAIN (TYPE VALIDATE) " + sql;

        try (StatementClient client =
                StatementClientFactory.newStatementClient(this.httpClient, this.session, validateSql)) {
            while (client.isRunning()) {
                client.advance();
            }

            if (client.isClientAborted() || client.isClientError()) {
                System.err.println("[remote-validation] Client error during EXPLAIN (TYPE VALIDATE)");
                return RemoteValidationResult.skipped();
            }

            if (client.isFinished()) {
                QueryError err = client.finalStatusInfo().getError();
                if (err == null) {
                    // Valid = true — server accepted the query
                    return RemoteValidationResult.valid();
                }
                String errorType = err.getErrorType();
                if ("INTERNAL_ERROR".equals(errorType)) {
                    System.err.println(
                        "[remote-validation] Server internal error: " + err.getMessage());
                    return RemoteValidationResult.skipped();
                }
                return RemoteValidationResult.failed(toFindings(err));
            }

            // Unexpected state
            System.err.println(
                "[remote-validation] Unexpected client state after EXPLAIN (TYPE VALIDATE)");
            return RemoteValidationResult.skipped();

        } catch (Exception e) {
            System.err.println("[remote-validation] Connection error: " + e.getMessage());
            return RemoteValidationResult.skipped();
        }
    }

    /**
     * Converts a {@link QueryError} from Trino into a list of {@link LintFinding} entries.
     *
     * <p>The error name is mapped to a rule ID as follows:
     * <ul>
     *   <li>{@code TABLE_NOT_FOUND} → E002</li>
     *   <li>{@code COLUMN_NOT_FOUND} → E003</li>
     *   <li>{@code FUNCTION_NOT_FOUND} → E004</li>
     *   <li>{@code TYPE_MISMATCH} → E005</li>
     *   <li>Other {@code USER_ERROR} → W004</li>
     * </ul>
     *
     * @param err the query error from the server
     * @return list containing a single lint finding
     */
    private static List<LintFinding> toFindings(QueryError err) {
        List<LintFinding> findings = new ArrayList<>();
        String ruleId;
        LintFinding.Severity severity;

        String errorName = err.getErrorName() != null ? err.getErrorName() : "";
        switch (errorName) {
            case "TABLE_NOT_FOUND":
                ruleId = "E002";
                severity = LintFinding.Severity.ERROR;
                break;
            case "COLUMN_NOT_FOUND":
                ruleId = "E003";
                severity = LintFinding.Severity.ERROR;
                break;
            case "FUNCTION_NOT_FOUND":
                ruleId = "E004";
                severity = LintFinding.Severity.ERROR;
                break;
            case "TYPE_MISMATCH":
                ruleId = "E005";
                severity = LintFinding.Severity.ERROR;
                break;
            default:
                ruleId = "W004";
                severity = LintFinding.Severity.WARNING;
                break;
        }

        String message = buildMessage(err);
        findings.add(new LintFinding(ruleId, severity, message));
        return findings;
    }

    /**
     * Builds a human-readable message string from a {@link QueryError},
     * optionally appending location information when {@link ErrorLocation} is available.
     *
     * <p>Falls back to the error name, then to {@code "Unknown server error"} when
     * both {@code message} and {@code errorName} are {@code null}.
     *
     * @param err query error from the server
     * @return formatted message string (never {@code null})
     */
    private static String buildMessage(QueryError err) {
        String base;
        if (err.getMessage() != null) {
            base = err.getMessage();
        } else if (err.getErrorName() != null) {
            base = err.getErrorName();
        } else {
            base = "Unknown server error";
        }
        ErrorLocation loc = err.getErrorLocation();
        if (loc != null) {
            return base + " (line " + loc.getLineNumber() + ", col " + loc.getColumnNumber() + ")";
        }
        return base;
    }

    /**
     * Closes the underlying {@link OkHttpClient} and releases its resources.
     */
    @Override
    public void close() {
        this.httpClient.dispatcher().executorService().shutdown();
        this.httpClient.connectionPool().evictAll();
    }
}
