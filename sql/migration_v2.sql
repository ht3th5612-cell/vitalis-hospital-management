USE hospital_db;

ALTER TABLE appointments
    DROP FOREIGN KEY fk_appointment_patient,
    DROP FOREIGN KEY fk_appointment_doctor;
ALTER TABLE appointments
    ADD CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id)
        REFERENCES users (user_id) ON DELETE RESTRICT;

ALTER TABLE medical_records
    DROP FOREIGN KEY fk_record_patient,
    DROP FOREIGN KEY fk_record_doctor;
ALTER TABLE medical_records
    ADD CONSTRAINT fk_record_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_record_doctor FOREIGN KEY (doctor_id)
        REFERENCES users (user_id) ON DELETE RESTRICT;

ALTER TABLE patient_vitals
    DROP FOREIGN KEY fk_vital_patient,
    DROP FOREIGN KEY fk_vital_nurse;
ALTER TABLE patient_vitals
    ADD CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_vital_nurse FOREIGN KEY (nurse_id)
        REFERENCES users (user_id) ON DELETE RESTRICT;

ALTER TABLE billings
    DROP FOREIGN KEY fk_billing_patient;
ALTER TABLE billings
    ADD CONSTRAINT fk_billing_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT;
