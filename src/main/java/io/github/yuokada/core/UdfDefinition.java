package io.github.yuokada.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single UDF entry loaded from a YAML UDF catalog file.
 *
 * <p>A definition may specify arity constraints in one of the following ways:
 * <ul>
 *   <li>{@code arity} — exact argument count. Takes precedence over {@code minArgs}/{@code maxArgs}.
 *   </li>
 *   <li>{@code minArgs} / {@code maxArgs} — lower and/or upper bound on argument count.</li>
 *   <li>Neither — the function is considered "known" but no arity check is performed (W003
 *       is never emitted for this function).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfDefinition {

    /**
     * Function name (case-insensitive; stored in original case from YAML).
     */
    public String name;

    /**
     * Optional description (not used for validation).
     */
    public String description;

    /**
     * Exact expected argument count. When non-null, takes precedence over
     * {@code minArgs} and {@code maxArgs}.
     */
    public Integer arity;

    /**
     * Minimum number of arguments (inclusive). Ignored when {@code arity} is non-null.
     */
    public Integer minArgs;

    /**
     * Maximum number of arguments (inclusive). {@code null} means no upper bound.
     * Ignored when {@code arity} is non-null.
     */
    public Integer maxArgs;

    /**
     * Returns a human-readable description of the expected argument count.
     *
     * <p>Examples: {@code "2"}, {@code "at least 1"}, {@code "between 2 and 4"}.
     * Returns {@code null} when no arity constraint is defined.
     *
     * @return arity description string, or {@code null} if unconstrained
     */
    public String expectedArityDescription() {
        if (this.arity != null) {
            return String.valueOf(this.arity);
        }
        if (this.minArgs != null && this.maxArgs != null) {
            return "between " + this.minArgs + " and " + this.maxArgs;
        }
        if (this.minArgs != null) {
            return "at least " + this.minArgs;
        }
        if (this.maxArgs != null) {
            return "at most " + this.maxArgs;
        }
        return null;
    }

    /**
     * Returns {@code true} when the given actual argument count satisfies this definition's
     * arity constraints.
     *
     * <p>When no constraint is defined (all fields are {@code null}), this method always
     * returns {@code true} (no arity check performed).
     *
     * @param actualArgs the number of arguments in the function call
     * @return {@code true} when arity is valid, {@code false} on mismatch
     */
    public boolean isArityValid(int actualArgs) {
        if (this.arity != null) {
            return actualArgs == this.arity;
        }
        if (this.minArgs != null && actualArgs < this.minArgs) {
            return false;
        }
        if (this.maxArgs != null && actualArgs > this.maxArgs) {
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} when this definition specifies at least one arity constraint
     * ({@code arity}, {@code minArgs}, or {@code maxArgs}).
     *
     * @return {@code true} if an arity check should be performed
     */
    public boolean hasArityConstraint() {
        return this.arity != null || this.minArgs != null || this.maxArgs != null;
    }
}
