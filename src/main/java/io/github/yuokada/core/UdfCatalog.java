package io.github.yuokada.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a YAML UDF catalog file and provides a map of function definitions.
 *
 * <p>Expected YAML format:
 * <pre>{@code
 * functions:
 *   - name: my_etl_transform
 *     description: "ETL transformation function"
 *     arity: 2
 *   - name: my_variadic_udf
 *     minArgs: 1
 * }</pre>
 *
 * <p>Function names are stored in lowercase for case-insensitive lookup.
 */
public final class UdfCatalog {

    private UdfCatalog() {
    }

    /**
     * Shared YAML-capable mapper; instantiated once to avoid per-call allocation overhead.
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Root element of the YAML document.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Root {
        /**
         * List of UDF definitions.
         */
        public List<UdfDefinition> functions;
    }

    /**
     * Loads and parses a YAML UDF catalog file.
     *
     * <p>Lines starting with {@code #} are treated as YAML comments and ignored.
     * Function names in the returned map are normalized to lowercase.
     *
     * @param path path to the YAML file (UTF-8 encoded)
     * @return unmodifiable map from lowercase function name to its definition
     * @throws IOException if the file cannot be read or parsed
     */
    public static Map<String, UdfDefinition> load(Path path) throws IOException {
        Root root = YAML_MAPPER.readValue(path.toFile(), Root.class);
        if (root == null || root.functions == null) {
            return Collections.emptyMap();
        }
        Map<String, UdfDefinition> result = new LinkedHashMap<>();
        for (UdfDefinition def : root.functions) {
            if (def != null && def.name != null && !def.name.isBlank()) {
                result.put(def.name.toLowerCase(), def);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
