package io.github.yuokada.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Config defaults for the format subcommand.
 */
public class FormatConfig {

    /**
     * Check mode default.
     */
    private Boolean check;

    /**
     * Diff mode default.
     */
    private Boolean diff;

    /**
     * Keyword case default.
     */
    @JsonProperty("keyword-case")
    private String keywordCase;

    public Boolean getCheck() {
        return this.check;
    }

    public void setCheck(Boolean check) {
        this.check = check;
    }

    public Boolean getDiff() {
        return this.diff;
    }

    public void setDiff(Boolean diff) {
        this.diff = diff;
    }

    public String getKeywordCase() {
        return this.keywordCase;
    }

    public void setKeywordCase(String keywordCase) {
        this.keywordCase = keywordCase;
    }
}
