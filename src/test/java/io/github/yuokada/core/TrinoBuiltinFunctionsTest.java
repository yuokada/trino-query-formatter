package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TrinoBuiltinFunctions}.
 */
class TrinoBuiltinFunctionsTest {

    @Test
    void testAggregatesAreBuiltin() {
        assertTrue(TrinoBuiltinFunctions.isBuiltin("count"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("sum"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("avg"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("min"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("max"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("approx_distinct"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("array_agg"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("map_agg"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("stddev"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("count_if"));
    }

    @Test
    void testWindowFunctionsAreBuiltin() {
        assertTrue(TrinoBuiltinFunctions.isBuiltin("row_number"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("rank"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("dense_rank"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("lead"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("lag"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("first_value"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("last_value"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("nth_value"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("ntile"));
    }

    @Test
    void testScalarFunctionsAreBuiltin() {
        // Math
        assertTrue(TrinoBuiltinFunctions.isBuiltin("abs"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("sqrt"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("round"));
        // String
        assertTrue(TrinoBuiltinFunctions.isBuiltin("lower"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("upper"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("length"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("substr"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("concat"));
        // Date/time
        assertTrue(TrinoBuiltinFunctions.isBuiltin("now"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("date_trunc"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("date_format"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("from_unixtime"));
        // Array
        assertTrue(TrinoBuiltinFunctions.isBuiltin("cardinality"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("array_sort"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("filter"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("transform"));
        // Map
        assertTrue(TrinoBuiltinFunctions.isBuiltin("map_keys"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("map_values"));
        // JSON
        assertTrue(TrinoBuiltinFunctions.isBuiltin("json_extract_scalar"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("json_parse"));
        // Conditional
        assertTrue(TrinoBuiltinFunctions.isBuiltin("coalesce"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("try"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("try_cast"));
        // Regex
        assertTrue(TrinoBuiltinFunctions.isBuiltin("regexp_like"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("regexp_extract"));
    }

    @Test
    void testCaseInsensitive() {
        assertTrue(TrinoBuiltinFunctions.isBuiltin("COUNT"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("Count"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("LOWER"));
        assertTrue(TrinoBuiltinFunctions.isBuiltin("Row_Number"));
    }

    @Test
    void testCustomUdfIsNotBuiltin() {
        assertFalse(TrinoBuiltinFunctions.isBuiltin("my_custom_udf"));
        assertFalse(TrinoBuiltinFunctions.isBuiltin("td_time_format"));
        assertFalse(TrinoBuiltinFunctions.isBuiltin("date_to_epoch"));
        assertFalse(TrinoBuiltinFunctions.isBuiltin("my_etl_transform"));
    }

    @Test
    void testNullAndEmptyReturnFalse() {
        assertFalse(TrinoBuiltinFunctions.isBuiltin(null));
        assertFalse(TrinoBuiltinFunctions.isAggregate(null));
        assertFalse(TrinoBuiltinFunctions.isWindow(null));
    }

    @Test
    void testIsAggregateCategory() {
        assertTrue(TrinoBuiltinFunctions.isAggregate("count"));
        assertTrue(TrinoBuiltinFunctions.isAggregate("stddev_pop"));
        assertFalse(TrinoBuiltinFunctions.isAggregate("lower"));
        assertFalse(TrinoBuiltinFunctions.isAggregate("row_number"));
    }

    @Test
    void testIsWindowCategory() {
        assertTrue(TrinoBuiltinFunctions.isWindow("row_number"));
        assertTrue(TrinoBuiltinFunctions.isWindow("lag"));
        assertFalse(TrinoBuiltinFunctions.isWindow("count"));
        assertFalse(TrinoBuiltinFunctions.isWindow("lower"));
    }

    @Test
    void testAllBuiltinsIsNonEmpty() {
        assertFalse(TrinoBuiltinFunctions.allBuiltins().isEmpty(),
            "Built-in catalog should not be empty");
    }
}
