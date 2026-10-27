DROP DATABASE IF EXISTS hospital_db;
CREATE DATABASE hospital_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hospital_db;

CREATE TABLE departments (
    department_id   INT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(255)
) ENGINE = InnoDB;

CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    department_id INT NULL,
    phone         VARCHAR(30),
    date_of_birth DATE         NULL,
    gender        VARCHAR(10)  NULL,
    nrc           VARCHAR(30)  NULL,
    email         VARCHAR(100) NULL,
    photo         MEDIUMBLOB   NULL,
    monthly_salary DECIMAL(10, 2) NOT NULL DEFAULT 0,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id)
        REFERENCES departments (department_id) ON DELETE SET NULL,
    INDEX idx_user_nrc (nrc),
    INDEX idx_user_email (email),
    CONSTRAINT chk_user_role CHECK (role IN
        ('OWNER', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'INVENTORY'))
) ENGINE = InnoDB;

CREATE TABLE doctors (
    user_id          INT PRIMARY KEY,
    department_id    INT NULL,
    specialization   VARCHAR(100),
    consultation_fee DECIMAL(10, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id)
        REFERENCES departments (department_id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE patients (
    patient_id INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    age        INT          NOT NULL,
    date_of_birth DATE      NULL,
    gender     VARCHAR(10)  NOT NULL,
    nrc        VARCHAR(30)  NULL,
    phone      VARCHAR(30)  NOT NULL,
    email      VARCHAR(100) NULL,
    address    VARCHAR(255),
    blood_group VARCHAR(5),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_patient_name (name),
    INDEX idx_patient_nrc (nrc)
) ENGINE = InnoDB;

CREATE TABLE appointments (
    appointment_id   INT AUTO_INCREMENT PRIMARY KEY,
    patient_id       INT  NOT NULL,
    doctor_id        INT  NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    reason           VARCHAR(255),
    status           VARCHAR(20) NOT NULL DEFAULT 'Scheduled',
    visit_type       VARCHAR(15) NOT NULL DEFAULT 'Outpatient',
    room             VARCHAR(30) NULL,
    admitted_at      DATETIME    NULL,
    discharged_at    DATETIME    NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    INDEX idx_appointment_date (appointment_date)
) ENGINE = InnoDB;

CREATE TABLE medical_records (
    record_id      INT AUTO_INCREMENT PRIMARY KEY,
    patient_id     INT NOT NULL,
    doctor_id      INT NOT NULL,
    appointment_id INT NULL,
    diagnosis      TEXT NOT NULL,
    prescription   TEXT,
    notes          TEXT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_record_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_doctor FOREIGN KEY (doctor_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (appointment_id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE patient_vitals (
    vital_id       INT AUTO_INCREMENT PRIMARY KEY,
    patient_id     INT NOT NULL,
    nurse_id       INT NOT NULL,
    blood_pressure VARCHAR(20),
    temperature    DECIMAL(4, 1),
    pulse          INT,
    notes          VARCHAR(255),
    recorded_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vital_nurse FOREIGN KEY (nurse_id)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE inventory (
    item_id        INT AUTO_INCREMENT PRIMARY KEY,
    item_name      VARCHAR(100) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    unit_cost      DECIMAL(10, 2) NOT NULL DEFAULT 0,
    stock_quantity INT NOT NULL,
    reorder_level  INT NOT NULL DEFAULT 10,
    expiry_date    DATE NULL,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;

CREATE TABLE messages (
    message_id  INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    kind        VARCHAR(10) NOT NULL DEFAULT 'text',
    file_name   VARCHAR(200) NULL,
    file_key    VARCHAR(40) NULL,
    file_size   INT NOT NULL DEFAULT 0,
    pinned      TINYINT(1) NOT NULL DEFAULT 0,
    sent_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at     DATETIME NULL,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    INDEX idx_message_receiver (receiver_id, read_at),
    INDEX idx_message_pair (sender_id, receiver_id)
) ENGINE = InnoDB;

CREATE TABLE billings (
    billing_id     INT AUTO_INCREMENT PRIMARY KEY,
    patient_id     INT NOT NULL,
    appointment_id INT NULL,
    description    VARCHAR(255) NOT NULL,
    bill_type      VARCHAR(10)  NOT NULL DEFAULT 'Service',
    total_amount   DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'Pending',
    payment_method VARCHAR(20),
    billed_by      INT NULL,
    inventory_id   INT NULL,
    quantity       INT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_billing_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (appointment_id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_user FOREIGN KEY (billed_by)
        REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory (item_id) ON DELETE SET NULL,
    INDEX idx_billing_status (payment_status)
) ENGINE = InnoDB;

INSERT INTO departments (department_id, department_name, description) VALUES
    (1, 'General Medicine', 'Outpatient consultations and general check-ups'),
    (2, 'Pediatrics',       'Medical care for infants, children and teenagers'),
    (3, 'Cardiology',       'Heart and cardiovascular treatment'),
    (4, 'Surgery',          'General and specialist surgical procedures'),
    (5, 'Emergency',        '24/7 emergency and trauma care'),
    (6, 'Laboratory',       'Diagnostic tests and pathology services'),
    (7, 'Pharmacy',         'Medicines, consumables and medical stock');

INSERT INTO users (user_id, username, password_hash, full_name, role, department_id, phone, monthly_salary, active) VALUES
    (1, 'owner',     '$2a$10$DBoLvF2LqlUD3WlWFGJ45O8rruIsQHLeRBoxp9VIA4c.f76OKwuiG', 'U Aung Kyaw',         'OWNER',        1, '09-770000001', 0.00, 1),
    (2, 'admin',     '$2a$10$53nUJzWPiCTbWpEyeDsv1eoqyOq6zmv4C9Usmw5NGezhxqx3dA2I6', 'Daw Mya Mya',         'ADMIN',        1, '09-770000002', 900.00, 1),
    (3, 'dr.alice',  '$2a$10$O0fDS8wiC38OL2w8FlJyyunQDZR9jPUuCKQHTSUGxlHUJUpcR.CHK', 'Dr. Alice Smith',     'DOCTOR',       1, '09-770000003', 1500.00, 1),
    (4, 'dr.bob',    '$2a$10$PBT62gpvp00IzJ3dZoL/oum3.PwtlNBB4GU4lx72OS9mtKEXE42sq', 'Dr. Bob Lin',         'DOCTOR',       3, '09-770000004', 1500.00, 1),
    (5, 'nurse.amy', '$2a$10$XaPv43XtlCvsOd9c10pqBesfUbfWmEgn/DDOz9vdELNwQn4qL4Cuq', 'Nurse Amy Tun',       'NURSE',        1, '09-770000005', 600.00, 1),
    (6, 'reception', '$2a$10$vmYCKqpIMQKI1G/67vg2zebkVrSKC4v5qT86aMDNTXGU76klz.y2i', 'Ko Zaw Min',          'RECEPTIONIST', 1, '09-770000006', 450.00, 1),
    (8, 'inventory', '$2a$10$Zr9GVNnDpdCBwMwz2rh7F.JDCKAC/mjtSJAgafAsfXBE.qs/jUQyy', 'Ko Min Khant',        'INVENTORY',    7, '09-770000008', 450.00, 1);

INSERT INTO doctors (user_id, department_id, specialization, consultation_fee) VALUES
    (3, 1, 'General Physician', 50.00),
    (4, 3, 'Cardiologist',      80.00);

INSERT INTO patients (patient_id, name, age, date_of_birth, gender, nrc, phone, address, blood_group, created_at) VALUES
    (1, 'John Carter',    34, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 34 YEAR), INTERVAL 100 DAY), 'Male',   '12/LAMANA(N)111111', '09-421000111', 'No. 12, Bogyoke Road, Yangon',   'O+',  DATE_SUB(NOW(), INTERVAL 60 DAY)),
    (2, 'Su Su Hlaing',   28, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 28 YEAR), INTERVAL 100 DAY), 'Female', '12/BAHANA(N)222222', '09-421000222', 'No. 5, Pyay Road, Yangon',       'A+',  DATE_SUB(NOW(), INTERVAL 45 DAY)),
    (3, 'Michael Brown',  52, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 52 YEAR), INTERVAL 100 DAY), 'Male',   '9/MAHAMU(N)333333', '09-421000333', 'No. 88, Mandalay Street, Mandalay', 'B+', DATE_SUB(NOW(), INTERVAL 30 DAY)),
    (4, 'Ei Ei Mon',       7, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 7 YEAR), INTERVAL 100 DAY), 'Female', NULL, '09-421000444', 'No. 9, Inya Road, Yangon',       'AB+', DATE_SUB(NOW(), INTERVAL 21 DAY)),
    (5, 'David Lee',      41, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 41 YEAR), INTERVAL 100 DAY), 'Male',   '12/KAMANA(N)555555', '09-421000555', 'No. 24, Baho Road, Yangon',      'O-',  DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (6, 'Nandar Win',     63, DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 63 YEAR), INTERVAL 100 DAY), 'Female', '12/PABATA(N)666666', '09-421000666', 'No. 3, Strand Road, Yangon',     'A-',  DATE_SUB(NOW(), INTERVAL 7 DAY));

INSERT INTO appointments
    (appointment_id, patient_id, doctor_id, appointment_date, appointment_time, reason, status, created_at) VALUES
    (1, 1, 3, CURDATE(), '09:00:00', 'Fever and cough for three days',    'Completed', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (2, 2, 3, CURDATE(), '10:30:00', 'Routine general check-up',          'Scheduled', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (3, 3, 4, CURDATE(), '11:15:00', 'Chest pain during exercise',        'Scheduled', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (4, 4, 3, CURDATE(), '13:00:00', 'Child vaccination follow-up',       'Scheduled', NOW()),
    (5, 5, 4, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:30:00', 'Blood pressure review', 'Scheduled', NOW()),
    (6, 6, 3, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00', 'Diabetes medication review', 'Scheduled', NOW()),
    (7, 2, 4, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:00:00', 'Palpitations', 'Completed', DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (8, 1, 3, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:00:00', 'Headache', 'Completed', DATE_SUB(NOW(), INTERVAL 6 DAY)),
    (9, 3, 3, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:00:00', 'Follow-up consultation', 'Cancelled', DATE_SUB(NOW(), INTERVAL 4 DAY));

INSERT INTO appointments
    (appointment_id, patient_id, doctor_id, appointment_date, appointment_time, reason, status,
     visit_type, room, admitted_at) VALUES
    (10, 6, 4, CURDATE(), '08:00:00', 'Chest pain - observation', 'Admitted',
     'Inpatient', 'Ward A', DATE_SUB(NOW(), INTERVAL 5 HOUR));

INSERT INTO medical_records
    (record_id, patient_id, doctor_id, appointment_id, diagnosis, prescription, notes, created_at) VALUES
    (1, 1, 3, 1, 'Acute viral pharyngitis',
        'Paracetamol 500 mg - 1 tablet every 6 hours for 3 days; warm water gargle',
        'Rest for two days and return if the fever continues.', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (2, 2, 4, 7, 'Palpitations - likely stress related',
        'Propranolol 10 mg - 1 tablet twice daily for 5 days',
        'ECG result was normal. Reduce caffeine intake.', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (3, 1, 3, 8, 'Tension headache',
        'Ibuprofen 400 mg - 1 tablet after meals for 2 days',
        'Advised to improve sleep habits.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (4, 3, 4, NULL, 'Hypertension stage 1',
        'Amlodipine 5 mg - 1 tablet daily',
        'Monitor blood pressure every morning for two weeks.', DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT INTO patient_vitals
    (vital_id, patient_id, nurse_id, blood_pressure, temperature, pulse, notes, recorded_at) VALUES
    (1, 1, 5, '120/80', 37.8, 88, 'Slight fever, advised to rest',      DATE_SUB(NOW(), INTERVAL 4 HOUR)),
    (2, 2, 5, '118/76', 36.7, 72, 'Stable',                             DATE_SUB(NOW(), INTERVAL 3 HOUR)),
    (3, 3, 5, '145/92', 36.9, 80, 'Elevated blood pressure',            DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (4, 4, 5, '100/70', 36.6, 95, 'Child - normal range',               DATE_SUB(NOW(), INTERVAL 1 HOUR)),
    (5, 5, 5, '138/88', 37.1, 76, 'Review with cardiologist',           DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO billings
    (billing_id, patient_id, description, total_amount, payment_status, payment_method, billed_by, created_at) VALUES
    (1, 1, 'General consultation + medication', 85.00,  'Paid',    'Cash',         6, DATE_SUB(NOW(), INTERVAL 5 MONTH)),
    (2, 3, 'Cardiology consultation',           120.00, 'Paid',    'Card',         6, DATE_SUB(NOW(), INTERVAL 4 MONTH)),
    (3, 2, 'General consultation',               50.00,  'Paid',    'Cash',         6, DATE_SUB(NOW(), INTERVAL 3 MONTH)),
    (4, 5, 'Cardiology follow-up + ECG',        150.00, 'Paid',    'Bank Transfer',6, DATE_SUB(NOW(), INTERVAL 2 MONTH)),
    (5, 4, 'Vaccination and pediatric check-up', 65.00, 'Paid',    'Cash',         6, DATE_SUB(NOW(), INTERVAL 1 MONTH)),
    (6, 1, 'General consultation + medication',  85.00, 'Paid',    'Cash',         6, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (7, 3, 'Cardiology consultation',           120.00, 'Pending', NULL,           6, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
    (8, 2, 'Routine general check-up',           50.00, 'Pending', NULL,           6, NOW());

INSERT INTO inventory
    (item_id, item_name, category, unit_price, unit_cost, stock_quantity, reorder_level, expiry_date) VALUES
    (1, 'Paracetamol 500 mg',      'Medicine',     0.20, 0.12, 450, 100, DATE_ADD(CURDATE(), INTERVAL 8 MONTH)),
    (2, 'Amoxicillin 250 mg',      'Medicine',     0.35, 0.21, 120,  80, DATE_ADD(CURDATE(), INTERVAL 5 MONTH)),
    (3, 'Ibuprofen 400 mg',        'Medicine',     0.25, 0.15,  60,  80, DATE_ADD(CURDATE(), INTERVAL 10 MONTH)),
    (4, 'Surgical Gloves',         'Consumable',   0.50, 0.30, 900, 200, NULL),
    (5, 'Face Masks (box)',        'Consumable',   3.00, 1.80,  25,  40, NULL),
    (6, 'Saline IV 500 ml',        'Consumable',   1.20, 0.72, 150,  50, DATE_ADD(CURDATE(), INTERVAL 15 DAY)),
    (7, 'Digital Thermometer',     'Equipment',   18.00, 10.80,  12,   5, NULL),
    (8, 'Blood Pressure Monitor',  'Equipment',   45.00, 27.00,   4,   6, NULL),
    (9, 'Amlodipine 5 mg',         'Medicine',     0.30, 0.18, 300, 100, DATE_ADD(CURDATE(), INTERVAL 20 DAY)),
    (10, 'Hand Sanitizer 500 ml',  'Consumable',   4.50, 2.70,   8,  15, DATE_ADD(CURDATE(), INTERVAL 2 MONTH)),
    (11, 'Ward A',       'Room',  40.00, 0, 10, 0, NULL),
    (12, 'Ward B',       'Room',  30.00, 0, 16, 0, NULL),
    (13, 'Private Room', 'Room',  90.00, 0,  5, 0, NULL),
    (14, 'ICU',          'Room', 200.00, 0,  4, 0, NULL);
