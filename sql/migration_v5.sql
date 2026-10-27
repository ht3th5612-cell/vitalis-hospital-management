USE hospital_db;

ALTER TABLE patients ADD COLUMN date_of_birth DATE        NULL AFTER age;
ALTER TABLE patients ADD COLUMN nrc           VARCHAR(30) NULL AFTER gender;
ALTER TABLE patients ADD INDEX idx_patient_nrc (nrc);

UPDATE patients
SET date_of_birth = DATE_SUB(DATE_SUB(CURDATE(), INTERVAL age YEAR), INTERVAL 100 DAY)
WHERE date_of_birth IS NULL;
