USE hospital_db;

ALTER TABLE users ADD COLUMN photo MEDIUMBLOB NULL AFTER email;
