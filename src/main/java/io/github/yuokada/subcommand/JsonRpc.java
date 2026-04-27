package io.github.yuokada.subcommand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yuokada.api.AnalyzerApi;
import io.github.yuokada.api.FormatterApi;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.github.yuokada.core.QueryAnalysisResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * Minimal JSON-RPC 2.0 endpoint over stdin/stdout for editor integrations.
 */
@CommandLine.Command(
    name = "json-rpc",
    description = "Run a JSON-RPC 2.0 endpoint over stdin/stdout.")
public class JsonRpc implements Callable<Integer> {

    /**
     * Shared JSON mapper.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JsonNode id = null;
                try {
                    JsonNode request = JSON.readTree(trimmed);
                    id = request.get("id");
                    JsonNode response = handleRequest(request);
                    System.out.println(JSON.writeValueAsString(response));
                } catch (RuntimeException | IOException e) {
                    ObjectNode response = error(id, -32700, "Parse error: " + e.getMessage());
                    System.out.println(JSON.writeValueAsString(response));
                }
            }
        }
        return ExitCodes.OK;
    }

    private static JsonNode handleRequest(JsonNode request) throws IOException {
        if (!request.isObject()) {
            return error(null, -32600, "Invalid Request");
        }
        JsonNode id = request.get("id");
        String method = asText(request.get("method"));
        if (method == null) {
            return error(id, -32600, "Invalid Request: missing method");
        }
        JsonNode params = request.path("params");
        return switch (method) {
            case "format" -> handleFormat(id, params);
            case "analyze" -> handleAnalyze(id, params);
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    private static JsonNode handleFormat(JsonNode id, JsonNode params) {
        String sql = sanitizeSql(asText(params.get("sql")));
        if (sql == null || sql.isBlank()) {
            return error(id, -32602, "Invalid params: sql is required");
        }

        String mode = asText(params.get("keywordCase"));
        KeywordCase keywordCase = KeywordCase.UPPER;
        if (mode != null) {
            try {
                keywordCase = KeywordCase.fromString(mode.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return error(id, -32602, "Invalid params: " + e.getMessage());
            }
        }

        int indentSize = 2;
        JsonNode indentNode = params.get("indentSize");
        if (indentNode != null && indentNode.isInt()) {
            indentSize = indentNode.intValue();
        }
        if (indentSize < 1) {
            return error(id, -32602, "Invalid params: indentSize must be >= 1");
        }

        String formatted = FormatterApi.formatStatement(sql, keywordCase, indentSize);
        ObjectNode result = JSON.createObjectNode();
        result.put("sql", formatted);
        return success(id, result);
    }

    private static JsonNode handleAnalyze(JsonNode id, JsonNode params) throws IOException {
        String sql = sanitizeSql(asText(params.get("sql")));
        if (sql == null || sql.isBlank()) {
            return error(id, -32602, "Invalid params: sql is required");
        }
        QueryAnalysisResult result = AnalyzerApi.analyzeStatement(sql, null, null, null, null);
        ObjectNode payload = JSON.createObjectNode();
        payload.set("analysis", JSON.readTree(result.toJson()));
        return success(id, payload);
    }

    private static ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode node = JSON.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (id != null) {
            node.set("id", id);
        } else {
            node.putNull("id");
        }
        node.set("result", result);
        return node;
    }

    private static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (id != null) {
            node.set("id", id);
        } else {
            node.putNull("id");
        }
        ObjectNode err = JSON.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        node.set("error", err);
        return node;
    }

    private static String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return null;
        }
        return node.textValue();
    }

    private static String sanitizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
