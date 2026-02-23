SELECT
    id,
    name,
    total_spent
FROM (
    SELECT
        c.id,
        c.name,
        SUM(o.amount) AS total_spent
    FROM catalog1.sales.customers c
    JOIN catalog1.sales.orders o ON c.id = o.customer_id
    GROUP BY c.id, c.name
) sub
WHERE total_spent > 1000
ORDER BY total_spent DESC;

SELECT id, score
FROM results
WHERE score > (SELECT AVG(score) FROM results WHERE category = 'math')
    AND id IN (SELECT student_id FROM enrolled WHERE course = 'advanced');

WITH top_products AS (
    SELECT product_id, SUM(quantity) AS total_qty
    FROM catalog1.sales.order_items
    GROUP BY product_id
    HAVING SUM(quantity) > 100
),
ranked AS (
    SELECT
        p.name,
        tp.total_qty,
        RANK() OVER (ORDER BY tp.total_qty DESC) AS rnk
    FROM top_products tp
    JOIN catalog1.sales.products p ON tp.product_id = p.id
)
SELECT name, total_qty, rnk
FROM ranked
WHERE rnk <= 10;
