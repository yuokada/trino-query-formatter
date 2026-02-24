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
     */
    public static TrinoExplainClient create(
            TrinoConnectionOptions opts, String catalog, String schema) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Authentication
        String token = opts.getAccessToken();
        String password = opts.getPassword();
        if (token != null && !token.isBlank()) {
            builder.addInterceptor(OkHttpUtil.tokenAuth(token));
        } else if (password != null && !password.isBlank()) {
            builder.addInterceptor(OkHttpUtil.basicAuth(opts.getUser(), password));
        }

        // SSL
        if (opts.isSslTrustAll()) {
            OkHttpUtil.setupInsecureSsl(builder);
        }

        // Timeout
        long timeoutSec = opts.getExplainTimeout();
        builder.connectTimeout(timeoutSec, TimeUnit.SECONDS);
        builder.readTimeout(timeoutSec, TimeUnit.SECONDS);
        builder.writeTimeout(timeoutSec, TimeUnit.SECONDS);

        OkHttpClient httpClient = builder.build();

        // Build the server URI
        String server = opts.getServer();
        String scheme = opts.isSsl() ? "https" : "http";
        URI serverUri = URI.create(scheme + "://" + server);

        ClientSession clientSession = ClientSession.builder()
            .server(serverUri)
            .user(Optional.of(opts.getUser()))
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
     * @param sql the original SQL statement (without the {@code EXPLAIN} prefix)
     * @return validation result
     */
    public RemoteValidationResult validate(String sql) {
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
            System.err.println("[remote-validation] Unexpected client state after EXPLAIN (TYPE VALIDATE)");
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

        String message = buildMessage(ruleId, err);
        findings.add(new LintFinding(ruleId, severity, message));
        return findings;
    }

    /**
     * Builds a human-readable message string from a {@link QueryError},
     * optionally appending location information when {@link ErrorLocation} is available.
     *
     * @param ruleId rule identifier for the message prefix
     * @param err    query error from the server
     * @return formatted message string
     */
    private static String buildMessage(String ruleId, QueryError err) {
        String base = err.getMessage() != null ? err.getMessage() : err.getErrorName();
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
