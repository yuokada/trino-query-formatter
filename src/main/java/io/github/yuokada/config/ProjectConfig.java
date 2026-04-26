package io.github.yuokada.config;

/**
 * Root config schema for .trino-query-formatter.yml.
 */
public class ProjectConfig {

    /**
     * Analyze defaults.
     */
    private AnalyzeConfig analyze = new AnalyzeConfig();

    /**
     * Format defaults.
     */
    private FormatConfig format = new FormatConfig();

    /**
     * Lint defaults.
     */
    private LintConfig lint = new LintConfig();

    public AnalyzeConfig getAnalyze() {
        return this.analyze;
    }

    public void setAnalyze(AnalyzeConfig analyze) {
        this.analyze = analyze;
    }

    public FormatConfig getFormat() {
        return this.format;
    }

    public void setFormat(FormatConfig format) {
        this.format = format;
    }

    public LintConfig getLint() {
        return this.lint;
    }

    public void setLint(LintConfig lint) {
        this.lint = lint;
    }
}
