SELECT
  TD_TIME_FORMAT(time, 'yyyy-MM-dd', 'JST')
, COUNT(*) "ALL" -- sample comment
, COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.execution')) "Only io.trino.execution"
, COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.util')) "Only io.trino.util.CompilerUtils"
FROM
  cat1.scm1.tb1
WHERE TD_INTERVAL(time, '-7d')
GROUP BY 1
ORDER BY 1 ASC, 2 ASC
;
DROP TABLE IF EXISTS cat2.scm1.tb1;
