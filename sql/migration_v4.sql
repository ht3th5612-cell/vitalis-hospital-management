USE hospital_db;

ALTER TABLE users     ADD COLUMN monthly_salary DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER phone;
ALTER TABLE inventory ADD COLUMN unit_cost      DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER unit_price;
ALTER TABLE billings  ADD COLUMN bill_type      VARCHAR(10)    NOT NULL DEFAULT 'Service' AFTER description;

UPDATE users SET monthly_salary = CASE role
    WHEN 'ADMIN' THEN 900 WHEN 'DOCTOR' THEN 1500 WHEN 'NURSE' THEN 600
    WHEN 'RECEPTIONIST' THEN 450 WHEN 'INVENTORY' THEN 450 ELSE 0 END;
UPDATE inventory SET unit_cost = ROUND(unit_price * 0.6, 2);
UPDATE billings SET bill_type = 'Medicine' WHERE inventory_id IS NOT NULL;

INSERT INTO inventory (item_name, category, unit_price, unit_cost, stock_quantity, reorder_level) VALUES
    ('Ward A',       'Room',  40.00, 0, 10, 0),
    ('Ward B',       'Room',  30.00, 0, 16, 0),
    ('Private Room', 'Room',  90.00, 0,  5, 0),
    ('ICU',          'Room', 200.00, 0,  4, 0);

UPDATE appointments SET room = 'Ward A'       WHERE room LIKE 'Ward A%';
UPDATE appointments SET room = 'Ward B'       WHERE room LIKE 'Ward B%';
UPDATE appointments SET room = 'Private Room' WHERE room LIKE 'Private%';
UPDATE appointments SET room = 'ICU'          WHERE room LIKE 'ICU%';
