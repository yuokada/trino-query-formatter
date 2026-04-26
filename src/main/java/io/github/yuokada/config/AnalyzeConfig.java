package io.github.yuokada.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Config defaults for the analyze subcommand.
 */
public class AnalyzeConfig {

    /**
     * Output format default.
     */
    private String format;

    /**
     * Detail level default.
     */
    private String details;

    /**
     * Validate functions default.
     */
    @JsonProperty("validate-functions")
    private Boolean validateFunctions;

    /**
     * UDF catalog path default.
     */
    @JsonProperty("udf-catalog")
    private String udfCatalog;

    /**
     * Remote server default.
     */
    private String server;

    public String getFormat() {
        return this.format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDetails() {
        return this.details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Boolean getValidateFunctions() {
        return this.validateFunctions;
    }

    public void setValidateFunctions(Boolean validateFunctions) {
        this.validateFunctions = validateFunctions;
    }

    public String getUdfCatalog() {
        return this.udfCatalog;
    }

    public void setUdfCatalog(String udfCatalog) {
        this.udfCatalog = udfCatalog;
    }

    public String getServer() {
        return this.server;
    }

    public void setServer(String server) {
        this.server = server;
    }
}
