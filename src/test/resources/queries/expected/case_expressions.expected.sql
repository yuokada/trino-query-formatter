SELECT
  id
, (CASE status WHEN 'active' THEN 1 WHEN 'inactive' THEN 0 ELSE -1 END) status_code
, (CASE WHEN (score >= 90) THEN 'A' WHEN (score >= 80) THEN 'B' WHEN (score >= 70) THEN 'C' ELSE 'F' END) grade
FROM
  students
;
SELECT
  order_id
, amount
, (CASE WHEN (amount > 1000) THEN 'large' WHEN (amount > 100) THEN 'medium' ELSE 'small' END) size_category
, SUM((CASE WHEN (status = 'complete') THEN amount ELSE 0 END)) completed_total
FROM
  orders
GROUP BY order_id, amount, status
;
