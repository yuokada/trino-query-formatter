package io.github.yuokada.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Catalog of Trino 435 built-in function names (all lowercase).
 *
 * <p>Function names are stored in three categories:
 * <ul>
 *   <li>Scalar functions — math, string, date/time, type conversion, array, map, JSON, etc.</li>
 *   <li>Aggregate functions — COUNT, SUM, AVG and statistical aggregates.</li>
 *   <li>Window functions — ROW_NUMBER, RANK, LEAD, LAG, etc.</li>
 * </ul>
 *
 * <p>This catalog is used by the UDF validation feature to identify function calls
 * that are not part of the Trino built-in function set.
 */
public final class TrinoBuiltinFunctions {

    /**
     * Trino built-in aggregate function names (lowercase).
     */
    private static final Set<String> AGGREGATE_FUNCTIONS;

    /**
     * Trino built-in window function names (lowercase).
     */
    private static final Set<String> WINDOW_FUNCTIONS;

    /**
     * Trino built-in scalar function names (lowercase).
     */
    private static final Set<String> SCALAR_FUNCTIONS;

    /**
     * Union of all three categories.
     */
    private static final Set<String> ALL_BUILTIN_FUNCTIONS;

    static {
        Set<String> agg = new HashSet<>();
        // General aggregates
        agg.add("count");
        agg.add("count_if");
        agg.add("sum");
        agg.add("avg");
        agg.add("min");
        agg.add("max");
        agg.add("arbitrary");
        agg.add("every");
        agg.add("bool_and");
        agg.add("bool_or");
        agg.add("bitwise_and_agg");
        agg.add("bitwise_or_agg");
        agg.add("bitwise_xor_agg");
        agg.add("checksum");
        agg.add("geometric_mean");
        agg.add("max_by");
        agg.add("min_by");
        // Statistical
        agg.add("corr");
        agg.add("covar_pop");
        agg.add("covar_samp");
        agg.add("kurtosis");
        agg.add("skewness");
        agg.add("stddev");
        agg.add("stddev_pop");
        agg.add("stddev_samp");
        agg.add("variance");
        agg.add("var_pop");
        agg.add("var_samp");
        agg.add("regr_slope");
        agg.add("regr_intercept");
        // Array/map aggregates
        agg.add("array_agg");
        agg.add("multimap_agg");
        agg.add("map_agg");
        agg.add("map_union");
        agg.add("map_union_sum");
        agg.add("histogram");
        agg.add("set_agg");
        agg.add("set_union");
        agg.add("reduce_agg");
        agg.add("list_agg");
        // Approximate
        agg.add("approx_distinct");
        agg.add("approx_most_frequent");
        agg.add("approx_percentile");
        agg.add("approx_set");
        agg.add("merge");
        agg.add("numeric_histogram");
        agg.add("qdigest_agg");
        agg.add("tdigest_agg");
        // Classification
        agg.add("classification_fall_out");
        agg.add("classification_miss_rate");
        agg.add("classification_precision");
        agg.add("classification_recall");
        agg.add("classification_thresholds");
        AGGREGATE_FUNCTIONS = Collections.unmodifiableSet(agg);

        Set<String> win = new HashSet<>();
        win.add("row_number");
        win.add("rank");
        win.add("dense_rank");
        win.add("percent_rank");
        win.add("cume_dist");
        win.add("ntile");
        win.add("lag");
        win.add("lead");
        win.add("first_value");
        win.add("last_value");
        win.add("nth_value");
        WINDOW_FUNCTIONS = Collections.unmodifiableSet(win);

        Set<String> scalar = new HashSet<>();
        // ---- Math ----
        scalar.add("abs");
        scalar.add("cbrt");
        scalar.add("ceil");
        scalar.add("ceiling");
        scalar.add("degrees");
        scalar.add("e");
        scalar.add("exp");
        scalar.add("floor");
        scalar.add("from_base");
        scalar.add("infinity");
        scalar.add("is_finite");
        scalar.add("is_infinite");
        scalar.add("is_nan");
        scalar.add("ln");
        scalar.add("log");
        scalar.add("log2");
        scalar.add("log10");
        scalar.add("mod");
        scalar.add("nan");
        scalar.add("pi");
        scalar.add("pow");
        scalar.add("power");
        scalar.add("radians");
        scalar.add("rand");
        scalar.add("random");
        scalar.add("round");
        scalar.add("sign");
        scalar.add("sqrt");
        scalar.add("to_base");
        scalar.add("truncate");
        scalar.add("width_bucket");
        // Trigonometry
        scalar.add("acos");
        scalar.add("asin");
        scalar.add("atan");
        scalar.add("atan2");
        scalar.add("cos");
        scalar.add("cosh");
        scalar.add("sin");
        scalar.add("sinh");
        scalar.add("tan");
        scalar.add("tanh");
        scalar.add("inverse_normal_cdf");
        scalar.add("normal_cdf");
        scalar.add("inverse_beta_cdf");
        scalar.add("beta_cdf");
        scalar.add("wilson_interval_lower");
        scalar.add("wilson_interval_upper");
        // ---- String ----
        scalar.add("chr");
        scalar.add("codepoint");
        scalar.add("concat");
        scalar.add("concat_ws");
        scalar.add("format");
        scalar.add("from_utf8");
        scalar.add("hamming_distance");
        scalar.add("index_of");
        scalar.add("length");
        scalar.add("levenshtein_distance");
        scalar.add("lower");
        scalar.add("lpad");
        scalar.add("ltrim");
        scalar.add("normalize");
        scalar.add("position");
        scalar.add("repeat");
        scalar.add("replace");
        scalar.add("reverse");
        scalar.add("rpad");
        scalar.add("rtrim");
        scalar.add("soundex");
        scalar.add("split");
        scalar.add("split_part");
        scalar.add("split_to_map");
        scalar.add("split_to_multimap");
        scalar.add("starts_with");
        scalar.add("strpos");
        scalar.add("substr");
        scalar.add("substring");
        scalar.add("to_utf8");
        scalar.add("translate");
        scalar.add("trim");
        scalar.add("upper");
        scalar.add("word_stem");
        // ---- Date / Time ----
        scalar.add("at_timezone");
        scalar.add("current_date");
        scalar.add("current_time");
        scalar.add("current_timestamp");
        scalar.add("current_timezone");
        scalar.add("date");
        scalar.add("date_add");
        scalar.add("date_diff");
        scalar.add("date_format");
        scalar.add("date_parse");
        scalar.add("date_trunc");
        scalar.add("day");
        scalar.add("day_of_month");
        scalar.add("day_of_week");
        scalar.add("day_of_year");
        scalar.add("format_datetime");
        scalar.add("from_iso8601_date");
        scalar.add("from_iso8601_timestamp");
        scalar.add("from_iso8601_timestamp_nanos");
        scalar.add("from_unixtime");
        scalar.add("from_unixtime_nanos");
        scalar.add("hour");
        scalar.add("last_day_of_month");
        scalar.add("localtime");
        scalar.add("localtimestamp");
        scalar.add("millisecond");
        scalar.add("minute");
        scalar.add("month");
        scalar.add("now");
        scalar.add("parse_datetime");
        scalar.add("parse_duration");
        scalar.add("quarter");
        scalar.add("second");
        scalar.add("timezone_hour");
        scalar.add("timezone_minute");
        scalar.add("to_iso8601");
        scalar.add("to_milliseconds");
        scalar.add("to_unixtime");
        scalar.add("week");
        scalar.add("week_of_year");
        scalar.add("year");
        scalar.add("year_of_week");
        scalar.add("yow");
        // ---- Array ----
        scalar.add("array_distinct");
        scalar.add("array_except");
        scalar.add("array_flatten");
        scalar.add("array_intersect");
        scalar.add("array_join");
        scalar.add("array_max");
        scalar.add("array_min");
        scalar.add("array_position");
        scalar.add("array_remove");
        scalar.add("array_reverse");
        scalar.add("array_sort");
        scalar.add("array_union");
        scalar.add("arrays_overlap");
        scalar.add("cardinality");
        scalar.add("combinations");
        scalar.add("contains");
        scalar.add("element_at");
        scalar.add("filter");
        scalar.add("flatten");
        scalar.add("ngrams");
        scalar.add("reduce");
        scalar.add("sequence");
        scalar.add("shuffle");
        scalar.add("slice");
        scalar.add("transform");
        scalar.add("zip");
        scalar.add("zip_with");
        // ---- Map ----
        scalar.add("map");
        scalar.add("map_concat");
        scalar.add("map_entries");
        scalar.add("map_filter");
        scalar.add("map_from_entries");
        scalar.add("map_keys");
        scalar.add("map_values");
        scalar.add("map_zip_with");
        scalar.add("transform_keys");
        scalar.add("transform_values");
        // ---- JSON ----
        scalar.add("is_json_scalar");
        scalar.add("json_array_contains");
        scalar.add("json_array_get");
        scalar.add("json_array_length");
        scalar.add("json_extract");
        scalar.add("json_extract_scalar");
        scalar.add("json_format");
        scalar.add("json_object");
        scalar.add("json_parse");
        scalar.add("json_size");
        // ---- URL ----
        scalar.add("url_decode");
        scalar.add("url_encode");
        scalar.add("url_extract_fragment");
        scalar.add("url_extract_host");
        scalar.add("url_extract_parameter");
        scalar.add("url_extract_path");
        scalar.add("url_extract_port");
        scalar.add("url_extract_protocol");
        scalar.add("url_extract_query");
        // ---- Regex ----
        scalar.add("regexp_count");
        scalar.add("regexp_extract");
        scalar.add("regexp_extract_all");
        scalar.add("regexp_like");
        scalar.add("regexp_position");
        scalar.add("regexp_replace");
        scalar.add("regexp_split");
        // ---- Binary / Hash ----
        scalar.add("crc32");
        scalar.add("from_base32");
        scalar.add("from_base64");
        scalar.add("from_base64url");
        scalar.add("from_hex");
        scalar.add("from_ieee754_32");
        scalar.add("from_ieee754_64");
        scalar.add("hmac_md5");
        scalar.add("hmac_sha1");
        scalar.add("hmac_sha256");
        scalar.add("hmac_sha512");
        scalar.add("md5");
        scalar.add("sha1");
        scalar.add("sha256");
        scalar.add("sha512");
        scalar.add("spooky_hash_v2_32");
        scalar.add("spooky_hash_v2_64");
        scalar.add("to_base32");
        scalar.add("to_base64");
        scalar.add("to_base64url");
        scalar.add("to_hex");
        scalar.add("to_ieee754_32");
        scalar.add("to_ieee754_64");
        scalar.add("xxhash64");
        // ---- UUID ----
        scalar.add("uuid");
        // ---- IP ----
        scalar.add("ip_prefix");
        scalar.add("ip_subnet_max");
        scalar.add("ip_subnet_min");
        scalar.add("ip_subnet_range");
        scalar.add("is_subnet_of");
        scalar.add("canonicalize_ipv6_address_to_ipv4");
        // ---- Color ----
        scalar.add("bar");
        scalar.add("color");
        scalar.add("render");
        scalar.add("rgb");
        // ---- HyperLogLog ----
        scalar.add("approx_set");
        scalar.add("cardinality");
        scalar.add("empty_approx_set");
        // ---- Type / Conversion ----
        scalar.add("cast");
        scalar.add("try_cast");
        scalar.add("typeof");
        scalar.add("to_char");
        // ---- Conditional ----
        scalar.add("coalesce");
        scalar.add("greatest");
        scalar.add("if");
        scalar.add("least");
        scalar.add("nullif");
        scalar.add("try");
        // ---- Misc ----
        scalar.add("classify");
        scalar.add("features");
        scalar.add("hash_counts");
        scalar.add("make_set_digest");
        scalar.add("merge_set_digest");
        scalar.add("set_digest_element_count");
        scalar.add("set_digest_intersection_cardinality");
        scalar.add("jaccard_index");
        scalar.add("fail");
        scalar.add("version");
        scalar.add("last_day_of_month");
        scalar.add("human_readable_seconds");
        scalar.add("tdigest_agg");
        scalar.add("values_at_quantiles");
        scalar.add("qdigest_agg");
        scalar.add("value_at_quantile");
        SCALAR_FUNCTIONS = Collections.unmodifiableSet(scalar);

        Set<String> all = new HashSet<>();
        all.addAll(AGGREGATE_FUNCTIONS);
        all.addAll(WINDOW_FUNCTIONS);
        all.addAll(SCALAR_FUNCTIONS);
        ALL_BUILTIN_FUNCTIONS = Collections.unmodifiableSet(all);
    }

    private TrinoBuiltinFunctions() {
    }

    /**
     * Returns {@code true} if {@code functionName} (case-insensitive) is a known
     * Trino built-in function.
     *
     * @param functionName the function name to look up
     * @return {@code true} when the function is a Trino built-in
     */
    public static boolean isBuiltin(String functionName) {
        if (functionName == null) {
            return false;
        }
        return ALL_BUILTIN_FUNCTIONS.contains(functionName.toLowerCase());
    }

    /**
     * Returns {@code true} if {@code functionName} (case-insensitive) is a known
     * Trino built-in aggregate function.
     *
     * @param functionName the function name to look up
     * @return {@code true} when the function is a built-in aggregate
     */
    public static boolean isAggregate(String functionName) {
        if (functionName == null) {
            return false;
        }
        return AGGREGATE_FUNCTIONS.contains(functionName.toLowerCase());
    }

    /**
     * Returns {@code true} if {@code functionName} (case-insensitive) is a known
     * Trino built-in window function.
     *
     * @param functionName the function name to look up
     * @return {@code true} when the function is a built-in window function
     */
    public static boolean isWindow(String functionName) {
        if (functionName == null) {
            return false;
        }
        return WINDOW_FUNCTIONS.contains(functionName.toLowerCase());
    }

    /**
     * Returns an unmodifiable view of all known Trino built-in function names (lowercase).
     *
     * @return set of all built-in function names
     */
    public static Set<String> allBuiltins() {
        return ALL_BUILTIN_FUNCTIONS;
    }
}
