package io.github.yuokada.benchmarks;

import io.github.yuokada.api.AnalyzerApi;
import io.github.yuokada.api.FormatterApi;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for parser, formatter, and analyzer hot paths.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class PerformanceBenchmarks {

    private SqlParser parser;
    private List<String> largeStatementCorpus;
    private String representativeSelect;

    /**
     * Prepares reusable SQL corpora for each benchmark trial.
     */
    @Setup
    public void setUp() {
        this.parser = new SqlParser();
        this.largeStatementCorpus = generateStatements(1000);
        this.representativeSelect = """
            SELECT o.orderkey, o.custkey, c.name, sum(l.extendedprice) AS revenue
            FROM orders o
            JOIN customer c ON (o.custkey = c.custkey)
            JOIN lineitem l ON (o.orderkey = l.orderkey)
            WHERE o.orderstatus = 'F'
            GROUP BY o.orderkey, o.custkey, c.name
            ORDER BY revenue DESC
            LIMIT 50
            """;
    }

    /**
     * Parses a large corpus of independent SQL statements.
     *
     * @param blackhole consumes parsed statements
     */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void parseLargeFile(Blackhole blackhole) {
        for (String sql : this.largeStatementCorpus) {
            Statement statement = this.parser.createStatement(sql);
            blackhole.consume(statement);
        }
    }

    /**
     * Formats a representative single SELECT statement.
     *
     * @return formatted SQL
     */
    @Benchmark
    public String formatSingleStatement() {
        return FormatterApi.formatStatement(this.representativeSelect, KeywordCase.UPPER, 2);
    }

    /**
     * Runs full analyzer logic across a large statement corpus.
     *
     * @param blackhole consumes analysis results
     */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void analyzeFull(Blackhole blackhole) {
        for (String sql : this.largeStatementCorpus) {
            blackhole.consume(AnalyzerApi.analyzeStatement(sql, null, null, null, null));
        }
    }

    private static List<String> generateStatements(int count) {
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            statements.add("SELECT orderkey, custkey, totalprice FROM orders WHERE orderkey = "
                + (i + 1));
        }
        return statements;
    }
}
