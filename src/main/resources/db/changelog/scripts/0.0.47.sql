--liquibase formatted sql
--changeset DanielK:47 splitStatements:false

SELECT setval(pg_get_serial_sequence('app_users', 'id'), COALESCE((SELECT MAX(id) FROM app_users), 1));
SELECT setval(pg_get_serial_sequence('payments', 'id'), COALESCE((SELECT MAX(id) FROM payments), 1));
SELECT setval(pg_get_serial_sequence('user_subscription', 'id'), COALESCE((SELECT MAX(id) FROM user_subscription), 1));
SELECT setval(pg_get_serial_sequence('generation_balance', 'id'), COALESCE((SELECT MAX(id) FROM generation_balance), 1));
SELECT setval(pg_get_serial_sequence('generation_balance_operations', 'id'), COALESCE((SELECT MAX(id) FROM generation_balance_operations), 1));