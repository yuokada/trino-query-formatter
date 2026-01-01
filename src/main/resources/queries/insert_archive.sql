INSERT INTO catalog1.audit.login_archive (user_id, login_time)
SELECT user_id, event_time
FROM catalog1.audit.login_events
WHERE event_time < DATE '2024-01-01';
