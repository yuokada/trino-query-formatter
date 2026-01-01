SELECT o.order_id,
       c.id   AS customer_id,
       c.name AS customer_name,
       o.total_amount
FROM catalog1.sales.orders o
JOIN catalog1.sales.customers c ON o.customer_id = c.id
WHERE o.order_date >= DATE '2024-01-01'
ORDER BY o.order_id;
