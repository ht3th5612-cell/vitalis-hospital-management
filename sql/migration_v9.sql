USE hospital_db;

ALTER TABLE messages
    ADD COLUMN kind      VARCHAR(10) NOT NULL DEFAULT 'text' AFTER body,
    ADD COLUMN file_name VARCHAR(200) NULL AFTER kind,
    ADD COLUMN file_key  VARCHAR(40) NULL AFTER file_name,
    ADD COLUMN file_size INT NOT NULL DEFAULT 0 AFTER file_key,
    ADD COLUMN pinned    TINYINT(1) NOT NULL DEFAULT 0 AFTER file_size;
