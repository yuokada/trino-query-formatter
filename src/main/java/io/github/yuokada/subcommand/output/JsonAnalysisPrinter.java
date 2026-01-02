package io.github.yuokada.subcommand.output;

import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.core.util.JsonUtil;
import java.io.IOException;

/**
 * JSON (NDJSON) printer supporting basic and full detail levels and optional AST embedding.
 */
public final class JsonAnalysisPrinter implements AnalysisPrinter {

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
    public void printStatement(QueryAnalysisResult result, Integer queryId, String originalSql) {
        String json;
        if (basicDetails) {
            String ast = showAst ? JsonUtil.escape(limitAst(QueryAnalyzer.dumpAst(originalSql))) : null;
            json = buildBasicJson(result, ast);
        } else {
            json = result.toJson();
            if (showAst) {
                String ast = JsonUtil.escape(limitAst(QueryAnalyzer.dumpAst(originalSql)));
                if (json.startsWith("{")) {
                    String body = json.substring(1);
                    json = "{\"ast\":\"" + ast + "\"," + body;
                }
            }
        }
        emitter.emit(json);
    }

    @Override
    public void close() throws IOException {
        emitter.close();
    }

    private static String buildBasicJson(QueryAnalysisResult r, String astOrNull) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        if (astOrNull != null) {
            sb.append("\"ast\":\"").append(astOrNull).append("\",");
        }
        sb.append("\"queryType\":\"")
            .append(JsonUtil.escape(r.getQueryType()))
            .append("\",");
        sb.append("\"catalogs\":[");
        boolean first = true;
        for (String c : r.getCatalogs()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"").append(JsonUtil.escape(c)).append("\"");
            first = false;
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
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
