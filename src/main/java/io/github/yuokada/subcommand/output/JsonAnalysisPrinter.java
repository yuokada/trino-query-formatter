package io.github.yuokada.subcommand.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import java.io.IOException;

/**
 * JSON (NDJSON) printer supporting basic and full detail levels and optional AST embedding.
 */
public final class JsonAnalysisPrinter implements AnalysisPrinter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OutputEmitter emitter;
    private final boolean basicDetails;
    private final boolean showAst;
    private final int astLimit;

    /**
     * @param emitter      Output sink.
     * @param basicDetails When true, only emits queryType and catalogs.
     * @param showAst      When true, embeds AST as an additional field.
     */
    public JsonAnalysisPrinter(OutputEmitter emitter, boolean basicDetails, boolean showAst,
        int astLimit) {
        this.emitter = emitter;
        this.basicDetails = basicDetails;
        this.showAst = showAst;
        this.astLimit = astLimit;
    }

    @Override
    public void printStatement(QueryAnalysisResult result, Integer queryId, String originalSql)
        throws IOException {
        String json;
        if (basicDetails) {
            String ast = showAst ? limitAst(QueryAnalyzer.dumpAst(originalSql)) : null;
            json = buildBasicJson(result, ast);
        } else {
            json = result.toJson();
            if (showAst) {
                json = addAstToJson(json, limitAst(QueryAnalyzer.dumpAst(originalSql)));
            }
        }
        emitter.emit(json);
    }

    @Override
    public void close() throws IOException {
        emitter.close();
    }

    private static String buildBasicJson(QueryAnalysisResult r, String astOrNull) {
        try {
            ObjectNode json = MAPPER.createObjectNode();
            if (astOrNull != null) {
                json.put("ast", astOrNull);
            }
            json.put("queryType", r.getQueryType());
            json.putPOJO("catalogs", new java.util.TreeSet<>(r.getCatalogs()));
            if (r.getParseError() != null) {
                json.put("parseError", r.getParseError());
            }
            return MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build basic JSON", e);
        }
    }

    private static String addAstToJson(String json, String ast) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(json);
            // Insert AST as first field by creating a new node
            ObjectNode newNode = MAPPER.createObjectNode();
            newNode.put("ast", ast);
            node.fields().forEachRemaining(entry -> newNode.set(entry.getKey(), entry.getValue()));
            return MAPPER.writeValueAsString(newNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to add AST to JSON", e);
        }
    }

    private String limitAst(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= astLimit) {
            return s;
        }
        return s.substring(0, astLimit) + "\n... (truncated)";
    }

}
