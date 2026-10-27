# VITALIS - Hospital Management System

A JavaFX desktop application with a MySQL / MariaDB (XAMPP) database.
Patients come booked or as walk-ins, are treated as outpatients or admitted to a room, and pay once at the checkout
(doctor's fee + room + medicines). The owner sees income, expenses and medicine usage.

## How to run

1. Start **MySQL** in the XAMPP Control Panel.
2. New installation: import `sql/hospital_db.sql` in phpMyAdmin (creates the database and demo data).
   **Existing database from an older version:** import `sql/migration_v2.sql`, `sql/migration_v3.sql`, `sql/migration_v4.sql`, `sql/migration_v5.sql`, `sql/migration_v6.sql`, `sql/migration_v7.sql` and `sql/migration_v8.sql`
   in order, each once (keeps your data; they only add columns - there are still no new tables - and `migration_v4.sql`
   moves the rooms into the inventory table; v5 adds the date of birth and the NRC of a patient; v6 adds date of birth, gender, NRC and e-mail to the staff and the e-mail to the patients; v7 adds the profile picture of a user). Optional: `sql/cleanup_unused.sql` removes leftovers of
   earlier versions. If the program shows a "Unknown column" error, the migration has not been imported yet.
3. Run `run.bat`, or start `src/app/Launcher.java` from Eclipse.

Demo accounts (password `123456`): `owner`, `admin`, `dr.alice`, `dr.bob`, `nurse.amy`, `reception`, `inventory`.
(An old database may also have `cashier`; it is a Receptionist now.)

Database settings: `src/db.properties`.

**Forgot password:** on the sign-in screen press *Forgot password?*, enter the **e-mail address** of the staff account and *Send code*: a 6-digit code (valid 5 minutes, 3 tries) is sent to that address (saved on the Staff screen > Email; an address belongs to one account), then type the code in the **OTP Verification** window (one box for each digit, a countdown, wrong code = red boxes, right code = green boxes, *Resend* for a new code) and choose a new password. The e-mail is sent through Gmail SMTP from the account in `src/mail.properties` (copy `src/mail.properties.example`; use a Gmail *App password*). An account without an e-mail address cannot use it: the owner sets the address on the Staff screen.

## Read these

| File | What it explains |
|---|---|
| `ROLES.md` | the 6 roles, the permission matrix, what each role does |
| `PATIENT_JOURNEY.md` | the whole procedure from arrival to leaving and paying, with a 3-minute demo |
| `BILLING_RULES.md` | how the checkout, income and expenses are calculated and the rules that protect the money |
| `PROJECT_GUIDE.md` | every table, which function uses it and which screen calls it |

## Project structure (MVC + DAO)

| Folder | What it is |
|---|---|
| `src/view` | FXML screens + `style.css` |
| `src/controller` | One controller per screen: reads the form, calls a DAO, shows the result |
| `src/model` | Plain Java objects (Patient, Appointment, Billing, ...) |
| `src/dao` | All SQL, one DAO per table |
| `src/database` | `DatabaseConnection` |
| `src/util` | Helpers: `Permissions` (who may do what), `Session`, `Validators`, `PasswordUtil`, `Formats`, `AlertUtil` |
| `sql` | Database scripts |
| `test` | `AllTests.java` - run `test.bat` (works on a throw-away database, never on `hospital_db`) |

One action, for example *Save patient*:
`patients.fxml` button -> `PatientController.handleSave()` -> `Validators` -> `PatientDAO.create()` -> `Permissions.require(...)` -> MySQL.

## Points to explain in the presentation

* **Roles and permissions** - one table in `Permissions.java`; it drives the menu, the buttons and the DAOs.
* **Login** - BCrypt password hash (`PasswordUtil`), `PreparedStatement` against SQL injection.
* **Consultation** - the doctor opens a patient (booked, early or walk-in), writes the diagnosis and presses one button.
* **Admit / discharge** - the same `appointments` row becomes an inpatient visit (room, admitted_at, discharged_at); the
  payment at the checkout discharges the patient.
* **Checkout** - one payment per visit: the DAO calculates the doctor's fee + the room (days x price per day) + the
  medicines; the stock goes down in the same database transaction.
* **Rooms** - rows of the inventory table (category `Room`: beds and price per day); the owner adds or closes rooms.
* **Finance** - income = paid bills; expenses = salaries + the cost of the medicines used (`users.monthly_salary`,
  `inventory.unit_cost`); the report also lists the medicine usage.
* **Department admin** - a doctor who may hire and dismiss only the doctors and nurses of the own department.
* **Protected history** - foreign keys use `ON DELETE RESTRICT`: a patient or account with history cannot be deleted.
* **Tests** - `test.bat` checks the permissions, department-admin limits, rooms, walk-in, checkout, stock, restock, the finance
  numbers and the protected history (249 checks).
