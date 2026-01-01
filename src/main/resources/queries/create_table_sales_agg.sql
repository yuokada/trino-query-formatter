CREATE TABLE catalog1.analytics.sales_daily AS
SELECT order_date,
       SUM(total_amount) AS total_amount
FROM catalog1.sales.orders
GROUP BY order_date;
