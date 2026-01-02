package io.github.yuokada.core;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.DelimiterLexer;
import io.trino.grammar.sql.SqlBaseParser;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.AllColumns;
import io.trino.sql.tree.CreateCatalog;
import io.trino.sql.tree.CreateTable;
import io.trino.sql.tree.CreateTableAsSelect;
import io.trino.sql.tree.Delete;
import io.trino.sql.tree.DropCatalog;
import io.trino.sql.tree.DropTable;
import io.trino.sql.tree.FunctionCall;
import io.trino.sql.tree.Insert;
import io.trino.sql.tree.Join;
import io.trino.sql.tree.Limit;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Query;
import io.trino.sql.tree.ShowCatalogs;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.Update;
import io.trino.sql.tree.With;
import io.trino.sql.tree.WithQuery;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.jboss.logging.Logger;

public final class QueryAnalyzer {

    private QueryAnalyzer() {
    }

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = Logger.getLogger(QueryAnalyzer.class);

    /**
     * SQL parser for this class.
     */
    private static final SqlParser sqlParser = new SqlParser();

    /**
     * Extracts catalogs from a given node.
     *
     * @param node The node to extract catalogs from.
     * @return A set of catalogs.
     */
    private static Set<String> extractCatalogs(Node node) {
        Set<String> catalogs = new HashSet<>();
        if (node instanceof Table table) {
            QualifiedName name = table.getName();
            if (name.getPrefix().isPresent()) {
                catalogs.add(name.getPrefix().get().getOriginalParts().get(0).toString());
            }
        }
        for (Node child : node.getChildren()) {
            catalogs.addAll(extractCatalogs(child));
        }
        return catalogs;
    }

    /**
     * Collects catalogs from a given SQL query.
     *
     * @param sql The SQL query.
     * @return A set of catalogs.
     */
    public static Set<String> collectCatalogs(String sql) {
        Statement statement = sqlParser.createStatement(sql);
        Set<String> catalogs = new HashSet<>();
        for (Node child : statement.getChildren()) {
            catalogs.addAll(extractCatalogs(child));
        }
        return catalogs;
    }

    /**
     * Performs a basic analysis for a single SQL statement.
     * Focuses on query type, catalogs, tables and simple flags.
     *
     * @param sql SQL statement text
     * @return analysis result for the statement
     */
    public static QueryAnalysisResult analyze(String sql) {
        return analyze(sql, null, null);
    }

    /**
     * Performs analysis with optional default catalog/schema for name resolution.
     *
     * @param sql            SQL statement text
     * @param defaultCatalog default catalog for un/partially qualified names (nullable)
     * @param defaultSchema  default schema for unqualified names (nullable)
     * @return analysis result for the statement
     */
    public static QueryAnalysisResult analyze(String sql, String defaultCatalog,
        String defaultSchema) {
        QueryAnalysisResult.Builder b = new QueryAnalysisResult.Builder();
        try {
            Statement statement = sqlParser.createStatement(sql);
            // Query type
            b.queryType(detectQueryType(sql));
            // Catalogs and tables
            Set<String> catalogs = new HashSet<>();
            Set<String> tables = new HashSet<>();
            collectTablesAndCatalogs(statement, tables, catalogs, defaultCatalog, defaultSchema);
            b.addAllCatalogs(catalogs).addAllTables(tables);
            // Flags
            b.usesSelectStar(containsNode(statement, AllColumns.class));
            b.hasLimit(containsNode(statement, Limit.class));
            if (statement instanceof Delete del) {
                b.hasWhereOnDelete(del.getWhere().isPresent());
            }
            // Extended details
            collectExtendedDetails(statement, b, defaultCatalog, defaultSchema, catalogs);
            return b.build();
        } catch (RuntimeException e) {
            // Return partial info with parse error message
            return b.queryType("Unknown").parseError(e.getMessage()).build();
        }
    }

    /**
     * Collects table and catalog references from the AST.
     *
     * @param node           root AST node
     * @param tables         target set to gather fully qualified table names
     * @param catalogs       target set to gather catalog names
     * @param defaultCatalog default catalog for un/partially qualified names (nullable)
     * @param defaultSchema  default schema for unqualified names (nullable)
     */
    private static void collectTablesAndCatalogs(Node node, Set<String> tables,
        Set<String> catalogs, String defaultCatalog, String defaultSchema) {
        if (node instanceof Table table) {
            QualifiedName qn = table.getName();
            // Join original parts to preserve quoted identifiers as much as possible
            String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
            tables.add(joined);
        } else if (node instanceof Insert ins) {
            // Include target table name as a table reference
            QualifiedName qn = ins.getTarget();
            if (qn != null) {
                String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
                tables.add(joined);
            }
        } else if (node instanceof CreateTable ct) {
            QualifiedName qn = ct.getName();
            String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
            tables.add(joined);
        } else if (node instanceof CreateTableAsSelect ctas) {
            QualifiedName qn = ctas.getName();
            String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
            tables.add(joined);
        }
        for (Node child : node.getChildren()) {
            collectTablesAndCatalogs(child, tables, catalogs, defaultCatalog, defaultSchema);
        }
    }

    /**
     * Collects extended information: CTE names, join descriptors, function usage and write targets.
     *
     * @param node           root AST node
     * @param b              result builder to receive findings
     * @param defaultCatalog default catalog (nullable)
     * @param defaultSchema  default schema (nullable)
     * @param catalogs       collected catalog names (for updates during resolution)
     */
    private static void collectExtendedDetails(Node node, QueryAnalysisResult.Builder b,
        String defaultCatalog, String defaultSchema, java.util.Set<String> catalogs) {
        if (node instanceof With with) {
            for (WithQuery wq : with.getQueries()) {
                b.addCte(wq.getName().getValue());
            }
        } else if (node instanceof Join join) {
            String type = join.getType().name();
            String desc = type + ":" + (join.getCriteria().isPresent() ? "on" : "none");
            b.addJoin(desc);
        } else if (node instanceof FunctionCall fc) {
            String fn = fc.getName().toString();
            String lower = fn.toLowerCase();
            if (fc.getWindow().isPresent()) {
                b.addFunctionWindow(lower);
            } else if (isAggregateName(lower)) {
                b.addFunctionAggregate(lower);
            } else {
                b.addFunctionScalar(lower);
            }
        } else if (node instanceof Insert ins) {
            QualifiedName qn = ins.getTarget();
            if (qn != null) {
                String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
                b.addWriteTarget(joined);
            }
        } else if (node instanceof CreateTable ct) {
            QualifiedName qn = ct.getName();
            String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
            b.addWriteTarget(joined);
        } else if (node instanceof CreateTableAsSelect ctas) {
            QualifiedName qn = ctas.getName();
            String joined = resolveName(qn, defaultCatalog, defaultSchema, catalogs);
            b.addWriteTarget(joined);
        }
        for (Node child : node.getChildren()) {
            collectExtendedDetails(child, b, defaultCatalog, defaultSchema, catalogs);
        }
    }

    /**
     * Checks if a function name is a common aggregate function.
     *
     * @param lower function name in lower case
     * @return true when name is a known aggregate
     */
    private static boolean isAggregateName(String lower) {
        return lower.equals("count") || lower.equals("sum") || lower.equals("avg")
            || lower.equals("min") || lower.equals("max") || lower.equals("array_agg")
            || lower.equals("approx_distinct");
    }

    /**
     * Checks if the given AST contains a node of the specified type.
     */
    private static boolean containsNode(Node node, Class<? extends Node> clazz) {
        if (clazz.isInstance(node)) {
            return true;
        }
        for (Node child : node.getChildren()) {
            if (containsNode(child, clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dumps tokens from a given SQL query.
     *
     * @param sql The SQL query.
     */
    public static void dumpToken(String sql) {
        CharStream stream = CharStreams.fromString(sql);
        TokenSource lexer = new DelimiterLexer(stream, ImmutableSet.of(";", "\\G"));
        var toContinue = true;
        while (toContinue) {
            Token token = lexer.nextToken();
            LOGGER.debug("Token: " + token);
            if (token.getType() == Token.EOF) {
                toContinue = false;
            } else if (token.getType() == SqlBaseParser.DELIMITER) {
                System.out.println(token.getText());
            } else if (token.getType() == SqlBaseParser.EMPTY
                || token.getType() == SqlBaseParser.COMMENT
                || token.getType() == SqlBaseParser.WS) {
                // Do nothing
                LOGGER.debug("Skip: " + token);
            } else {
                // System.out.println("\"" + token + "\"");
                System.out.println(token.getText());
            }
        }
    }

    /**
     * Builds a simple string representation of the AST with indentation.
     * Intended for debugging and --show-ast output.
     *
     * @param sql SQL statement text
     * @return Multi-line string describing node classes and key identifiers
     */
    public static String dumpAst(String sql) {
        Statement statement = sqlParser.createStatement(sql);
        StringBuilder sb = new StringBuilder();
        dumpNodeRecursive(statement, sb, 0);
        return sb.toString();
    }

    private static void dumpNodeRecursive(Node node, StringBuilder sb, int depth) {
        indent(sb, depth);
        sb.append(node.getClass().getSimpleName());
        if (node instanceof Table t) {
            QualifiedName qn = t.getName();
            String joined = String.join(".",
                qn.getOriginalParts().stream().map(Object::toString).toList());
            sb.append("[name=").append(joined).append("]");
        } else if (node instanceof Insert ins) {
            QualifiedName qn = ins.getTarget();
            if (qn != null) {
                String joined = String.join(".",
                    qn.getOriginalParts().stream().map(Object::toString).toList());
                sb.append("[target=").append(joined).append("]");
            }
        } else if (node instanceof CreateTable ct) {
            QualifiedName qn = ct.getName();
            String joined = String.join(".",
                qn.getOriginalParts().stream().map(Object::toString).toList());
            sb.append("[name=").append(joined).append("]");
        } else if (node instanceof CreateTableAsSelect ctas) {
            QualifiedName qn = ctas.getName();
            String joined = String.join(".",
                qn.getOriginalParts().stream().map(Object::toString).toList());
            sb.append("[name=").append(joined).append("]");
        }
        sb.append('\n');
        for (Node child : node.getChildren()) {
            dumpNodeRecursive(child, sb, depth + 1);
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    /**
     * Resolves a QualifiedName into a fully qualified name string, applying defaults when provided.
     * Also updates the given catalogs set when a catalog can be determined.
     */
    private static String resolveName(QualifiedName qn, String defaultCatalog, String defaultSchema,
        java.util.Set<String> catalogs) {
        var parts = qn.getOriginalParts();
        int size = parts.size();
        if (size >= 3) {
            return resolveFullyQualifiedName(parts, catalogs);
        } else if (size == 2) {
            return resolveSemiQualifiedName(parts, defaultCatalog, catalogs);
        } else { // size == 1
            return resolveUnqualifiedName(parts, defaultCatalog, defaultSchema, catalogs);
        }
    }

    private static String resolveFullyQualifiedName(java.util.List<?> parts,
        java.util.Set<String> catalogs) {
        catalogs.add(parts.get(0).toString());
        return String.join(".", parts.stream().map(Object::toString).toList());
    }

    private static String resolveSemiQualifiedName(java.util.List<?> parts, String defaultCatalog,
        java.util.Set<String> catalogs) {
        String schema = parts.get(0).toString();
        String table = parts.get(1).toString();
        if (defaultCatalog != null && !defaultCatalog.isEmpty()) {
            catalogs.add(defaultCatalog);
            return String.join(".", defaultCatalog, schema, table);
        }
        return String.join(".", schema, table);
    }

    private static String resolveUnqualifiedName(java.util.List<?> parts, String defaultCatalog,
        String defaultSchema, java.util.Set<String> catalogs) {
        String table = parts.get(0).toString();
        if (defaultCatalog != null && !defaultCatalog.isEmpty()
            && defaultSchema != null && !defaultSchema.isEmpty()) {
            catalogs.add(defaultCatalog);
            return String.join(".", defaultCatalog, defaultSchema, table);
        }
        return table;
    }

    /**
     * Detects the query type of a given SQL query.
     *
     * @param sql The SQL query.
     * @return The query type.
     */
    public static String detectQueryType(String sql) {
        Statement statement = sqlParser.createStatement(sql);
        if (statement instanceof Query
            || statement instanceof Insert
            || statement instanceof Delete
            || statement instanceof Update) {
            return statement.getClass().getSimpleName();
        } else if (statement instanceof CreateTable) {
            return statement.getClass().getSimpleName();
        } else if (statement instanceof DropTable) {
            return statement.getClass().getSimpleName();
        } else if (statement instanceof ShowCatalogs
            || statement instanceof CreateCatalog
            || statement instanceof DropCatalog) {
            return statement.getClass().getSimpleName();
        } else {
            return String.format("Unknown: %s", statement.getClass().getSimpleName());
        }
    }

}
