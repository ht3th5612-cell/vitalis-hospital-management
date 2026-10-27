USE hospital_db;

ALTER TABLE users
    ADD COLUMN date_of_birth DATE         NULL AFTER phone,
    ADD COLUMN gender        VARCHAR(10)  NULL AFTER date_of_birth,
    ADD COLUMN nrc           VARCHAR(30)  NULL AFTER gender,
    ADD COLUMN email         VARCHAR(100) NULL AFTER nrc,
    ADD INDEX idx_user_nrc (nrc),
    ADD INDEX idx_user_email (email);

ALTER TABLE patients ADD COLUMN email VARCHAR(100) NULL AFTER phone;
