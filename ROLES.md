# VITALIS - the 6 roles and what each one may do

The whole table lives in one file: `src/util/Permissions.java`.
* The **left menu** shows a screen only if the role has its permission (`MainController`).
* Each **screen** hides the form or disables the button the role may not use.
* Each **DAO** method that changes data calls `Permissions.require(...)` first, so a role cannot do it even
  if a screen is changed. (Reading data is controlled by the menu only.)

| Role | Demo login | In one sentence |
|---|---|---|
| `OWNER` | `owner` | Super admin: sees the money (income, expenses, medicine usage), the staff and the patients; hires and dismisses everybody; adds medicines and rooms |
| `ADMIN` | `admin` | Department admin: works like a doctor **and** hires / dismisses the doctors and nurses of the own department |
| `RECEPTIONIST` | `reception` | Front desk **and** cashier: registers patients, books (in advance or walk-in), takes the one payment of a visit |
| `DOCTOR` | `dr.alice`, `dr.bob` | Consultation screen: sees own patients (also a patient without appointment), writes the diagnosis, completes or admits |
| `NURSE` | `nurse.amy` | Records vitals of any patient, sees patients and records |
| `INVENTORY` | `inventory` | Keeps the stock: adds new medicines with their selling price, restocks the medicines that run low |

## Permission matrix (✔ = allowed, view = can only look)

| Screen / action | Owner | Admin | Reception / Cashier | Doctor | Nurse | Inventory |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Dashboard (each role sees its own version, see below) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| **Patients** - view | ✔ | ✔ | ✔ | ✔ | ✔ | |
| Patients - register / update | | | ✔ | | | |
| Patients - delete (a patient registered by mistake, without any history) | | | ✔ | | | |
| **Appointments** - view | | own department | ✔ | own department | own department | |
| Appointments - book / walk-in / delete | | | ✔ | | | |
| Appointments - status, **admit**, **discharge** | | ✔ | ✔ | ✔ | | |
| **Consultation** (see a patient, diagnosis, complete / admit, walk-in) | | ✔ | | ✔ | | |
| **Patient Vitals** - view | ✔ | ✔ | | ✔ | ✔ | |
| Patient Vitals - record / delete | | | | | ✔ | |
| **Medical Records** - view | ✔ (Records) | own department | | own department | own department | |
| Medical Records - write | | ✔ | | ✔ | | |
| **Bills** (list of all bill lines) - view only | ✔ (a tab of Records) | | ✔ | | | |
| **Checkout** - take the payment of a visit | | | ✔ | | | |
| **Inventory** - view | ✔ | | ✔ | | | ✔ |
| Inventory - **restock** (add delivered stock) | ✔ | | | | | ✔ |
| Inventory - add a new medicine, set its selling price and cost, delete | ✔ | | | | | ✔ |
| **Rooms** - add / change beds and price / remove | ✔ | | | | | |
| **Staff** (hire, salary, dismiss; a doctor also has specialisation + fee) | ✔ (every role, also admins) | doctors and nurses of own department | | | | |
| **Departments** | ✔ | | | | | |
| Create / change an OWNER account | ✔ | | | | | |
| **Reports** (income, expenses, medicine usage) | ✔ | | | | | |

Notes
* Writing a medical record and recording vitals are clinical acts, so the owner does not do them. The department admin
  is a senior doctor, so the admin may write records and see patients like a doctor (the admin has no consultation fee).
* "own department": a doctor, a nurse and a department admin see only the **patients of their own department** - the patients who have (or had) a visit with a doctor of that department - in the Patients, Appointments, Medical Records and Vitals screens and in their drop-downs (`DaoSupport.scopeDepartment()`, applied in the DAO). The owner and the reception see every department. **Find patient** (booking and *See now*) still searches the whole register, so that a returning patient who has never been in this department can be found; as soon as a visit with the doctor starts, the patient belongs to the department.
* **My profile** (click the name and picture at the bottom of the left menu; every role, own account only): change the own name, phone, e-mail, date of birth and gender - the **current password is needed to change the phone number or the e-mail**; change the own password - the **old password is needed** (five wrong passwords in a row lock the screen until the next sign-in). The username, role, department, NRC and salary are changed only by the owner or the department admin on the Staff screen (`UserDAO.updateOwnProfile`, `UserDAO.changeOwnPassword`).
* **Profile picture:** on *My profile* press *Change photo* (a PNG / JPG / GIF / BMP file; it is cut to a square and saved as a small PNG in `users.photo`) or *Remove photo*. The picture and the name at the bottom of the left menu show it; **a click on them opens My profile**.
* **Messages** (menu *Messages*, every role): a one-to-one chat between any two active staff members. A red number on the menu and on the staff list shows the unread messages; the screen checks for new messages every 4 seconds. Besides text (with emoji from the smiley button) a message can be a **photo** or a **video** (one paperclip button opens one window for both; video up to 25 MB; *Play* opens it in the player of the computer, *Save* keeps a copy). Text written before sending a photo or video becomes its caption. Either person can **pin** a message (*Pin* under it); the pinned messages are shown in a bar above the chat, and a click on the bar jumps to them. Only the two people of a chat can open its files. A message cannot be edited or deleted; a dismissed staff member cannot receive messages; an account that has messages cannot be deleted (it is dismissed instead).
* **Forgot password** (sign-in screen, every role): the user types the e-mail address of the staff account and a one-time code (OTP) is e-mailed to it; see README. Nothing is revealed about whether an address belongs to an account.
* A **department admin is a doctor too**: the admin appears in the doctor lists, can be booked, and the owner can give the admin a specialisation and a consultation fee on the Staff screen.
* "own department": the DAO (`UserDAO.requireManageable`, `DoctorDAO`) refuses to hire, change, dismiss or delete anybody
  who is not a DOCTOR or NURSE of the admin's own department, and the lists show only those people.
* The receptionist sees the Inventory screen read-only so that the medicines are known when the bill is made.
* Rooms are rows of the `inventory` table with the category `Room`; the Inventory screen never shows them.

## What each role does, step by step

**Owner**
1. Departments: create a new department when the hospital opens one.
2. Staff: hire a person (role, **department** - required for a doctor, nurse and department admin - and **monthly salary**); the owner chooses any department; **Dismiss** makes the account inactive
   (it cannot sign in and the salary is not counted any more; the history stays).
2. Rooms: add a room, change its number of beds or its price per day, close or remove it.
3. Inventory: add a new medicine (selling price and **cost**), change prices.
5. Reports: income, expenses (salaries + medicine cost), profit, which medicines are used most, low stock.
6. **Records**: one screen with three tabs (view only: the owner does not register, book, take payments or treat) - **Patient history** (every patient ever registered; select one to see the visits, medical records and vital signs), **In hospital now** (the patients who are in the hospital at this moment, with doctor, room, latest vitals and diagnosis) and **Bills**.

**Department admin**
1. Staff: hire and dismiss the doctors and nurses of the own department only.
2. Consultation, Medical Records, Appointments, Vitals: the same as a doctor.

**Receptionist / Cashier**
1. Patients: register the patient.
2. Appointments: book a visit in advance, or press **Walk-in** when the patient is here now.
3. Checkout: choose the visit, add medicines if the patient buys some, choose the payment method, press **Pay**.
   An admitted patient is discharged by the same button.

**Doctor**
1. Consultation: choose a patient of the list (or **See now** for a patient without appointment), write the diagnosis,
   press **Save and complete the visit** (or **Save and admit**).

**Nurse**
1. Patient Vitals: records blood pressure, temperature, pulse of any patient (an appointment is not needed).

**Inventory staff**
1. Inventory: tick *Show only low stock*, select an item, enter the delivered quantity (and the cost), **Add to stock**.
2. Inventory: **New inventory item** - name, category, **unit price (the selling price)**, unit cost, stock, reorder level, expiry; select an item to change its price.

## The dashboard of each role

Every role opens its own dashboard. The widgets are built in *DashboardController* with the small helpers of *DashboardWidgets*
(KPI card, area chart, donut, table with status pills). A doctor, nurse or admin only gets the data of the own department.

| Role | Cards | Charts | Tables |
|---|---|---|---|
| **Owner** | registered patients, patients in hospital, income and profit of the month | Patient Visits (7 days), Bed Occupancy, Visit Status | Department Overview, Patients in Hospital, Low Stock |
| **Admin** | staff of the department, visits today, patients in hospital, visits this month | Patient Visits, Visit Status | Today's Visits, Patients in Hospital (with diagnosis) |
| **Doctor** | my visits today, my open patients, patients in hospital, my records | my Patient Visits, my Visit Status | My Schedule Today, Patients in Hospital (with diagnosis) |
| **Nurse** | patients in hospital, staying in rooms, vitals recorded today, visits today | Patient Visits, Visit Status | Patients in Hospital (with the latest vitals), Today's Visits |
| **Reception / Cashier** | visits today, patients in hospital, waiting to pay, revenue collected | Patient Visits, Bed Occupancy | Today's Visits, Waiting for Checkout |
| **Inventory** | items, low stock, expiring in 30 days, stock value | Items by Category | Low Stock, Expiring Soon |
