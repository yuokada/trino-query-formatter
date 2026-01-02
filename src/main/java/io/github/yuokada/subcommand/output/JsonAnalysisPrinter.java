package io.github.yuokada.subcommand.output;

import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
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
            String ast =
                showAst ? escapeJsonString(limitAst(QueryAnalyzer.dumpAst(originalSql))) : null;
            json = buildBasicJson(result, ast);
        } else {
            json = result.toJson();
            if (showAst) {
                String ast = escapeJsonString(limitAst(QueryAnalyzer.dumpAst(originalSql)));
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
            .append(escapeJsonString(r.getQueryType()))
            .append("\",");
        sb.append("\"catalogs\":[");
        boolean first = true;
        for (String c : r.getCatalogs()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"").append(escapeJsonString(c)).append("\"");
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

    private static String escapeJsonString(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
