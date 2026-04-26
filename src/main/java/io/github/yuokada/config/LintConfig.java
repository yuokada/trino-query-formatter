package io.github.yuokada.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Lint-related config values.
 */
public class LintConfig {

    /**
     * Rule IDs disabled by default.
     */
    @JsonProperty("disable-rules")
    private List<String> disableRules = new ArrayList<>();

    /**
     * Rule IDs force-enabled by default.
     */
    @JsonProperty("enable-rules")
    private List<String> enableRules = new ArrayList<>();

    public List<String> getDisableRules() {
        return this.disableRules;
    }

    public void setDisableRules(List<String> disableRules) {
        this.disableRules = disableRules;
    }

    public List<String> getEnableRules() {
        return this.enableRules;
    }

    public void setEnableRules(List<String> enableRules) {
        this.enableRules = enableRules;
    }
}
