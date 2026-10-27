# VITALIS - how the tables and the functions connect

Read this top to bottom once and you can explain the whole project.

## 1. One action from screen to database

Example: the receptionist presses **Save patient**.

```
patients.fxml                 the button says onAction="#handleSave"
   |
PatientController.handleSave()   reads the text fields, checks them with Validators
   |
PatientDAO.create(patient)       Permissions.require(...) then the SQL INSERT
   |
table  patients                  MySQL stores the row
```

Every screen works the same way: **FXML -> Controller -> DAO -> table**.
A DAO is just a class that holds the SQL of one table. A *model* (Patient, Billing, ...) is a plain
Java object that carries one row between the DAO and the controller.

## 2. Table -> model -> DAO -> controller -> screen

| Database table | Model class | DAO (SQL lives here) | Controller | Screen (FXML) |
|---|---|---|---|---|
| `users` | `User` | `UserDAO` | `UserController`, `LoginController` | `users.fxml`, `login.fxml` |
| `doctors` (+ `users`) | `Doctor` | `DoctorDAO` (profile: specialisation, fee) | `UserController` (the Staff screen) | `users.fxml` |
| `departments` | `Department` | `DepartmentDAO` | `DepartmentController` | `departments.fxml` |
| `patients` | `Patient` | `PatientDAO` | `PatientController` | `patients.fxml` |
| `appointments` | `Appointment` | `AppointmentDAO` | `AppointmentController`, `ConsultationController` | `appointments.fxml`, `consultation.fxml` |
| `medical_records` | `MedicalRecord` | `MedicalRecordDAO` | `MedicalRecordController` | `medical-records.fxml` |
| `patient_vitals` | `Vital` | `VitalDAO` | `VitalController` | `vitals.fxml` |
| `billings` | `Billing` | `BillingDAO` | `BillingController` | `billing.fxml` (the Checkout) |
| `messages` | `Message` | `MessageDAO` | `MessageController` (and the unread badge of `MainController`) | `messages.fxml` |
| `inventory` | `InventoryItem` | `InventoryDAO` (medicines), `RoomDAO` (the rows with category `Room`) | `InventoryController`, `RoomController` | `inventory.fxml`, `rooms.fxml` |
| (numbers from many tables) | `DashboardStats` | `ReportDAO` | `DashboardController`, `ReportController` | `dashboard.fxml`, `reports.fxml` |

`MainController` + `main.fxml` is the window with the left menu; it opens the screens above.

## 3. Every DAO function, the table it uses, and who calls it

**UserDAO** - table `users` (+ `departments` for the name)
* `authenticate(username, password)` - login; compares the password with the BCrypt hash. *LoginController*
* `findAll(search)` / `create(..., monthlySalary)` / `update(user)` / `updatePassword(id, pw)` / `delete(id)` - *UserController* (Dismiss = `update` with `active = false`)
* `updateOwnProfile(changes, currentPassword)` / `changeOwnPassword(old, new)` / `findById(id)` - *ProfileController* (every role, the own account only; no permission is needed, the password is the proof)
* `requireManageable(role, department)` - an owner manages everybody; a department admin only the doctors and nurses of the own department

**DoctorDAO** - tables `users` (role DOCTOR) + `doctors` (specialization, fee)
* `findAll(search)` / `findActive()` - the doctor drop-downs in *AppointmentController* and the doctor fields of *UserController*
* `saveProfile(userId, department, specialisation, fee)` - the Staff screen saves the doctor part after the account is saved (a doctor is deleted with `UserDAO.delete`)

**DepartmentDAO** - table `departments`
* `findAll()` - *DepartmentController* and the department drop-downs in *UserController*
* `create` / `update` / `delete` - *DepartmentController*

**PatientDAO** - table `patients`
* `findAll(search)` - the patient register (name, NRC, phone, blood group)
* `search(name, dateOfBirth, nrc, gender)` - the **Find patient** window (*PatientFinder*): a returning patient is found by name, date of birth, NRC and gender
* `findInHospital()` - the patients who are in the hospital now (the Vitals and Medical Records drop-downs)
* `findById(id)`
* `create` / `update` / `delete` - *PatientController* (the reception; the owner only looks: *PatientHistoryController* and *CurrentPatientsController* are tabs of *RecordsController*). `delete` is refused by the database when the patient already has appointments, records or bills.

**AppointmentDAO** - table `appointments` (+ `patients`, `users` for the names, `doctors` for the fee)
* `findAll(search, doctorId)` / `create` / `updateStatus` / `delete` - *AppointmentController*
* `admit(id, room)` / `discharge(id)` - the Admit / Discharge buttons (*AppointmentController*)
* `findByPatient(patientId)` - the visits of a patient with the doctor's fee
* `startWalkIn(patientId, doctorId, reason)` - a visit that starts now (*AppointmentController*, *ConsultationController*)
* `findOpenForDoctor(doctorId)` - "my patients" of the Consultation screen; `findToPay()` - the visits the checkout can pay (*BillingController*)
* `findToday(doctorId)` - today's visits (not cancelled) of the Dashboard; `findInHospital()` - the "Patients in Hospital" table of the Dashboard (a doctor, nurse or admin sees only the own department)
* `findRecent(limit, doctorId)` - the latest appointments

**MedicalRecordDAO** - table `medical_records` (+ `patients`, `users`)
* `findAll(search, doctorId)` / `create(record)` - *MedicalRecordController*, *ConsultationController* (there is no delete on purpose)
* `findByPatient(patientId)` - the earlier diagnoses shown in the consultation

**VitalDAO** - table `patient_vitals` (+ `patients`, `users`)
* `findAll(search)` / `create(vital)` / `delete(id)` - *VitalController*; `findLatest(patientId)` - *ConsultationController*

**BillingDAO** - table `billings` (+ `patients`)
* `preview(visitId, medicines)` - the lines of a visit (doctor's fee, room, medicines) without saving; `checkout(visitId, method, medicines, billedBy)` - saves them all as Paid in one transaction, reduces the stock and discharges an admitted patient - *BillingController*
* `findAll(search)` / `findByVisit(visitId)` / `receiptLines(line)` / `markPaid(id, method)` (old pending bills). There is no delete: a bill is final

**InventoryDAO** - table `inventory`
* `findAll(search)` / `create` / `update` / `delete` - *InventoryController* (rooms are never listed)
* `restock(itemId, quantity, newUnitCost)` - the inventory staff add a delivery
* `findLowStock()` - the low-stock table on *ReportController*

**MessageDAO** - table `messages` (sender_id, receiver_id, body, **kind**, **file_name**, **file_key**, **file_size**, **pinned**, sent_at, read_at). `kind` is text, image or video. The file itself is saved in the folder `chat_files` next to the program (under a random name, `file_key`); the table keeps only the name, the size and the key, because a database packet of XAMPP is only 1 MB.
* `findContacts()` - the active staff with the last message and the unread count; `findConversation(otherId)` - the last 200 messages of one chat; `send(toId, text)`; `sendFile(toId, kind, fileName, bytes, caption)` (photo, or video up to 25 MB); `findFile(messageId)` (only the two people of the chat); `setPinned(messageId, yes/no)` and `findPinned(otherId)`; `markRead(fromId)`; `countUnread()` - the red number in the menu

**RoomDAO** - the rooms (rows of `inventory` with category `Room`)
* `findAll()` / `occupiedBeds()` / `freeRoomNames()` / `create` / `update` / `delete` - *RoomController*, the Admit drop-downs

**ReportDAO** - counts and sums over several tables
* `loadStats()` - patients, doctors, today's appointments, revenue, pending bills, low stock (Dashboard, Reports, Billing summary)
* `monthlyFinance()` - income, salaries and medicine cost of six months; `medicineUsage(thisMonth)`; `salaryTotal()` - *ReportController*
* `monthlyRevenue()` - revenue per month
* `countAppointmentsTodayForDoctor`, `countRecordsForDoctor`, `countVitalsTodayForNurse`, `countInventoryItems`, `countExpiringSoon`, `countStaffInDepartment` - the cards on the *Dashboard* for each role
* `visitsPerDay(7, doctorId)` - the "Patient Visits" area chart; `visitStatusThisMonth(doctorId)` - the "Visit Status" donut; `departmentOverview()` - the owner's "Department Overview" table

## 4. The columns each table has (this is exactly what the SQL in the DAOs uses)

| Table | Columns |
|---|---|
| `users` | user_id, username, password_hash, full_name, role, department_id, phone, **date_of_birth, gender, nrc, email, photo**, **monthly_salary**, active, created_at |
| `doctors` | user_id, department_id, specialization, consultation_fee |
| `departments` | department_id, department_name, description |
| `patients` | patient_id, name, age, **date_of_birth**, gender, **nrc**, phone, **email**, address, blood_group, created_at |
| `appointments` | appointment_id, patient_id, doctor_id, appointment_date, appointment_time, reason, status, **visit_type, room, admitted_at, discharged_at**, created_at |
| `medical_records` | record_id, patient_id, doctor_id, appointment_id, diagnosis, prescription, notes, created_at |
| `patient_vitals` | vital_id, patient_id, nurse_id, blood_pressure, temperature, pulse, notes, recorded_at |
| `billings` | billing_id, patient_id, appointment_id, description, **bill_type**, total_amount, payment_status, payment_method, billed_by, **inventory_id, quantity**, created_at |
| `inventory` | item_id, item_name, category, unit_price, **unit_cost**, stock_quantity, reorder_level, expiry_date, updated_at (a row with category `Room` is a room: stock_quantity = beds, unit_price = price per day) |

There are **10 tables and no others** (the `messages` table is the chat). `sql/hospital_db.sql` creates exactly these. `sql/migration_v3.sql`, `sql/migration_v4.sql`, `sql/migration_v5.sql` and `sql/migration_v6.sql` and `sql/migration_v7.sql` add (`sql/migration_v8.sql` creates the `messages` table of the chat and `sql/migration_v9.sql` adds the columns for photos, videos and pins) the new (bold) columns to a database
that was made with the older script and move the rooms into `inventory` - no new table.

## 5. The small helper classes

| Class | What it does |
|---|---|
| `DatabaseConnection` | opens a MySQL connection from `db.properties` |
| `Session` | remembers who is signed in |
| `Validators` | checks what the user typed (empty, number, money, password length) |
| `PasswordUtil` | BCrypt hash / verify |
| `Formats` | formats dates, times and money |
| `AlertUtil` | pop-up messages (info, warning, error, yes/no) in the style of the program |
| `ChatFiles`, `ChatContent`, `EmojiPicker` | the chat extras: choose and shrink a photo (at most 1280 px, saved as JPG), check a video, open or save a file; the photo and video parts of a message bubble; the emoji pop-up; |
| `AppNavigator` | loads an FXML screen into the window |
| `Permissions` | the table of what each role may do; `has()` hides screens and buttons, `require()` stops the DAO |
| `UiFactory` | the small statistic cards, the role titles and a drop-down helper |
| `Mailer`, `OtpService`, `ForgotPasswordDialog` | send an e-mail through Gmail SMTP; make and check the 6-digit code (kept in memory, 5 minutes, 3 tries; a right code gives a one-time token for the new password); the three-step OTP window (colours: the `otp-*` classes of style.css) |

## 6. Roles and permissions

See `ROLES.md`. The table is in `src/util/Permissions.java`; the menu (`MainController`), the screens and the DAO
methods that change data all use it. Doctors, nurses and department admins see only the patients of their own department: the
DAO read methods add `DaoSupport.doctorScope(...)` / `patientScope(...)` to the SQL when `Permissions.isDepartmentScoped()`.

## 7. Protected history

The foreign keys use `ON DELETE RESTRICT`: a patient or staff account that already has appointments, medical
records, vitals or bills cannot be deleted. The program shows a message instead of losing the history.
