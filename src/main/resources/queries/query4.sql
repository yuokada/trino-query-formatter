SELECT
    TD_TIME_FORMAT(time, 'yyyy-MM-dd', 'JST')
    , COUNT(*) AS "ALL" -- sample comment
    , COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.execution')) AS "Only io.trino.execution"
    , COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.util')) AS "Only io.trino.util.CompilerUtils"
FROM cat1.scm1.tb1
WHERE TD_INTERVAL(time, '-7d')
GROUP BY 1
ORDER BY 1,2
;
