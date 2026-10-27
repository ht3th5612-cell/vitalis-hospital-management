USE hospital_db;

ALTER TABLE appointments
    ADD COLUMN visit_type    VARCHAR(15) NOT NULL DEFAULT 'Outpatient' AFTER status,
    ADD COLUMN room          VARCHAR(30) NULL AFTER visit_type,
    ADD COLUMN admitted_at   DATETIME    NULL AFTER room,
    ADD COLUMN discharged_at DATETIME    NULL AFTER admitted_at;

ALTER TABLE billings
    ADD COLUMN inventory_id INT NULL AFTER billed_by,
    ADD COLUMN quantity     INT NULL AFTER inventory_id,
    ADD CONSTRAINT fk_billing_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory (item_id) ON DELETE SET NULL;

UPDATE users SET role = 'RECEPTIONIST' WHERE role = 'CASHIER';
ALTER TABLE users DROP CONSTRAINT chk_user_role;
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN
    ('OWNER', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'INVENTORY'));
