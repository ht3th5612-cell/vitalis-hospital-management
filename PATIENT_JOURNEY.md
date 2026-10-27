# VITALIS - the patient's journey, from arriving to leaving (and paying)

Who may do each step is in `ROLES.md`. This file follows one patient through the program.

```
ARRIVE            SEE THE DOCTOR              STAY (optional)           PAY + LEAVE
Reception   ->    Nurse (optional) ->  Doctor     ->    Admit to a room  ->  Reception: Checkout
register +        vitals               diagnosis        (the doctor or        one payment =
booking or                             "Save and         the reception)       doctor's fee + room + medicines
walk-in                                 complete"                             (discharges an admitted patient)
patients          patient_vitals       medical_records  appointments         billings (+ inventory stock goes down)
appointments                           appointments     (room, admitted_at)
```

## Step by step

| # | What happens | Who | Screen | Function | Table |
|---|---|---|---|---|---|
| 1 | The patient arrives. A new patient is registered with name, **date of birth**, gender and **NRC** (a child has none); a returning patient is **found** (Find patient: name, date of birth, NRC, gender) and not registered again. | Receptionist | Patients | `PatientDAO.create()` | `patients` |
| 2a | **Booked in advance:** an appointment with a doctor is booked (status *Scheduled*). | Receptionist | Appointments | `AppointmentDAO.create()` | `appointments` |
| 2b | **Walk-in** (the patient is here now): **Walk-in** starts a visit with the doctor now. A patient who was booked for today and comes early gets the same visit. | Receptionist, Doctor | Appointments / Consultation | `AppointmentDAO.startWalkIn()` | `appointments` |
| 3 | The nurse measures blood pressure, temperature and pulse. A booking is not needed. | Nurse | Patient Vitals | `VitalDAO.create()` | `patient_vitals` |
| 4 | The doctor sees the list of own patients (also the ones booked for a later day, a patient may come earlier) and opens one: details, latest vitals, earlier diagnoses. | Doctor | Consultation | `AppointmentDAO.findOpenForDoctor()`, `VitalDAO.findLatest()`, `MedicalRecordDAO.findByPatient()` | `appointments`, `patient_vitals`, `medical_records` |
| 5a | **Outpatient:** the doctor writes the diagnosis and presses **Save and complete the visit**: the visit becomes *Completed* and the record is saved. | Doctor | Consultation | `AppointmentDAO.updateStatus()`, `MedicalRecordDAO.create()` | `appointments`, `medical_records` |
| 5b | **Inpatient:** the doctor presses **Save and admit** with a room that has a free bed: status *Admitted*, `visit_type = Inpatient`, `admitted_at` = now. | Doctor / Receptionist | Consultation / Appointments | `AppointmentDAO.admit()` | `appointments`, `inventory` (rooms) |
| 6 | **Checkout:** the cashier chooses the visit. The bill shows the doctor's fee, the room (days x price per day) and, after the cashier adds them, the medicines. | Receptionist | Checkout | `BillingDAO.preview()` | `appointments`, `doctors`, `inventory` |
| 7 | **Payment:** one press of **Pay** saves every line as *Paid*, takes the medicines out of the stock and, for an admitted patient, **discharges** the patient (`discharged_at` = now). The receipt opens and can be printed. | Receptionist | Checkout | `BillingDAO.checkout()`, `Receipt.show()` | `billings`, `inventory`, `appointments` |
| 8 | The visit is Completed or Discharged and Paid: the patient leaves. | | | | |

A patient who is admitted can also be discharged earlier with **Discharge** on the Appointments screen; the room days
are then counted until that time.

## Background work

| Who | Screen | What |
|---|---|---|
| Owner | Rooms | add a room, change its beds and price per day, close or remove it |
| Owner | Inventory | add a new medicine (price, cost), change prices |
| Inventory staff | Inventory | see the low-stock rows (*Show only low stock*) and **Add to stock** when a delivery arrives |
| Owner / Department admin | Staff | hire (role, department, monthly salary; a doctor also gets specialisation and consultation fee), **dismiss** (inactive account); the admin only for the doctors and nurses of the own department |
| Owner | Reports | income, expenses (salaries + medicine cost), profit, medicine usage, low stock, 6-month chart |

## How the tables connect

```
patients ──< appointments >── users (doctor)      appointments: visit_type, room (a room name), admitted_at, discharged_at
    │              │
    │              └──< medical_records
    ├──< patient_vitals >── users (nurse)
    └──< billings >── appointments (which visit; one row per line: Service / Room / Medicine)
              └──── inventory (which medicine, how many: inventory_id, quantity)

inventory also holds the ROOMS (category 'Room'): item_name = room name, stock_quantity = beds, unit_price = price per day
users.monthly_salary = the salary expense      inventory.unit_cost = what a medicine costs the hospital
```

`patient_id` ties everything to the same patient: one history of visits, vitals, records and bills.

## Things to know

* **Front-desk checks** (in the DAO, so they hold on every screen): the same NRC, or the same name with the same date of birth, cannot be registered twice; the age is worked out from the date of birth; a
  doctor cannot have two appointments at the same date and time, a patient cannot be at two doctors at the same time;
  appointments in the past are refused; an inactive doctor cannot be booked; a new appointment always starts as
  *Scheduled*; a patient already admitted cannot be admitted again or start another visit before the discharge.
* **Rooms** are rows of the inventory table (category `Room`). A room has a number of **beds**: it is offered in the
  Admit drop-down only while a bed is free (beds - patients with status *Admitted* in it). The owner changes the rooms on
  the Rooms screen; a bed with a patient cannot be removed and a room with history cannot be renamed or deleted (set its
  beds to 0 to close it).
* **Who is in the hospital:** the Patients screen is the permanent register (a patient who has left stays there, to be found when coming back). The nurse (Vitals) and the doctor (Medical Records) choose from `PatientDAO.findInHospital()`: patients with a visit waiting today, an admitted patient, or a finished visit that is not paid yet. After the checkout the patient disappears from these lists. To book a returning patient the Appointments screen and **See now** still list the whole register.
* **Walk-in** works for a patient who has no appointment (or comes earlier); the patient must be registered at the
  reception first.
* A prescription is free text; medicine is sold (and the stock reduced) only at the Checkout.
* The rules that protect the money are in `BILLING_RULES.md`.

## A 3-minute demo

1. `reception`: **Patients** -> register a patient -> **Appointments** -> pick the patient and Dr. Bob -> **Walk-in**.
2. `nurse.amy`: **Patient Vitals** -> record 120/80, 36.8, 72.
3. `dr.bob`: **Consultation** -> select the patient -> diagnosis -> **Save and admit** with *Ward B* (or **Save and complete the visit**).
4. `reception`: **Checkout** -> the visit -> add Paracetamol x 10 -> **Pay**: the fee 80, the room and the medicine are one bill;
   the patient is discharged and the receipt opens.
5. `inventory`: **Inventory** -> *Show only low stock* -> select an item -> quantity -> **Add to stock**.
6. `owner`: **Reports** (income, expenses, medicine usage) and **Rooms** (add a room).
7. `admin`: **Staff** -> hire a nurse for the own department; try another department: it is refused.
