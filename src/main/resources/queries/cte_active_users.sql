WITH active_users AS (
    SELECT user_id
    FROM catalog2.app.events
    WHERE event_type = 'login' AND event_time >= DATE '2024-01-01'
)
SELECT u.user_id, COUNT(*) AS login_count
FROM active_users u
JOIN catalog2.app.events e ON e.user_id = u.user_id AND e.event_type = 'login'
GROUP BY u.user_id
ORDER BY login_count DESC;
