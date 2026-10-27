USE hospital_db;

DROP TABLE IF EXISTS audit_log;
ALTER TABLE users    DROP COLUMN IF EXISTS must_change_password;
ALTER TABLE patients DROP COLUMN IF EXISTS active;
