SELECT
    o.order_id,
    c.name AS customer_name,
    p.name AS product_name,
    oi.quantity,
    oi.unit_price
FROM catalog1.sales.orders o
INNER JOIN catalog1.sales.customers c ON o.customer_id = c.id
LEFT JOIN catalog1.sales.order_items oi ON o.order_id = oi.order_id
LEFT JOIN catalog1.sales.products p ON oi.product_id = p.id
WHERE o.order_date >= DATE '2024-01-01'
    AND c.region = 'APAC'
ORDER BY o.order_id, oi.line_num;

SELECT
    a.id,
    b.val,
    c.label
FROM t1 a
CROSS JOIN t2 b
FULL OUTER JOIN t3 c ON a.key = c.key
WHERE b.val IS NOT NULL;
