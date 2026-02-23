SELECT
    id,
    CASE status
        WHEN 'active' THEN 1
        WHEN 'inactive' THEN 0
        ELSE -1
    END AS status_code,
    CASE
        WHEN score >= 90 THEN 'A'
        WHEN score >= 80 THEN 'B'
        WHEN score >= 70 THEN 'C'
        ELSE 'F'
    END AS grade
FROM students;

SELECT
    order_id,
    amount,
    CASE
        WHEN amount > 1000 THEN 'large'
        WHEN amount > 100 THEN 'medium'
        ELSE 'small'
    END AS size_category,
    SUM(CASE WHEN status = 'complete' THEN amount ELSE 0 END) AS completed_total
FROM orders
GROUP BY order_id, amount, status;
