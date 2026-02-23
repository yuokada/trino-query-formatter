package io.github.yuokada.core;

/**
 * AST display mode used with {@code analyze --show-ast}.
 *
 * <ul>
 *   <li>{@code TREE} — human-friendly indented tree with key attributes (default).</li>
 *   <li>{@code OUTLINE} — clause-focused one-line-per-clause summary.</li>
 *   <li>{@code RAW} — original class-name based tree (backward compatibility).</li>
 * </ul>
 */
public enum AstView {

    /**
     * Human-friendly indented tree with key attributes on significant nodes.
     */
    TREE,

    /**
     * Clause-focused summary showing structural elements (WITH/SELECT/FROM/JOIN/WHERE etc.).
     */
    OUTLINE,

    /**
     * Raw class-name based tree (same as the original {@code --show-ast} output).
     */
    RAW;

    /**
     * Parses an {@code AstView} from a string value (case-insensitive).
     *
     * @param value the string to parse
     * @return the matching {@code AstView}
     * @throws IllegalArgumentException when no match is found
     */
    public static AstView fromString(String value) {
        for (AstView v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException(
            "Invalid --ast-view value: '" + value + "'. Expected: tree, outline, or raw");
    }
}
