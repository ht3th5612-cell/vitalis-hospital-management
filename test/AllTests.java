import dao.AppointmentDAO;
import dao.BillingDAO;
import dao.DepartmentDAO;
import dao.DoctorDAO;
import dao.InventoryDAO;
import dao.MedicalRecordDAO;
import dao.MessageDAO;
import dao.PatientDAO;
import dao.ReportDAO;
import dao.RoomDAO;
import dao.UserDAO;
import dao.VitalDAO;
import model.Appointment;
import model.Billing;
import model.InventoryItem;
import model.MedicalRecord;
import model.Message;
import model.Patient;
import model.User;
import model.Vital;
import util.Mailer;
import util.OtpService;
import util.Permissions;
import util.Receipt;
import util.Session;
import util.Validators;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AllTests {

    private static final String TEST_DB = "hospital_db_test";
    private static final String SERVER = "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/" + TEST_DB
            + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        createTestDatabase();
        try {
            permissionTable();
            daoRefusesWhatTheRoleMayNotDo();
            staffAccounts();
            departmentAdmin();
            identityAndEmail();
            forgotPasswordOtp();
            myProfile();
            profilePhoto();
            messages();
            chatFilesAndPins();
            departmentScope();
            loginAndRoles();
            receptionRules();
            rooms();
            walkInAndConsultation();
            admitAndDischarge();
            checkout();
            medicineRules();
            receipt();
            moneyRules();
            restock();
            financeReport();
            historyIsProtected();
            doctorSeesOwnAppointmentsOnly();
            dashboardData();
            validators();
        } finally {
            dropTestDatabase();
        }
        System.out.println();
        System.out.println(passed + " checks passed, " + failures.size() + " failed.");
        failures.forEach(f -> System.out.println("  FAILED: " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void permissionTable() throws Exception {
        section("permission table");
        signIn("owner");
        check("owner only looks at the bills (no payment, no booking)",
                Permissions.has(Permissions.BILL_VIEW) && !Permissions.has(Permissions.BILL_CREATE)
                        && !Permissions.has(Permissions.APPOINTMENT_VIEW) && !Permissions.has(Permissions.APPOINTMENT_BOOK));
        check("owner can manage owner accounts", Permissions.has(Permissions.OWNER_ACCOUNTS));
        check("owner sees the reports and manages rooms and medicines",
                Permissions.has(Permissions.REPORT_VIEW) && Permissions.has(Permissions.ROOM_MANAGE)
                        && Permissions.has(Permissions.INVENTORY_EDIT));
        check("owner does not write medical records", !Permissions.has(Permissions.RECORD_WRITE));
        check("owner only looks at the patients (no registering, no deleting)",
                Permissions.has(Permissions.PATIENT_VIEW) && !Permissions.has(Permissions.PATIENT_EDIT)
                        && !Permissions.has(Permissions.PATIENT_DELETE) && Permissions.has(Permissions.RECORDS_OVERVIEW));

        signIn("admin");
        check("admin manages staff", Permissions.has(Permissions.STAFF_MANAGE) && Permissions.has(Permissions.DOCTOR_MANAGE));
        check("admin works like a doctor", Permissions.has(Permissions.RECORD_WRITE) && Permissions.has(Permissions.APPOINTMENT_UPDATE));
        check("admin has no bills, no reports, no rooms, no stock",
                !Permissions.has(Permissions.BILL_VIEW) && !Permissions.has(Permissions.REPORT_VIEW)
                        && !Permissions.has(Permissions.ROOM_MANAGE) && !Permissions.has(Permissions.INVENTORY_VIEW));
        check("admin cannot manage owner accounts", !Permissions.has(Permissions.OWNER_ACCOUNTS));
        check("admin is a department admin", Permissions.isDepartmentAdmin());

        signIn("reception");
        check("reception registers patients and books", Permissions.has(Permissions.PATIENT_EDIT) && Permissions.has(Permissions.APPOINTMENT_BOOK));
        check("reception takes payments", Permissions.has(Permissions.BILL_CREATE));
        check("reception can delete a patient that was registered by mistake", Permissions.has(Permissions.PATIENT_DELETE));
        check("reception cannot manage staff or rooms", !Permissions.has(Permissions.STAFF_MANAGE) && !Permissions.has(Permissions.ROOM_MANAGE));

        signIn("dr.alice");
        check("doctor writes medical records", Permissions.has(Permissions.RECORD_WRITE));
        check("doctor does not record vitals or bills",
                !Permissions.has(Permissions.VITAL_RECORD) && !Permissions.has(Permissions.BILL_VIEW));

        signIn("nurse.amy");
        check("nurse records vitals", Permissions.has(Permissions.VITAL_RECORD));
        check("nurse only views patients and records",
                Permissions.has(Permissions.PATIENT_VIEW) && !Permissions.has(Permissions.PATIENT_EDIT)
                        && Permissions.has(Permissions.RECORD_VIEW) && !Permissions.has(Permissions.RECORD_WRITE));

        signIn("inventory");
        check("inventory staff view and restock", Permissions.has(Permissions.INVENTORY_VIEW) && Permissions.has(Permissions.INVENTORY_RESTOCK));
        check("inventory staff add new medicines and set the price", Permissions.has(Permissions.INVENTORY_EDIT));
        check("inventory staff cannot manage rooms or staff", !Permissions.has(Permissions.ROOM_MANAGE) && !Permissions.has(Permissions.STAFF_MANAGE));
        check("inventory staff see nothing clinical",
                !Permissions.has(Permissions.PATIENT_VIEW) && !Permissions.has(Permissions.APPOINTMENT_VIEW));

        Session.clear();
        check("nobody signed in has no permission", !Permissions.has(Permissions.PATIENT_VIEW));
    }

    private static void daoRefusesWhatTheRoleMayNotDo() throws Exception {
        section("the DAO refuses");
        signIn("nurse.amy");
        refused("nurse cannot register a patient", () -> PatientDAO.create(newPatient("Nope")));
        MedicalRecord record = new MedicalRecord();
        record.setPatientId(1);
        record.setDoctorId(5);
        record.setDiagnosis("x");
        refused("nurse cannot write a medical record", () -> MedicalRecordDAO.create(record));

        signIn("dr.alice");
        refused("doctor cannot record vitals", () -> VitalDAO.create(newVital(1, 5)));
        refused("doctor cannot take a payment", () -> BillingDAO.checkout(1, "Cash", List.of(), 3));
        refused("doctor cannot book an appointment", () -> AppointmentDAO.create(newAppointment(1, 3)));
        refused("doctor cannot add a room", () -> RoomDAO.create("Nope", 1, 10));

        signIn("admin");
        refused("admin cannot take a payment", () -> BillingDAO.checkout(1, "Cash", List.of(), 2));
        signIn("owner");
        refused("the owner cannot take a payment", () -> BillingDAO.checkout(1, "Cash", List.of(), 1));
        refused("the owner cannot book an appointment", () -> AppointmentDAO.create(newAppointment(1, 3)));
        refused("the owner cannot admit a patient", () -> AppointmentDAO.admit(1, "Ward A"));
        signIn("admin");
        refused("admin cannot delete a patient", () -> PatientDAO.delete(1));
        refused("admin cannot add a room", () -> RoomDAO.create("Nope", 1, 10));

        signIn("reception");
        expectIllegal("reception cannot delete a patient that has history (the database protects it)", () -> PatientDAO.delete(1));
        refused("reception cannot change the stock", () -> InventoryDAO.delete(1));
        refused("reception cannot add a department", () -> DepartmentDAO.create("Nope", ""));
        refused("reception cannot add a room", () -> RoomDAO.create("Nope", 1, 10));

        signIn("inventory");
        refused("inventory staff cannot register a patient", () -> PatientDAO.create(newPatient("Nope")));

        Session.clear();
        refused("nobody signed in cannot change anything", () -> PatientDAO.create(newPatient("Nope")));

        signIn("reception");
        long patientsBefore = count("SELECT COUNT(*) FROM patients");
        PatientDAO.create(newPatient("Reception Patient"));
        check("reception can register a patient", count("SELECT COUNT(*) FROM patients") == patientsBefore + 1);
        signIn("nurse.amy");
        VitalDAO.create(newVital(1, 5));
        check("nurse can record vitals", count("SELECT COUNT(*) FROM patient_vitals") == 6);
        signIn("dr.alice");
        record.setDoctorId(3);
        MedicalRecordDAO.create(record);
        check("doctor can write a medical record", count("SELECT COUNT(*) FROM medical_records") == 5);
    }

    private static void staffAccounts() throws Exception {
        section("staff accounts");
        signIn("reception");
        refused("reception cannot create staff", () -> UserDAO.create("x.user", "secret1", "X", "NURSE", "", null, 0));

        signIn("admin");
        refused("admin cannot create an OWNER account", () -> UserDAO.create("x.owner", "secret1", "X", "OWNER", "", 1, 0));
        refused("admin cannot reset the owner's password", () -> UserDAO.updatePassword(1, "secret1"));
        refused("admin cannot delete the owner", () -> UserDAO.delete(1));

        signIn("owner");
        int owner2 = UserDAO.create("second.owner", "secret1", "Second Owner", "OWNER", "", null, 0);
        check("owner can create another owner", owner2 > 0);
        expectIllegal("a weak password is refused", () -> UserDAO.create("weak.user", "123", "Weak", "NURSE", "", 1, 0));
        expectIllegal("a duplicate username is refused", () -> UserDAO.create("owner", "secret1", "Dup", "NURSE", "", 1, 0));
        expectIllegal("a negative salary is refused", () -> UserDAO.create("neg.user", "secret1", "Neg", "NURSE", "", 1, -1));
        UserDAO.create("paid.nurse", "secret1", "Paid Nurse", "NURSE", "", 1, 650);
        check("the salary is saved with the account",
                UserDAO.findAll("paid.nurse").get(0).getMonthlySalary() == 650.00);
        UserDAO.delete(UserDAO.findAll("paid.nurse").get(0).getUserId());

        expectIllegal("a nurse needs a department", () -> UserDAO.create("no.dept1", "secret1", "No Dept", "NURSE", "", null, 0));
        expectIllegal("a doctor needs a department", () -> UserDAO.create("no.dept2", "secret1", "No Dept", "DOCTOR", "", null, 0));
        expectIllegal("a department admin needs a department", () -> UserDAO.create("no.dept3", "secret1", "No Dept", "ADMIN", "", null, 0));
        int receptionist = UserDAO.create("desk.two", "secret1", "Desk Two", "RECEPTIONIST", "", null, 450);
        check("a receptionist needs no department", receptionist > 0);
        UserDAO.delete(receptionist);
        DepartmentDAO.create("Radiology", "X-ray and scans");
        int radiology = (int) count("SELECT department_id FROM departments WHERE department_name = 'Radiology'");
        int radNurse = UserDAO.create("rad.nurse", "secret1", "Rad Nurse", "NURSE", "", radiology, 600);
        int radAdmin = UserDAO.create("rad.admin", "secret1", "Rad Admin", "ADMIN", "", radiology, 900);
        check("the owner hires a nurse and an admin into a department that was just created",
                count("SELECT COUNT(*) FROM users WHERE department_id = " + radiology) == 2);
        User moved = new User();
        moved.setUserId(radNurse);
        moved.setFullName("Rad Nurse");
        moved.setRole("NURSE");
        moved.setDepartmentId(null);
        expectIllegal("a nurse cannot be left without a department", () -> UserDAO.update(moved));
        moved.setDepartmentId(1);
        UserDAO.update(moved);
        check("the owner moves a nurse to another department", count("SELECT department_id FROM users WHERE user_id = " + radNurse) == 1);
        UserDAO.delete(radNurse);
        UserDAO.delete(radAdmin);
        DepartmentDAO.delete(radiology);
    }

    private static void departmentAdmin() throws Exception {
        section("department admin");
        signIn("admin");
        List<User> visible = UserDAO.findAll("");
        check("the admin lists only the doctors and nurses of the own department",
                visible.stream().anyMatch(u -> u.getUsername().equals("dr.alice"))
                        && visible.stream().anyMatch(u -> u.getUsername().equals("nurse.amy"))
                        && visible.stream().noneMatch(u -> u.getUsername().equals("dr.bob"))
                        && visible.stream().noneMatch(u -> u.getUsername().equals("owner"))
                        && visible.stream().noneMatch(u -> u.getUsername().equals("reception")));
        check("the doctor list shows only the own department, the department admin is a doctor too",
                DoctorDAO.findAll("").stream().allMatch(d -> d.getDepartmentId() == 1)
                        && DoctorDAO.findAll("").stream().anyMatch(d -> d.getUserId() == 3)
                        && DoctorDAO.findAll("").stream().anyMatch(d -> d.getUserId() == 2)
                        && DoctorDAO.findAll("").stream().noneMatch(d -> d.getUserId() == 4));

        int nurse = UserDAO.create("new.nurse", "secret1", "New Nurse", "NURSE", "", 1, 600);
        check("the admin hires a nurse into the own department", nurse > 0);
        refused("the admin cannot hire into another department", () -> UserDAO.create("far.nurse", "secret1", "Far", "NURSE", "", 3, 0));
        refused("the admin cannot hire a receptionist", () -> UserDAO.create("rec.two", "secret1", "Rec", "RECEPTIONIST", "", 1, 0));
        refused("the admin cannot hire an admin", () -> UserDAO.create("adm.two", "secret1", "Adm", "ADMIN", "", 1, 0));
        refused("the admin cannot hire a doctor for another department",
                () -> UserDAO.create("far.doc", "secret1", "Far Doc", "DOCTOR", "", 3, 10));
        int doctor = UserDAO.create("new.doc", "secret1", "New Doc", "DOCTOR", "", 1, 900);
        DoctorDAO.saveProfile(doctor, 1, "General", 40);
        check("the admin hires a doctor with specialisation and fee into the own department",
                doctor > 0 && DoctorDAO.findAll("new.doc").get(0).getConsultationFee() == 40.00
                        && "General".equals(DoctorDAO.findAll("new.doc").get(0).getSpecialization()));
        DoctorDAO.saveProfile(doctor, 1, "General", 45);
        check("the doctor profile can be changed", DoctorDAO.findAll("new.doc").get(0).getConsultationFee() == 45.00);
        expectIllegal("a negative fee is refused", () -> DoctorDAO.saveProfile(doctor, 1, "General", -1));
        refused("the admin cannot change the profile of a doctor of another department", () -> DoctorDAO.saveProfile(4, 3, "Heart", 1));
        signIn("nurse.amy");
        refused("a nurse cannot save a doctor profile", () -> DoctorDAO.saveProfile(doctor, 1, "x", 1));
        signIn("admin");

        User dismissed = new User();
        dismissed.setUserId(nurse);
        dismissed.setFullName("New Nurse");
        dismissed.setRole("NURSE");
        dismissed.setDepartmentId(1);
        dismissed.setMonthlySalary(600);
        dismissed.setActive(false);
        UserDAO.update(dismissed);
        check("a dismissed nurse cannot sign in any more", UserDAO.authenticate("new.nurse", "secret1") == null);
        check("a dismissed nurse's salary is no longer counted",
                Math.abs(ReportDAO.salaryTotal() - count("SELECT SUM(monthly_salary) FROM users WHERE active = 1")) < 0.001);

        User bob = new User();
        bob.setUserId(4);
        bob.setFullName("Dr. Bob Lin");
        bob.setRole("DOCTOR");
        bob.setDepartmentId(3);
        bob.setActive(false);
        refused("the admin cannot dismiss a doctor of another department", () -> UserDAO.update(bob));
        refused("the admin cannot delete a doctor of another department", () -> UserDAO.delete(4));
        refused("the admin cannot reset the password of another department", () -> UserDAO.updatePassword(4, "secret1"));
        User alice = new User();
        alice.setUserId(3);
        alice.setFullName("Dr. Alice Smith");
        alice.setRole("DOCTOR");
        alice.setDepartmentId(3);
        refused("the admin cannot move an own doctor to another department", () -> UserDAO.update(alice));
        User promoted = new User();
        promoted.setUserId(5);
        promoted.setFullName("Nurse Amy Tun");
        promoted.setRole("RECEPTIONIST");
        promoted.setDepartmentId(1);
        refused("the admin cannot change a nurse into another role", () -> UserDAO.update(promoted));
        check("nothing changed after the refusals",
                count("SELECT COUNT(*) FROM users WHERE user_id = 5 AND role = 'NURSE' AND active = 1") == 1
                        && count("SELECT COUNT(*) FROM users WHERE user_id = 3 AND department_id = 1") == 1
                        && count("SELECT COUNT(*) FROM users WHERE user_id = 4 AND active = 1") == 1);

        UserDAO.delete(nurse);
        UserDAO.delete(doctor);
        check("accounts without history can be deleted by the admin",
                count("SELECT COUNT(*) FROM users WHERE username IN ('new.nurse', 'new.doc')") == 0);
    }

    private static void receptionRules() throws Exception {
        section("reception rules");
        signIn("reception");

        LocalDate birth = LocalDate.of(1985, 6, 15);
        Patient first = newPatient("Dup Patient");
        first.setDateOfBirth(birth);
        first.setNrc("12/LAMANA(N)777777");
        PatientDAO.create(first);
        int firstId = latestPatientId();
        Patient again = newPatient("  dup PATIENT ");
        again.setDateOfBirth(birth);
        expectIllegal("the same name and date of birth cannot be registered twice (case does not matter)", () -> PatientDAO.create(again));
        Patient sameNrc = newPatient("Other Name");
        sameNrc.setNrc(" 12/lamana(n)777777 ");
        expectIllegal("the same NRC cannot be registered twice (spaces and case do not matter)", () -> PatientDAO.create(sameNrc));
        Patient sameNameOtherBirth = newPatient("Dup Patient");
        sameNameOtherBirth.setDateOfBirth(birth.plusDays(1));
        PatientDAO.create(sameNameOtherBirth);
        check("the same name with another date of birth is a different person", count("SELECT COUNT(*) FROM patients WHERE name = 'Dup Patient'") == 2);
        Patient child1 = newPatient("Child One");
        child1.setDateOfBirth(LocalDate.now().minusYears(5));
        PatientDAO.create(child1);
        Patient child2 = newPatient("Child Two");
        child2.setDateOfBirth(LocalDate.now().minusYears(6));
        PatientDAO.create(child2);
        check("two patients without an NRC are allowed (a child has none)", count("SELECT COUNT(*) FROM patients WHERE nrc IS NULL AND name LIKE 'Child %'") == 2);
        Patient other = newPatient("Dup Patient");
        other.setPatientId(latestPatientIdByName("Dup Patient"));
        other.setDateOfBirth(birth);
        expectIllegal("changing a patient into an existing one is refused", () -> PatientDAO.update(other));
        Patient self = newPatient("Dup Patient");
        self.setPatientId(firstId);
        self.setDateOfBirth(birth);
        self.setNrc("12/LAMANA(N)777777");
        self.setPhone("09-123456789");
        PatientDAO.update(self);
        check("updating a patient without changing the identity is allowed",
                count("SELECT COUNT(*) FROM patients WHERE patient_id = " + firstId + " AND phone = '09-123456789'") == 1);
        Patient noBirth = newPatient("No Birth");
        noBirth.setDateOfBirth(null);
        expectIllegal("the date of birth is required", () -> PatientDAO.create(noBirth));
        Patient future = newPatient("Future Baby");
        future.setDateOfBirth(LocalDate.now().plusDays(1));
        expectIllegal("a date of birth in the future is refused", () -> PatientDAO.create(future));
        Patient tooOld = newPatient("Too Old");
        tooOld.setDateOfBirth(LocalDate.of(1850, 1, 1));
        expectIllegal("a date of birth before 1900 is refused", () -> PatientDAO.create(tooOld));
        Patient longNrc = newPatient("Long Nrc");
        longNrc.setNrc("X".repeat(31));
        expectIllegal("an NRC of more than 30 characters is refused", () -> PatientDAO.create(longNrc));
        check("the age is worked out from the date of birth", PatientDAO.findById(firstId).getAge()
                == java.time.Period.between(birth, LocalDate.now()).getYears()
                && count("SELECT age FROM patients WHERE patient_id = " + firstId) == PatientDAO.findById(firstId).getAge());

        check("a search without name, date of birth or NRC finds nobody", PatientDAO.search("", null, "", "Male").isEmpty());
        check("find by a part of the name (case does not matter)", PatientDAO.search("dup pat", null, null, null).size() == 2);
        check("find by date of birth", PatientDAO.search(null, birth, null, null).stream().anyMatch(p -> p.getPatientId() == firstId));
        check("find by NRC (spaces and case do not matter)", PatientDAO.search(null, null, "12/lamana (n) 777777", null).size() == 1);
        check("name + gender narrows the result", PatientDAO.search("Dup", null, null, "Female").size() == 2
                && PatientDAO.search("Dup", null, null, "Male").isEmpty());
        check("name + date of birth gives exactly one patient", PatientDAO.search("Dup Patient", birth, null, null).size() == 1);
        check("nobody is found for an unknown NRC", PatientDAO.search(null, null, "99/NONE(N)000000", null).isEmpty());
        check("a patient is found by NRC in the patient list too", PatientDAO.findAll("777777").size() == 1);
        check("the patient is found by id", PatientDAO.findById(firstId).getName().equals("Dup Patient") && PatientDAO.findById(999999) == null);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Appointment slot = newAppointment(firstId, 3);
        slot.setAppointmentDate(tomorrow);
        slot.setAppointmentTime(LocalTime.of(20, 15));
        slot.setStatus("Completed");
        int slotId = AppointmentDAO.create(slot);
        check("a new appointment always starts as Scheduled",
                "Scheduled".equals(AppointmentDAO.findByPatient(firstId).stream().filter(a -> a.getAppointmentId() == slotId).findFirst().orElseThrow().getStatus()));

        int otherPatient = latestPatientId();
        Appointment sameDoctor = newAppointment(otherPatient, 3);
        sameDoctor.setAppointmentDate(tomorrow);
        sameDoctor.setAppointmentTime(LocalTime.of(20, 15));
        expectIllegal("a doctor cannot have two appointments at the same time", () -> AppointmentDAO.create(sameDoctor));
        Appointment samePatient = newAppointment(firstId, 4);
        samePatient.setAppointmentDate(tomorrow);
        samePatient.setAppointmentTime(LocalTime.of(20, 15));
        expectIllegal("a patient cannot be at two doctors at the same time", () -> AppointmentDAO.create(samePatient));
        Appointment past = newAppointment(firstId, 3);
        past.setAppointmentDate(LocalDate.now().minusDays(1));
        expectIllegal("an appointment in the past is refused", () -> AppointmentDAO.create(past));

        AppointmentDAO.updateStatus(slotId, "Cancelled");
        int rebooked = AppointmentDAO.create(sameDoctor);
        check("the slot of a cancelled appointment can be booked again", rebooked > 0);
        expectIllegal("a cancelled appointment cannot be re-opened when its slot was taken meanwhile",
                () -> AppointmentDAO.updateStatus(slotId, "Scheduled"));

        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 0 WHERE user_id = 4");
        }
        check("an inactive doctor is not offered for booking",
                DoctorDAO.findActive().stream().noneMatch(d -> d.getUserId() == 4)
                        && DoctorDAO.findActive().stream().anyMatch(d -> d.getUserId() == 3));
        Appointment toInactive = newAppointment(firstId, 4);
        toInactive.setAppointmentDate(tomorrow);
        toInactive.setAppointmentTime(LocalTime.of(21, 0));
        expectIllegal("an appointment with an inactive doctor is refused", () -> AppointmentDAO.create(toInactive));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 1 WHERE user_id = 4");
        }
    }

    private static void rooms() throws Exception {
        section("rooms");
        signIn("reception");
        int beds = RoomDAO.findAll().stream().mapToInt(InventoryItem::getStockQuantity).sum();
        check("the demo hospital has 4 rooms with 35 beds", RoomDAO.findAll().size() == 4 && beds == 35);
        check("the demo patient is in Ward A", RoomDAO.occupiedBeds().getOrDefault("Ward A", 0) == 1);
        check("every room has a free bed", RoomDAO.freeRoomNames().size() == 4);
        check("rooms are not listed as medicines",
                InventoryDAO.findAll("").stream().noneMatch(i -> RoomDAO.CATEGORY.equals(i.getCategory())));

        signIn("owner");
        RoomDAO.create("Tiny Room", 1, 25);
        InventoryItem tiny = findRoom("Tiny Room");
        check("the owner adds a room", tiny.getStockQuantity() == 1 && tiny.getUnitPrice() == 25.00);
        expectIllegal("a room name cannot be used twice (case does not matter)", () -> RoomDAO.create(" tiny ROOM ", 2, 10));
        expectIllegal("a room needs a name", () -> RoomDAO.create(" ", 2, 10));
        expectIllegal("beds cannot be negative", () -> RoomDAO.create("Bad A", -1, 10));
        expectIllegal("too many beds are refused", () -> RoomDAO.create("Bad B", 501, 10));
        expectIllegal("the price per day must be above 0", () -> RoomDAO.create("Bad C", 2, 0));
        expectIllegal("NaN is not a price", () -> RoomDAO.create("Bad D", 2, Double.NaN));

        signIn("reception");
        int patientA = createPatientAs("reception", "Room Patient A");
        int patientB = createPatientAs("reception", "Room Patient B");
        int visitA = AppointmentDAO.startWalkIn(patientA, 3, null);
        int visitB = AppointmentDAO.startWalkIn(patientB, 3, null);
        expectIllegal("a room that does not exist is refused", () -> AppointmentDAO.admit(visitA, "Ward Z"));
        AppointmentDAO.admit(visitA, "  tiny ROOM ");
        check("the room name is saved as the official name", "Tiny Room".equals(findVisit(patientA, visitA).getRoom()));
        expectIllegal("a full room cannot take another patient", () -> AppointmentDAO.admit(visitB, "Tiny Room"));
        check("a room without a free bed is not offered", !RoomDAO.freeRoomNames().contains("Tiny Room")
                && RoomDAO.freeRoomNames().contains("Ward A"));
        check("the room is shown as occupied", AppointmentDAO.findOccupiedRooms().stream().anyMatch(r -> r.startsWith("Tiny Room (Room Patient A")));
        AppointmentDAO.admit(visitB, "Ward A");
        check("a room with several beds takes several patients", RoomDAO.occupiedBeds().get("Ward A") == 2);

        signIn("owner");
        expectIllegal("a bed with a patient cannot be taken away", () -> RoomDAO.update(tiny.getItemId(), "Tiny Room", 0, 25));
        expectIllegal("a room with history cannot be renamed", () -> RoomDAO.update(tiny.getItemId(), "Renamed", 1, 25));
        RoomDAO.update(tiny.getItemId(), "Tiny Room", 3, 35);
        check("beds and price can be changed", findRoom("Tiny Room").getStockQuantity() == 3 && findRoom("Tiny Room").getUnitPrice() == 35.00);
        expectIllegal("a room with history cannot be removed", () -> RoomDAO.delete(tiny.getItemId()));

        signIn("reception");
        AppointmentDAO.discharge(visitA);
        AppointmentDAO.discharge(visitB);
        check("after the discharge the beds are free again",
                RoomDAO.occupiedBeds().getOrDefault("Tiny Room", 0) == 0 && RoomDAO.occupiedBeds().get("Ward A") == 1);

        signIn("owner");
        RoomDAO.create("Never Used", 2, 10);
        RoomDAO.delete(findRoom("Never Used").getItemId());
        check("a room that was never used can be removed", RoomDAO.findAll().stream().noneMatch(r -> r.getItemName().equals("Never Used")));
        RoomDAO.create("Closed Room", 0, 10);
        check("a room with 0 beds is closed: it is not offered", !RoomDAO.freeRoomNames().contains("Closed Room"));
        check("a room is never a low-stock item",
                InventoryDAO.findLowStock().stream().noneMatch(i -> RoomDAO.CATEGORY.equals(i.getCategory()))
                        && ReportDAO.loadStats().getLowStockItems()
                        == count("SELECT COUNT(*) FROM inventory WHERE category <> 'Room' AND stock_quantity <= reorder_level"));
    }

    private static void walkInAndConsultation() throws Exception {
        section("walk-in and consultation");
        signIn("reception");
        int patient = createPatientAs("reception", "Walkin Patient");
        int visit = AppointmentDAO.startWalkIn(patient, 3, null);
        Appointment started = findVisit(patient, visit);
        check("a walk-in visit starts now, Scheduled, with the doctor",
                "Scheduled".equals(started.getStatus()) && started.getDoctorId() == 3
                        && started.getAppointmentDate().equals(LocalDate.now()) && "Walk-in".equals(started.getReason()));
        check("the same patient and doctor on the same day use the same visit", AppointmentDAO.startWalkIn(patient, 3, "again") == visit);

        signIn("nurse.amy");
        refused("a nurse cannot start a visit", () -> AppointmentDAO.startWalkIn(patient, 3, null));

        signIn("dr.alice");
        check("the doctor sees the patient in 'my patients'",
                AppointmentDAO.findOpenForDoctor(3).stream().anyMatch(a -> a.getAppointmentId() == visit));
        check("the doctor does not see the patients of another doctor",
                AppointmentDAO.findOpenForDoctor(3).stream().allMatch(a -> a.getDoctorId() == 3));
        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patient);
        record.setDoctorId(3);
        record.setAppointmentId(visit);
        record.setDiagnosis("Common cold");
        record.setPrescription("Paracetamol");
        AppointmentDAO.updateStatus(visit, "Completed");
        MedicalRecordDAO.create(record);
        check("after 'save and complete' the patient leaves the doctor's list",
                AppointmentDAO.findOpenForDoctor(3).stream().noneMatch(a -> a.getAppointmentId() == visit));
        check("the record is in the history of the patient", MedicalRecordDAO.findByPatient(patient).size() == 1
                && "Common cold".equals(MedicalRecordDAO.findByPatient(patient).get(0).getDiagnosis()));
        check("and the visit can be paid now", AppointmentDAO.findToPay().stream().anyMatch(a -> a.getAppointmentId() == visit));

        signIn("reception");
        int early = createPatientAs("reception", "Early Patient");
        Appointment tomorrow = newAppointment(early, 3);
        tomorrow.setAppointmentDate(LocalDate.now().plusDays(1));
        tomorrow.setAppointmentTime(LocalTime.of(7, 5));
        int earlyVisit = AppointmentDAO.create(tomorrow);
        signIn("dr.alice");
        check("a doctor sees a patient booked for a later day (the patient may come earlier)",
                AppointmentDAO.findOpenForDoctor(3).stream().anyMatch(a -> a.getAppointmentId() == earlyVisit));
        AppointmentDAO.updateStatus(earlyVisit, "Completed");

        signIn("reception");
        expectIllegal("an admitted patient cannot start another visit (demo patient 6 is in Ward A)",
                () -> AppointmentDAO.startWalkIn(6, 3, null));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 0 WHERE user_id = 4");
        }
        expectIllegal("an inactive doctor cannot be chosen for a walk-in", () -> AppointmentDAO.startWalkIn(patient, 4, null));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 1 WHERE user_id = 4");
        }

        int adminPatient = createPatientAs("reception", "Admin Seen Patient");
        signIn("admin");
        int adminVisit = AppointmentDAO.startWalkIn(adminPatient, 2, null);
        record.setPatientId(adminPatient);
        record.setDoctorId(2);
        record.setAppointmentId(adminVisit);
        AppointmentDAO.updateStatus(adminVisit, "Completed");
        MedicalRecordDAO.create(record);
        check("the department admin can see a patient like a doctor",
                count("SELECT COUNT(*) FROM medical_records WHERE doctor_id = 2 AND appointment_id = " + adminVisit) == 1);

        signIn("nurse.amy");
        check("no vital signs for a patient who was never measured", VitalDAO.findLatest(patient) == null);
        VitalDAO.create(newVital(patient, 5));
        check("a nurse measures a patient without an appointment", VitalDAO.findLatest(patient) != null
                && "120/80".equals(VitalDAO.findLatest(patient).getBloodPressure()));
        check("the latest vital signs of a demo patient are found", VitalDAO.findLatest(1) != null);
    }

    private static void admitAndDischarge() throws Exception {
        section("admit and discharge");
        signIn("reception");
        PatientDAO.create(newPatient("Inpatient Test"));
        int patientId = latestPatientId();
        int visit = AppointmentDAO.create(newAppointment(patientId, 4));

        check("the demo data has one admitted patient", ReportDAO.loadStats().getAdmittedPatients() == 1);
        final int visitId = visit;
        expectIllegal("a room is needed to admit", () -> {
            AppointmentDAO.admit(visitId, "");
        });
        AppointmentDAO.admit(visit, "Ward B");
        Appointment admitted = findVisit(patientId, visit);
        check("the appointment time 09:00 is read back as 09:00", admitted.getAppointmentTime().equals(LocalTime.of(9, 0)));
        check("admitted: status Admitted, Inpatient, room, time",
                "Admitted".equals(admitted.getStatus()) && admitted.isInpatient()
                        && "Ward B".equals(admitted.getRoom()) && admitted.getAdmittedAt() != null);
        check("the dashboard counts it", ReportDAO.loadStats().getAdmittedPatients() == 2);
        expectIllegal("an admitted visit cannot be changed with Update status", () -> AppointmentDAO.updateStatus(visitId, "Completed"));
        expectIllegal("an admitted visit cannot be admitted again", () -> AppointmentDAO.admit(visitId, "Ward A"));

        AppointmentDAO.discharge(visit);
        Appointment discharged = findVisit(patientId, visit);
        check("discharged: status Discharged and the time is recorded",
                "Discharged".equals(discharged.getStatus()) && discharged.getDischargedAt() != null);
        check("the stay is at least one day", discharged.getStayDays() >= 1);
        check("the dashboard count goes down again", ReportDAO.loadStats().getAdmittedPatients() == 1);
        expectIllegal("a patient cannot be discharged twice", () -> AppointmentDAO.discharge(visitId));

        signIn("nurse.amy");
        refused("a nurse cannot admit", () -> AppointmentDAO.admit(visitId, "Ward A"));
    }

    private static void checkout() throws Exception {
        section("checkout");
        signIn("reception");
        int patientId = latestPatientId();
        Appointment visit = AppointmentDAO.findByPatient(patientId).get(0);
        List<Billing> lines = BillingDAO.preview(visit.getAppointmentId(), List.of());
        check("the bill of the visit has the doctor's fee and the room",
                lines.size() == 2 && "Service".equals(lines.get(0).getBillType()) && lines.get(0).getTotalAmount() == 80.00
                        && "Room".equals(lines.get(1).getBillType()) && lines.get(1).getTotalAmount() == 30.00);
        check("the room line shows the days and the price per day", lines.get(1).getDescription().contains("Ward B: 1 day x $30.00"));
        check("the preview saves nothing", count("SELECT COUNT(*) FROM billings WHERE appointment_id = " + visit.getAppointmentId()) == 0);
        check("the visit is waiting to be paid", AppointmentDAO.findToPay().stream().anyMatch(a -> a.getAppointmentId() == visit.getAppointmentId()));

        int waiting = AppointmentDAO.startWalkIn(createPatientAs("reception", "Not Seen Yet"), 3, null);
        expectIllegal("a visit the doctor has not completed cannot be paid", () -> BillingDAO.preview(waiting, List.of()));
        expectIllegal("a visit that does not exist cannot be paid", () -> BillingDAO.preview(999999, List.of()));
        AppointmentDAO.updateStatus(waiting, "Cancelled");
        expectIllegal("a cancelled visit cannot be paid", () -> BillingDAO.checkout(waiting, "Cash", List.of(), 6));

        long before = count("SELECT COUNT(*) FROM billings");
        expectIllegal("a payment method from the list is needed", () -> BillingDAO.checkout(visit.getAppointmentId(), "Bitcoin", List.of(), 6));
        expectIllegal("a payment method is needed", () -> BillingDAO.checkout(visit.getAppointmentId(), null, List.of(), 6));
        check("nothing was saved by the refused payments", count("SELECT COUNT(*) FROM billings") == before);

        int firstId = BillingDAO.checkout(visit.getAppointmentId(), "Cash", List.of(), 6);
        List<Billing> saved = BillingDAO.findByVisit(visit.getAppointmentId());
        check("the payment saves one line for each part, all Paid with the method",
                saved.size() == 2 && saved.get(0).getBillingId() == firstId
                        && saved.stream().allMatch(b -> "Paid".equals(b.getPaymentStatus()) && "Cash".equals(b.getPaymentMethod())));
        check("the lines add up to 110.00", saved.stream().mapToDouble(Billing::getTotalAmount).sum() == 110.00);
        check("the lines know who took the payment", saved.stream().allMatch(b -> b.getBilledByName() != null));
        expectIllegal("a visit cannot be paid twice", () -> BillingDAO.checkout(visit.getAppointmentId(), "Card", List.of(), 6));
        check("the second payment saved nothing", count("SELECT COUNT(*) FROM billings WHERE appointment_id = " + visit.getAppointmentId()) == 2);
        check("a paid visit is no longer waiting", AppointmentDAO.findToPay().stream().noneMatch(a -> a.getAppointmentId() == visit.getAppointmentId()));

        int stayPatient = createPatientAs("reception", "Admitted Payer");
        int stay = AppointmentDAO.startWalkIn(stayPatient, 4, null);
        AppointmentDAO.admit(stay, "Ward B");
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE appointments SET admitted_at = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE appointment_id = " + stay);
        }
        check("an admitted patient can be paid before leaving", AppointmentDAO.findToPay().stream().anyMatch(a -> a.getAppointmentId() == stay));
        List<Billing> stayLines = BillingDAO.preview(stay, List.of());
        check("3 days in Ward B at 30 = 90 and the doctor's 80",
                stayLines.get(1).getTotalAmount() == 90.00 && stayLines.get(1).getDescription().contains("3 days x $30.00"));
        BillingDAO.checkout(stay, "Card", List.of(), 6);
        Appointment left = findVisit(stayPatient, stay);
        check("the payment discharged the patient", "Discharged".equals(left.getStatus()) && left.getDischargedAt() != null);
        check("the bed is free again", RoomDAO.occupiedBeds().getOrDefault("Ward B", 0) == 0);
        check("the whole stay was paid in one step: 80 + 90",
                BillingDAO.findByVisit(stay).stream().mapToDouble(Billing::getTotalAmount).sum() == 170.00);

        int freePatient = createPatientAs("reception", "Free Visit");
        signIn("admin");
        int freeVisit = AppointmentDAO.startWalkIn(freePatient, 2, null);
        AppointmentDAO.updateStatus(freeVisit, "Completed");
        signIn("reception");
        expectIllegal("a visit with nothing to charge cannot be paid", () -> BillingDAO.checkout(freeVisit, "Cash", List.of(), 6));
        int gone = createPatientAs("reception", "Gone Home");
        check("a patient without a visit is not in the hospital", inHospital(gone) == false);
        int goneVisit = AppointmentDAO.startWalkIn(gone, 3, null);
        check("a walk-in patient is in the hospital", inHospital(gone));
        AppointmentDAO.updateStatus(goneVisit, "Completed");
        check("a patient who is not paid yet is still in the hospital", inHospital(gone));
        BillingDAO.checkout(goneVisit, "Cash", List.of(medicine(6, 1)), 6);
        check("after the payment the patient is gone from the hospital list", !inHospital(gone));
        check("but the patient stays in the patient register", PatientDAO.findAll("Gone Home").size() == 1);
        Appointment later = newAppointment(gone, 3);
        later.setAppointmentDate(LocalDate.now().plusDays(2));
        later.setAppointmentTime(LocalTime.of(8, 0));
        int laterVisit = AppointmentDAO.create(later);
        check("a patient booked for a later day is not in the hospital yet", !inHospital(gone));
        AppointmentDAO.updateStatus(laterVisit, "Cancelled");
        int again = AppointmentDAO.startWalkIn(gone, 3, null);
        check("when the patient comes back the patient is in the hospital again", inHospital(gone));
        AppointmentDAO.updateStatus(again, "Cancelled");
        check("a cancelled visit means not in the hospital", !inHospital(gone));
    }

    private static void medicineRules() throws Exception {
        section("medicine on the bill");
        signIn("reception");
        int patient = createPatientAs("reception", "Medicine Buyer");
        int visit1 = completedVisit(patient, 3);
        check("Paracetamol starts with 450 in stock", stock(1) == 450);

        List<Billing> lines = BillingDAO.preview(visit1, List.of(medicine(1, 10)));
        check("the bill has the doctor's fee and the medicine: 50.00 + 2.00",
                lines.size() == 2 && lines.get(1).getTotalAmount() == 2.00 && "Medicine".equals(lines.get(1).getBillType())
                        && lines.get(1).getDescription().equals("Paracetamol 500 mg x 10"));
        check("the preview does not touch the stock", stock(1) == 450);

        BillingDAO.checkout(visit1, "Cash", List.of(medicine(1, 10)), 6);
        check("paying takes 10 out of the stock", stock(1) == 440);
        Billing line = BillingDAO.findByVisit(visit1).get(1);
        check("the bill line remembers the item and the quantity", line.getInventoryId() == 1 && line.getQuantity() == 10);

        int visit2 = completedVisit(patient, 3);
        long bills = count("SELECT COUNT(*) FROM billings");
        expectIllegal("selling more than the stock is refused", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(1, 999999)), 6));
        expectIllegal("a quantity of 0 is refused", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(1, 0)), 6));
        expectIllegal("a negative quantity is refused", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(1, -2)), 6));
        expectIllegal("a medicine without a quantity is refused", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(1, null)), 6));
        expectIllegal("an item that does not exist is refused", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(99999, 1)), 6));
        int roomItem = RoomDAO.findAll().get(0).getItemId();
        expectIllegal("a room cannot be sold as a medicine", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(roomItem, 1)), 6));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE inventory SET expiry_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY) WHERE item_id = 10");
        }
        expectIllegal("an expired medicine cannot be sold", () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(10, 1)), 6));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE inventory SET expiry_date = DATE_ADD(CURDATE(), INTERVAL 2 MONTH) WHERE item_id = 10");
        }
        check("nothing was saved and the stock did not change", count("SELECT COUNT(*) FROM billings") == bills
                && stock(1) == 440 && stock(10) == 8);

        expectIllegal("together more than the stock is refused (all or nothing)",
                () -> BillingDAO.checkout(visit2, "Cash", List.of(medicine(1, 300), medicine(1, 300)), 6));
        check("the stock and the bills are unchanged after the rollback",
                stock(1) == 440 && count("SELECT COUNT(*) FROM billings") == bills
                        && "Completed".equals(findVisit(patient, visit2).getStatus()));
        BillingDAO.checkout(visit2, "Card", List.of(medicine(1, 40), medicine(2, 20)), 6);
        check("two different medicines in one payment", stock(1) == 400 && stock(2) == 100
                && BillingDAO.findByVisit(visit2).size() == 3);
    }

    private static void receipt() throws Exception {
        section("receipt");
        signIn("reception");
        int patient = createPatientAs("reception", "Receipt Test");
        int visit = completedVisit(patient, 3);
        BillingDAO.checkout(visit, "Card", List.of(medicine(1, 3), medicine(2, 4)), 6);
        List<Billing> saved = BillingDAO.findByVisit(visit);
        String text = Receipt.text(saved);
        System.out.println(text.lines().map(line -> "          | " + line).collect(java.util.stream.Collectors.joining("\n")));
        check("the receipt shows the receipt number", text.contains(String.format("R-%06d", saved.get(0).getBillingId())));
        check("the receipt shows the patient", text.contains("Receipt Test") && text.contains(String.format("P-%04d", patient)));
        check("the receipt shows every line", text.contains("Consultation - Dr. Alice Smith") && text.contains("Paracetamol 500 mg x 3")
                && text.contains("Amoxicillin 250 mg x 4"));
        check("the receipt shows the total 52.00, the status and the method",
                text.contains("$52.00") && text.contains("PAID") && text.contains("Card"));
        check("the receipt shows who served the patient", text.contains(saved.get(0).getBilledByName()) && text.contains("Billed by"));
        check("every line of the receipt fits the width", text.lines().allMatch(line -> line.length() <= 44));
        long minutesOff = Math.abs(java.time.Duration.between(saved.get(0).getCreatedAt(), java.time.LocalDateTime.now()).toMinutes());
        check("the bill time shown is the real local time (off by " + minutesOff + " min)", minutesOff <= 5);

        check("the receipt of one line is the receipt of the whole visit", BillingDAO.receiptLines(saved.get(1)).size() == 3);
        Billing old = BillingDAO.findById(1);
        check("an old bill without a visit has a receipt of its own line", BillingDAO.receiptLines(old).size() == 1);

        Billing longLine = new Billing(99, 1, "Long Description", "Check-up with a very long description that must be wrapped over several lines of the receipt",
                12.50, "Pending", null, java.time.LocalDateTime.now());
        String wrapped = Receipt.text(longLine);
        check("a long description is wrapped inside the receipt width", wrapped.lines().allMatch(line -> line.length() <= 44));
        check("a pending bill is not shown as paid", wrapped.contains("NOT PAID YET"));
        check("an unknown bill id gives nothing", BillingDAO.findById(999999) == null);
    }

    private static void moneyRules() throws Exception {
        section("money rules");
        signIn("reception");

        Billing pending = BillingDAO.findById(7);
        check("the demo data has pending bills", pending != null && "Pending".equals(pending.getPaymentStatus()));
        expectIllegal("a payment method from the list is needed to mark a bill as paid", () -> BillingDAO.markPaid(7, "Bitcoin"));
        expectIllegal("a payment method is needed to mark a bill as paid", () -> BillingDAO.markPaid(7, ""));
        BillingDAO.markPaid(7, "Cash");
        check("the payment is recorded", "Paid".equals(BillingDAO.findById(7).getPaymentStatus())
                && "Cash".equals(BillingDAO.findById(7).getPaymentMethod()));
        expectIllegal("a bill that is already paid cannot be paid again", () -> BillingDAO.markPaid(7, "Card"));
        check("the paid bill is still there and there is no way to delete a bill", count("SELECT COUNT(*) FROM billings WHERE billing_id = 7") == 1);
        signIn("owner");
        refused("the owner cannot mark a bill as paid", () -> BillingDAO.markPaid(8, "Cash"));
        check("the owner sees all the bill lines", BillingDAO.findAll("").size() > 5);

        expectIllegal("text that looks like a number is refused: NaN", () -> Validators.requireMoney("NaN", "Amount"));
        expectIllegal("text that looks like a number is refused: Infinity", () -> Validators.requireMoney("Infinity", "Amount"));
        expectIllegal("text that looks like a number is refused: 1e9", () -> Validators.requireMoney("1e9", "Amount"));
        expectIllegal("three decimals are refused", () -> Validators.requireMoney("1.234", "Amount"));
        expectIllegal("a negative amount is refused", () -> Validators.requireMoney("-5", "Amount"));
        expectIllegal("an amount above the limit is refused", () -> Validators.requireMoney("100000000", "Amount"));
        check("80 and 12.50 are accepted", Validators.requireMoney("80", "Amount") == 80 && Validators.requireMoney("12.50", "Amount") == 12.5);

        check("every saved amount has at most 2 decimals",
                count("SELECT COUNT(*) FROM billings WHERE total_amount <> ROUND(total_amount, 2)") == 0);

        Map<String, Double> before = ReportDAO.monthlyRevenue();
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("INSERT INTO billings (patient_id, description, total_amount, payment_status, payment_method, created_at) "
                    + "VALUES (1, 'first day of the oldest month', 7.00, 'Paid', 'Cash', "
                    + "DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 5 MONTH))");
        }
        double totalBefore = before.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalAfter = ReportDAO.monthlyRevenue().values().stream().mapToDouble(Double::doubleValue).sum();
        check("a bill from the first day of the oldest month is in the revenue chart", Math.abs(totalAfter - totalBefore - 7.00) < 0.001);
        check("revenue on the dashboard = the sum of the paid bills",
                Math.abs(ReportDAO.loadStats().getRevenue() - decimal("SELECT SUM(total_amount) FROM billings WHERE payment_status = 'Paid'")) < 0.001);
    }

    private static void restock() throws Exception {
        section("restock and low stock");
        signIn("inventory");
        check("the blood pressure monitor is low in stock (4 of reorder level 6)",
                InventoryDAO.findLowStock().stream().anyMatch(i -> i.getItemId() == 8));
        InventoryDAO.restock(8, 20, 30.00);
        check("a delivery of 20 is added to the stock", stock(8) == 24);
        check("the cost of the delivery is saved", decimal("SELECT unit_cost FROM inventory WHERE item_id = 8") == 30.00);
        InventoryDAO.restock(8, 5, null);
        check("without a new cost the old cost stays", stock(8) == 29 && decimal("SELECT unit_cost FROM inventory WHERE item_id = 8") == 30.00);
        check("a restocked item is not low any more", InventoryDAO.findLowStock().stream().noneMatch(i -> i.getItemId() == 8));
        expectIllegal("a delivery of 0 is refused", () -> InventoryDAO.restock(8, 0, null));
        expectIllegal("a negative delivery is refused", () -> InventoryDAO.restock(8, -3, null));
        expectIllegal("a huge delivery is refused", () -> InventoryDAO.restock(8, 2_000_000, null));
        expectIllegal("a negative cost is refused", () -> InventoryDAO.restock(8, 1, -1.0));
        expectIllegal("an unknown item cannot be restocked", () -> InventoryDAO.restock(99999, 1, null));
        int roomItem = RoomDAO.findAll().get(0).getItemId();
        expectIllegal("a room cannot be restocked", () -> InventoryDAO.restock(roomItem, 1, null));
        check("nothing changed after the refusals", stock(8) == 29);

        InventoryItem medicine = new InventoryItem();
        medicine.setItemName("Test Syrup");
        medicine.setCategory("Medicine");
        medicine.setUnitPrice(5);
        medicine.setUnitCost(3);
        medicine.setStockQuantity(10);
        medicine.setReorderLevel(5);
        InventoryDAO.create(medicine);
        check("inventory staff add a new medicine with its selling price and cost",
                decimal("SELECT unit_price FROM inventory WHERE item_name = 'Test Syrup'") == 5.00
                        && decimal("SELECT unit_cost FROM inventory WHERE item_name = 'Test Syrup'") == 3.00);
        int syrup = (int) count("SELECT item_id FROM inventory WHERE item_name = 'Test Syrup'");
        medicine.setItemId(syrup);
        medicine.setUnitPrice(6.50);
        InventoryDAO.update(medicine);
        check("the selling price can be changed", decimal("SELECT unit_price FROM inventory WHERE item_id = " + syrup) == 6.50);
        expectIllegal("the new price is used at the checkout: a medicine without a price cannot be sold",
                () -> { medicine.setUnitPrice(0); InventoryDAO.update(medicine); BillingDAO.preview(1, List.of(medicine(syrup, 1))); });
        signIn("reception");
        refused("reception cannot add a medicine", () -> InventoryDAO.create(medicine));
        refused("reception cannot restock", () -> InventoryDAO.restock(8, 1, null));
        signIn("owner");
        InventoryItem asRoom = new InventoryItem();
        asRoom.setItemName("Fake Room");
        asRoom.setCategory(" room ");
        asRoom.setUnitPrice(5);
        expectIllegal("a medicine cannot be created in the category Room", () -> InventoryDAO.create(asRoom));
        InventoryItem hijack = new InventoryItem();
        hijack.setItemId(roomItem);
        hijack.setItemName("Hijacked");
        hijack.setCategory("Medicine");
        hijack.setUnitPrice(1);
        InventoryDAO.update(hijack);
        InventoryDAO.delete(roomItem);
        check("the inventory screen cannot change or delete a room",
                count("SELECT COUNT(*) FROM inventory WHERE item_id = " + roomItem + " AND category = 'Room' AND item_name <> 'Hijacked'") == 1);
    }

    private static void financeReport() throws Exception {
        section("finance report");
        signIn("owner");
        List<ReportDAO.MonthFinance> months = ReportDAO.monthlyFinance();
        ReportDAO.MonthFinance now = months.get(months.size() - 1);
        check("the report has six months", months.size() == 6);
        check("income = the paid bills of this month", Math.abs(now.income() - decimal(
                "SELECT COALESCE(SUM(total_amount), 0) FROM billings WHERE payment_status = 'Paid' "
                        + "AND created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")) < 0.001);
        check("medicine cost = quantity x the unit cost of the medicines sold this month", Math.abs(now.medicineCost() - decimal(
                "SELECT COALESCE(SUM(b.quantity * i.unit_cost), 0) FROM billings b JOIN inventory i ON i.item_id = b.inventory_id "
                        + "WHERE b.payment_status = 'Paid' AND b.created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")) < 0.011);
        check("the medicine cost is not zero (medicines were sold)", now.medicineCost() > 0);
        check("salaries = the monthly salaries of the active staff",
                Math.abs(now.salary() - decimal("SELECT SUM(monthly_salary) FROM users WHERE active = 1")) < 0.001 && now.salary() > 0);
        check("expense = salaries + medicine cost, profit = income - expense",
                Math.abs(now.expense() - now.salary() - now.medicineCost()) < 0.001
                        && Math.abs(now.profit() - (now.income() - now.expense())) < 0.001);

        List<ReportDAO.MedicineUsage> usage = ReportDAO.medicineUsage(true);
        ReportDAO.MedicineUsage paracetamol = usage.stream().filter(u -> u.name().startsWith("Paracetamol")).findFirst().orElseThrow();
        check("the usage report counts the medicines used this month: 10 + 40 + 3 Paracetamol", paracetamol.quantity() == 53);
        check("its sales and cost are right: 53 x 0.20 and 53 x 0.12",
                Math.abs(paracetamol.sales() - 10.60) < 0.001 && Math.abs(paracetamol.cost() - 6.36) < 0.001);
        check("the most used medicine comes first", usage.get(0).quantity() >= usage.get(usage.size() - 1).quantity());
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("INSERT INTO billings (patient_id, description, bill_type, total_amount, payment_status, payment_method, "
                    + "inventory_id, quantity, created_at) VALUES (1, 'old medicine', 'Medicine', 1.75, 'Paid', 'Cash', 2, 5, "
                    + "DATE_SUB(CURDATE(), INTERVAL 3 MONTH))");
        }
        int thisMonth = ReportDAO.medicineUsage(true).stream().filter(u -> u.name().startsWith("Amoxicillin")).mapToInt(ReportDAO.MedicineUsage::quantity).sum();
        int allTime = ReportDAO.medicineUsage(false).stream().filter(u -> u.name().startsWith("Amoxicillin")).mapToInt(ReportDAO.MedicineUsage::quantity).sum();
        check("an old sale is only in the all-time usage", allTime == thisMonth + 5);

        signIn("reception");
        check("the old sale is in the history of the months before",
                ReportDAO.monthlyFinance().stream().mapToDouble(ReportDAO.MonthFinance::medicineCost).sum() > now.medicineCost());
    }

    private static void identityAndEmail() throws Exception {
        section("identity and e-mail");
        signIn("owner");
        User draft = new User();
        draft.setUsername("id.nurse");
        draft.setFullName("Id Nurse");
        draft.setRole("NURSE");
        draft.setDepartmentId(1);
        draft.setDateOfBirth(LocalDate.of(1995, 3, 4));
        draft.setGender("Female");
        draft.setNrc("  12/LAMANA(N)900001 ");
        draft.setEmail(" Id.Nurse@Example.com ");
        int id = UserDAO.create(draft, "secret1");
        User saved = UserDAO.findAll("id.nurse").get(0);
        check("a staff member has date of birth and gender", LocalDate.of(1995, 3, 4).equals(saved.getDateOfBirth())
                && "Female".equals(saved.getGender()));
        check("the NRC is cleaned up and the e-mail is kept in lower case",
                "12/LAMANA(N)900001".equals(saved.getNrc()) && "id.nurse@example.com".equals(saved.getEmail()));

        User sameNrc = new User();
        sameNrc.setUsername("id.two");
        sameNrc.setFullName("Id Two");
        sameNrc.setRole("NURSE");
        sameNrc.setDepartmentId(1);
        sameNrc.setNrc("12/lamana(n) 900001");
        expectIllegal("two staff members cannot have the same NRC (spaces and case do not matter)", () -> UserDAO.create(sameNrc, "secret1"));
        User sameEmail = new User();
        sameEmail.setUsername("id.three");
        sameEmail.setFullName("Id Three");
        sameEmail.setRole("NURSE");
        sameEmail.setDepartmentId(1);
        sameEmail.setEmail("ID.NURSE@example.com");
        expectIllegal("two staff members cannot have the same e-mail", () -> UserDAO.create(sameEmail, "secret1"));
        for (String bad : new String[]{"abc", "a@b", "a b@c.com", "@x.com", "x".repeat(101) + "@a.com"}) {
            User badMail = new User();
            badMail.setUsername("id.bad");
            badMail.setFullName("Bad Mail");
            badMail.setRole("NURSE");
            badMail.setDepartmentId(1);
            badMail.setEmail(bad);
            expectIllegal("the e-mail '" + (bad.length() > 12 ? "xxx...@a.com" : bad) + "' is refused", () -> UserDAO.create(badMail, "secret1"));
        }
        User badGender = new User();
        badGender.setUsername("id.g");
        badGender.setFullName("Bad Gender");
        badGender.setRole("NURSE");
        badGender.setDepartmentId(1);
        badGender.setGender("Robot");
        expectIllegal("a gender other than Male, Female, Other is refused", () -> UserDAO.create(badGender, "secret1"));
        User future = new User();
        future.setUsername("id.f");
        future.setFullName("Future Staff");
        future.setRole("NURSE");
        future.setDepartmentId(1);
        future.setDateOfBirth(LocalDate.now().plusDays(1));
        expectIllegal("a staff date of birth in the future is refused", () -> UserDAO.create(future, "secret1"));
        check("nothing was saved by the refused accounts", count("SELECT COUNT(*) FROM users WHERE username LIKE 'id.%'") == 1);

        User dismissed = saved.copy();
        dismissed.setActive(false);
        UserDAO.update(dismissed);
        User after = UserDAO.findAll("id.nurse").get(0);
        check("dismissing keeps the identity of the staff member",
                !after.isActive() && "12/LAMANA(N)900001".equals(after.getNrc()) && "id.nurse@example.com".equals(after.getEmail())
                        && LocalDate.of(1995, 3, 4).equals(after.getDateOfBirth()));
        after.setEmail(null);
        after.setNrc(null);
        UserDAO.update(after);
        check("the e-mail and the NRC can be removed again",
                UserDAO.findAll("id.nurse").get(0).getEmail() == null && UserDAO.findAll("id.nurse").get(0).getNrc() == null);
        UserDAO.delete(id);

        signIn("owner");
        check("the history of a patient: vital signs, newest first", VitalDAO.findByPatient(1).size() >= 1
                && VitalDAO.findByPatient(1).stream().allMatch(v -> v.getPatientId() == 1));
        check("the patients in the hospital now are listed with the doctor and the status",
                AppointmentDAO.findInHospital().stream().anyMatch(h -> h.patient().equals("Nandar Win") && "Admitted".equals(h.status())
                        && "Ward A".equals(h.room()) && h.doctor() != null));
        check("the in-hospital list holds the same patients as the drop-downs",
                AppointmentDAO.findInHospital().stream().map(h -> h.patientId()).distinct().count() == PatientDAO.findInHospital().size());

        signIn("reception");
        Patient family = newPatient("Email Patient");
        family.setEmail(" Family@Mail.com ");
        PatientDAO.create(family);
        int patientId = latestPatientId();
        check("a patient e-mail is saved in lower case", "family@mail.com".equals(PatientDAO.findById(patientId).getEmail()));
        check("a patient is found by e-mail in the list", PatientDAO.findAll("family@mail").size() == 1);
        Patient badEmail = newPatient("Bad Email Patient");
        badEmail.setEmail("not-an-email");
        expectIllegal("a wrong patient e-mail is refused", () -> PatientDAO.create(badEmail));
        Patient sharedEmail = newPatient("Email Sibling");
        sharedEmail.setEmail("family@mail.com");
        PatientDAO.create(sharedEmail);
        check("a family may share one e-mail address", PatientDAO.findAll("family@mail").size() == 2);
        Patient noEmail = newPatient("No Email Patient");
        PatientDAO.create(noEmail);
        check("the e-mail of a patient is optional", PatientDAO.findById(latestPatientId()).getEmail() == null);
    }

    private static void forgotPasswordOtp() throws Exception {
        section("forgot password (OTP)");
        List<String[]> outbox = new ArrayList<>();
        Mailer.setSender((to, subject, body) -> outbox.add(new String[]{to, subject, body}));
        java.time.Instant start = java.time.Instant.now();
        final java.time.Instant[] now = {start};
        OtpService.setClock(new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneId.systemDefault();
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public java.time.Instant instant() {
                return now[0];
            }
        });
        try {
            signIn("owner");
            User draft = new User();
            draft.setUsername("otp.nurse");
            draft.setFullName("Otp Nurse");
            draft.setRole("NURSE");
            draft.setDepartmentId(1);
            draft.setEmail("otp.nurse@example.com");
            int id = UserDAO.create(draft, "oldpass1");
            UserDAO.create("no.mail", "oldpass1", "No Mail", "NURSE", "", 1, 0);

            OtpService.request("nobody.here@example.com");
            check("no e-mail is sent for an address that belongs to no account", outbox.isEmpty());
            expectIllegal("something that is not an e-mail address is refused", () -> OtpService.request("otp.nurse"));
            expectIllegal("an empty address is refused", () -> OtpService.request("  "));
            expectIllegal("a code cannot be used when none was asked for", () -> OtpService.confirm("otp.nurse@example.com", "123456", "newpass1"));

            OtpService.request("  Otp.Nurse@Example.com ");
            check("a code is e-mailed to the address of the account", outbox.size() == 1 && "otp.nurse@example.com".equals(outbox.get(0)[0]));
            String code = outbox.get(0)[2].replaceAll("(?s).*code is: (\\d{6}).*", "$1");
            check("the code has 6 digits and the e-mail says how long it is valid", code.matches("\\d{6}") && outbox.get(0)[2].contains("5 minutes"));
            expectIllegal("a new code cannot be asked for at once", () -> OtpService.request("otp.nurse@example.com"));

            expectIllegal("a weak new password is refused", () -> OtpService.confirm("otp.nurse@example.com", code, "123"));
            String wrong = code.equals("000000") ? "111111" : "000000";
            expectIllegal("a wrong code is refused (2 tries left)", () -> OtpService.confirm("otp.nurse@example.com", wrong, "newpass1"));
            check("the old password still works after a wrong code", UserDAO.authenticate("otp.nurse", "oldpass1") != null);
            expectIllegal("a wrong code is refused (1 try left)", () -> OtpService.confirm("otp.nurse@example.com", wrong, "newpass1"));
            expectIllegal("the third wrong code cancels the code", () -> OtpService.confirm("otp.nurse@example.com", wrong, "newpass1"));
            expectIllegal("after that even the right code does not work", () -> OtpService.confirm("otp.nurse@example.com", code, "newpass1"));
            check("the password was not changed by the wrong codes", UserDAO.authenticate("otp.nurse", "oldpass1") != null);

            now[0] = now[0].plusSeconds(61);
            OtpService.request("otp.nurse@example.com");
            check("after a minute a new code can be asked for", outbox.size() == 2);
            String second = outbox.get(1)[2].replaceAll("(?s).*code is: (\\d{6}).*", "$1");
            OtpService.confirm("otp.nurse@example.com", " " + second + " ", "newpass1");
            check("the right code changes the password", UserDAO.authenticate("otp.nurse", "newpass1") != null
                    && UserDAO.authenticate("otp.nurse", "oldpass1") == null);
            expectIllegal("a code works only once", () -> OtpService.confirm("otp.nurse@example.com", second, "another1"));

            now[0] = now[0].plusSeconds(120);
            OtpService.request("otp.nurse@example.com");
            String third = outbox.get(2)[2].replaceAll("(?s).*code is: (\\d{6}).*", "$1");
            now[0] = now[0].plusSeconds(5 * 60 + 1);
            expectIllegal("a code expires after 5 minutes", () -> OtpService.confirm("otp.nurse@example.com", third, "newpass2"));
            check("the password stayed as it was", UserDAO.authenticate("otp.nurse", "newpass1") != null);

            now[0] = now[0].plusSeconds(120);
            Mailer.setSender((to, subject, body) -> {
                throw new java.io.IOException("no internet");
            });
            expectIllegal("a failed e-mail gives a clear message", () -> {
                try {
                    OtpService.request("otp.nurse@example.com");
                } catch (IllegalStateException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            });
            Mailer.setSender((to, subject, body) -> outbox.add(new String[]{to, subject, body}));
            OtpService.request("otp.nurse@example.com");
            check("a failed e-mail does not block the next request", outbox.size() == 4);

            now[0] = now[0].plusSeconds(120);
            User off = UserDAO.findAll("otp.nurse").get(0).copy();
            off.setActive(false);
            UserDAO.update(off);
            OtpService.request("otp.nurse@example.com");
            check("a dismissed account gets no e-mail", outbox.size() == 4);

            User on = UserDAO.findAll("otp.nurse").get(0).copy();
            on.setActive(true);
            UserDAO.update(on);
            now[0] = now[0].plusSeconds(120);
            OtpService.request("otp.nurse@example.com");
            String fifth = outbox.get(outbox.size() - 1)[2].replaceAll("(?s).*code is: (\\d{6}).*", "$1");
            expectIllegal("a new password cannot be set before the code was checked",
                    () -> OtpService.setNewPassword("otp.nurse@example.com", "0123456789abcdef", "stepsecret1"));
            String wrongStep = fifth.equals("000000") ? "111111" : "000000";
            expectIllegal("a wrong code gives no token", () -> OtpService.verify("otp.nurse@example.com", wrongStep));
            String token = OtpService.verify("otp.nurse@example.com", fifth);
            check("a right code gives a token", token != null && token.length() >= 16);
            expectIllegal("a wrong token cannot set the password",
                    () -> OtpService.setNewPassword("otp.nurse@example.com", "not-the-token", "stepsecret1"));
            expectIllegal("a weak password is refused and the code stays valid",
                    () -> OtpService.setNewPassword("otp.nurse@example.com", token, "123"));
            OtpService.setNewPassword("otp.nurse@example.com", token, "stepsecret1");
            check("the token sets the new password", UserDAO.authenticate("otp.nurse", "stepsecret1") != null);
            expectIllegal("the token works only once", () -> OtpService.setNewPassword("otp.nurse@example.com", token, "stepsecret2"));
            expectIllegal("the code works only once", () -> OtpService.verify("otp.nurse@example.com", fifth));

            now[0] = now[0].plusSeconds(120);
            OtpService.request("otp.nurse@example.com");
            String sixth = outbox.get(outbox.size() - 1)[2].replaceAll("(?s).*code is: (\\d{6}).*", "$1");
            String token2 = OtpService.verify("otp.nurse@example.com", sixth);
            now[0] = now[0].plusSeconds(5 * 60 + 1);
            expectIllegal("a token cannot be used after the code expired",
                    () -> OtpService.setNewPassword("otp.nurse@example.com", token2, "stepsecret3"));
            UserDAO.delete(id);
            UserDAO.delete(UserDAO.findAll("no.mail").get(0).getUserId());
        } finally {
            Mailer.setSender(null);
            OtpService.setClock(null);
        }
    }

    private static void myProfile() throws Exception {
        section("my profile");
        Session.clear();
        refused("nobody signed in cannot change a profile", () -> UserDAO.updateOwnProfile(new User(), ""));
        refused("nobody signed in cannot change a password", () -> UserDAO.changeOwnPassword("123456", "654321"));

        signIn("nurse.amy");
        int amy = Session.getUser().getUserId();
        User before = UserDAO.findById(amy);
        User change = new User();
        change.setFullName("Nurse Amy Tun Updated");
        change.setPhone(before.getPhone());
        change.setEmail(before.getEmail());
        change.setDateOfBirth(LocalDate.of(1990, 2, 3));
        change.setGender("Female");
        UserDAO.updateOwnProfile(change, "");
        User after = UserDAO.findById(amy);
        check("the name, date of birth and gender can be changed without the password",
                "Nurse Amy Tun Updated".equals(after.getFullName()) && LocalDate.of(1990, 2, 3).equals(after.getDateOfBirth())
                        && "Female".equals(after.getGender()));
        check("the signed-in session shows the new name", "Nurse Amy Tun Updated".equals(Session.getUser().getFullName()));
        check("the role, department, salary and username did not change",
                "NURSE".equals(after.getRole()) && after.getDepartmentId() == 1 && after.getMonthlySalary() == before.getMonthlySalary()
                        && after.getUsername().equals("nurse.amy"));

        User phone = new User();
        phone.setFullName("Nurse Amy Tun");
        phone.setPhone("09-999999999");
        phone.setEmail(null);
        expectIllegal("a new phone number needs the current password", () -> UserDAO.updateOwnProfile(phone, ""));
        expectIllegal("a new phone number with a wrong password is refused", () -> UserDAO.updateOwnProfile(phone, "wrongpass"));
        check("nothing changed after the refusals", "09-770000005".equals(UserDAO.findById(amy).getPhone()));
        UserDAO.updateOwnProfile(phone, "123456");
        check("the phone number changes with the right password", "09-999999999".equals(UserDAO.findById(amy).getPhone()));

        User mail = new User();
        mail.setFullName("Nurse Amy Tun");
        mail.setPhone("09-999999999");
        mail.setEmail(" Amy.Tun@Example.com ");
        expectIllegal("a new e-mail address needs the current password", () -> UserDAO.updateOwnProfile(mail, null));
        UserDAO.updateOwnProfile(mail, "123456");
        check("the e-mail changes with the right password and is kept in lower case", "amy.tun@example.com".equals(UserDAO.findById(amy).getEmail()));
        User badMail = new User();
        badMail.setFullName("Nurse Amy Tun");
        badMail.setPhone("09-999999999");
        badMail.setEmail("not-an-email");
        expectIllegal("a wrong e-mail address is refused", () -> UserDAO.updateOwnProfile(badMail, "123456"));
        User takenMail = new User();
        takenMail.setFullName("Nurse Amy Tun");
        takenMail.setPhone("09-999999999");
        takenMail.setEmail("ht3th5612@gmail.com");
        signIn("inventory");
        User inv = UserDAO.findById(Session.getUser().getUserId());
        User invMail = new User();
        invMail.setFullName(inv.getFullName());
        invMail.setPhone(inv.getPhone());
        invMail.setEmail("amy.tun@example.com");
        expectIllegal("an e-mail address of another staff member cannot be used", () -> UserDAO.updateOwnProfile(invMail, "123456"));
        signIn("nurse.amy");
        User noName = new User();
        noName.setFullName("  ");
        expectIllegal("the name is required", () -> UserDAO.updateOwnProfile(noName, "123456"));
        User future = new User();
        future.setFullName("Nurse Amy Tun");
        future.setPhone("09-999999999");
        future.setEmail("amy.tun@example.com");
        future.setDateOfBirth(LocalDate.now().plusDays(3));
        expectIllegal("a date of birth in the future is refused", () -> UserDAO.updateOwnProfile(future, "123456"));
        User removeMail = new User();
        removeMail.setFullName("Nurse Amy Tun");
        removeMail.setPhone("09-999999999");
        expectIllegal("removing the e-mail also needs the password", () -> UserDAO.updateOwnProfile(removeMail, ""));
        check("a user's own change does not touch the other accounts",
                count("SELECT COUNT(*) FROM users WHERE full_name LIKE 'Nurse Amy Tun%'") == 1);

        expectIllegal("a password change needs the current password", () -> UserDAO.changeOwnPassword("", "newsecret1"));
        expectIllegal("a wrong current password is refused", () -> UserDAO.changeOwnPassword("nope123", "newsecret1"));
        expectIllegal("a weak new password is refused", () -> UserDAO.changeOwnPassword("123456", "123"));
        expectIllegal("the new password must differ from the current one", () -> UserDAO.changeOwnPassword("123456", "123456"));
        check("the password is unchanged after the refusals", UserDAO.authenticate("nurse.amy", "123456") != null);
        UserDAO.changeOwnPassword("123456", "newsecret1");
        check("the new password works and the old one does not", UserDAO.authenticate("nurse.amy", "newsecret1") != null
                && UserDAO.authenticate("nurse.amy", "123456") == null);

        for (int i = 0; i < 5; i++) {
            expectIllegal("wrong current password " + (i + 1), () -> UserDAO.changeOwnPassword("wrong-" + System.nanoTime(), "another1"));
        }
        expectIllegal("after five wrong passwords even the right one is refused", () -> UserDAO.changeOwnPassword("newsecret1", "another1"));
        check("a new sign-in opens it again", UserDAO.authenticate("nurse.amy", "newsecret1") != null);
        UserDAO.changeOwnPassword("newsecret1", "123456");
        check("the password can be changed back", UserDAO.authenticate("nurse.amy", "123456") != null);

        for (String username : new String[]{"owner", "admin", "dr.alice", "reception", "inventory"}) {
            signIn(username);
            User me = UserDAO.findById(Session.getUser().getUserId());
            User same = new User();
            same.setFullName(me.getFullName());
            same.setPhone(me.getPhone());
            same.setEmail(me.getEmail());
            UserDAO.updateOwnProfile(same, "");
            check("the " + username + " can open and save the own profile", UserDAO.findById(me.getUserId()).getFullName().equals(me.getFullName()));
        }
    }

    private static void profilePhoto() throws Exception {
        section("profile picture");
        Path dir = Files.createTempDirectory("vitalis-photo");
        try {
            java.awt.image.BufferedImage wide = new java.awt.image.BufferedImage(600, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = wide.createGraphics();
            g.setColor(java.awt.Color.ORANGE);
            g.fillRect(0, 0, 600, 300);
            g.dispose();
            java.io.File jpg = dir.resolve("wide.jpg").toFile();
            javax.imageio.ImageIO.write(wide, "jpg", jpg);
            byte[] png = util.ImageUtil.toAvatarPng(jpg);
            java.awt.image.BufferedImage back = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            check("a wide JPG becomes a square 256 x 256 PNG", back.getWidth() == 256 && back.getHeight() == 256);
            check("the picture is small enough for the database", png.length > 0 && png.length < 500_000);

            java.io.File text = dir.resolve("notes.png").toFile();
            Files.writeString(text.toPath(), "this is not a picture");
            expectIllegal("a file that is not a picture is refused", () -> util.ImageUtil.toAvatarPng(text));
            expectIllegal("a missing file is refused", () -> util.ImageUtil.toAvatarPng(dir.resolve("missing.png").toFile()));
            expectIllegal("no file is refused", () -> util.ImageUtil.toAvatarPng(null));

            Session.clear();
            refused("nobody signed in cannot set a picture", () -> UserDAO.updateOwnPhoto(png));
            signIn("dr.alice");
            int alice = Session.getUser().getUserId();
            check("a new account has no picture", UserDAO.findPhoto(alice) == null);
            UserDAO.updateOwnPhoto(png);
            check("the picture is saved and read back unchanged", java.util.Arrays.equals(png, UserDAO.findPhoto(alice)));
            signIn("nurse.amy");
            check("the picture of one user is not the picture of another", UserDAO.findPhoto(Session.getUser().getUserId()) == null);
            expectIllegal("a picture that is too big is refused", () -> UserDAO.updateOwnPhoto(new byte[600_000]));
            check("the lists of staff do not carry the picture", UserDAO.findById(alice).getFullName() != null);
            signIn("dr.alice");
            UserDAO.updateOwnPhoto(null);
            check("the picture can be removed", UserDAO.findPhoto(alice) == null);
        } finally {
            try (var files = Files.list(dir)) {
                files.forEach(p -> p.toFile().delete());
            }
            dir.toFile().delete();
        }
    }

    /** Chat between staff members: private, one to one, with unread counts. */
    private static void messages() throws Exception {
        section("messages");
        Session.clear();
        refused("nobody signed in cannot send a message", () -> MessageDAO.send(4, "hello"));
        refused("nobody signed in cannot read messages", () -> MessageDAO.findConversation(4));
        refused("nobody signed in has no unread count", MessageDAO::countUnread);

        signIn("dr.alice");
        int alice = Session.getUser().getUserId();
        check("the staff list has every other active account and not the own one",
                MessageDAO.findContacts().stream().noneMatch(c -> c.userId() == alice)
                        && MessageDAO.findContacts().stream().anyMatch(c -> c.userId() == 4)
                        && MessageDAO.findContacts().stream().allMatch(c -> c.lastMessage() == null && c.unread() == 0));

        MessageDAO.send(4, "  Patient in bed 3 needs a review  ");
        MessageDAO.send(4, "မင်္ဂလာပါ \uD83D\uDE00");
        check("a message is saved (the spaces around it are cut)",
                MessageDAO.findConversation(4).get(0).getBody().equals("Patient in bed 3 needs a review"));
        check("Myanmar text and emoji are saved as they are",
                MessageDAO.findConversation(4).get(1).getBody().equals("မင်္ဂလာပါ \uD83D\uDE00"));
        check("the sender sees nothing unread", MessageDAO.countUnread() == 0);
        check("a message that is not read yet is marked so", MessageDAO.findConversation(4).stream().noneMatch(Message::isRead));

        signIn("dr.bob");
        int bob = Session.getUser().getUserId();
        check("the receiver has 2 unread messages", MessageDAO.countUnread() == 2);
        MessageDAO.Contact fromAlice = MessageDAO.findContacts().stream().filter(c -> c.userId() == alice).findFirst().orElseThrow();
        check("the staff list shows the last message and the unread count of the sender",
                fromAlice.unread() == 2 && fromAlice.lastMessage().startsWith("မင်္ဂလာပါ"));
        check("the contact with a message comes first", MessageDAO.findContacts().get(0).userId() == alice);
        MessageDAO.send(alice, "I will come now");
        MessageDAO.markRead(alice);
        check("after reading there is nothing unread", MessageDAO.countUnread() == 0);
        check("the conversation has all 3 messages in the order they were sent",
                MessageDAO.findConversation(alice).size() == 3 && MessageDAO.findConversation(alice).get(2).getBody().equals("I will come now"));

        signIn("dr.alice");
        check("the sender sees that the messages were read",
                MessageDAO.findConversation(4).stream().filter(m -> m.getSenderId() == alice).allMatch(Message::isRead));
        check("and has one new message from the other side", MessageDAO.countUnread() == 1);

        signIn("nurse.amy");
        check("a third person does not see the conversation of two others", MessageDAO.findConversation(alice).isEmpty()
                && MessageDAO.findConversation(bob).isEmpty());
        check("and nothing is unread for a third person", MessageDAO.countUnread() == 0);
        MessageDAO.markRead(alice);
        signIn("dr.bob");
        check("reading in the name of another does not touch the messages of the others", MessageDAO.countUnread() == 0);

        signIn("dr.alice");
        expectIllegal("an empty message is refused", () -> MessageDAO.send(4, "   "));
        expectIllegal("a message of only nothing is refused", () -> MessageDAO.send(4, null));
        expectIllegal("a message that is too long is refused", () -> MessageDAO.send(4, "x".repeat(MessageDAO.MAX_LENGTH + 1)));
        MessageDAO.send(4, "y".repeat(MessageDAO.MAX_LENGTH));
        check("a message of exactly the maximum length is accepted", MessageDAO.findConversation(4).size() == 4);
        expectIllegal("a message to oneself is refused", () -> MessageDAO.send(alice, "hello me"));
        expectIllegal("a message to an account that does not exist is refused", () -> MessageDAO.send(99999, "hello"));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 0 WHERE user_id = 5");
        }
        expectIllegal("a message to a dismissed staff member is refused", () -> MessageDAO.send(5, "hello"));
        check("a dismissed staff member is not in the staff list", MessageDAO.findContacts().stream().noneMatch(c -> c.userId() == 5));
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE users SET active = 1 WHERE user_id = 5");
        }

        signIn("owner");
        expectIllegal("an account that has messages cannot be deleted (the history is protected)", () -> UserDAO.delete(4));
        for (String username : new String[]{"owner", "admin", "reception", "inventory", "nurse.amy"}) {
            signIn(username);
            check("the " + username + " can open the staff list", !MessageDAO.findContacts().isEmpty());
        }
    }

    private static void chatFilesAndPins() throws Exception {
        section("chat photos, videos and pins");
        java.nio.file.Path chatFolder = Files.createTempDirectory("vitalis-chat-files");
        System.setProperty("chat.folder", chatFolder.toString());
        Session.clear();
        refused("nobody signed in cannot send a file", () -> MessageDAO.sendFile(4, Message.IMAGE, "a.jpg", new byte[]{1}, ""));
        refused("nobody signed in cannot read a file", () -> MessageDAO.findFile(1));
        refused("nobody signed in cannot pin a message", () -> MessageDAO.setPinned(1, true));
        refused("nobody signed in cannot read the pinned messages", () -> MessageDAO.findPinned(4));

        signIn("dr.alice");
        int alice = Session.getUser().getUserId();
        java.util.Random random = new java.util.Random(7);
        byte[] photo = new byte[40000];
        random.nextBytes(photo);
        byte[] video = new byte[2 * 1024 * 1024 + 123457];
        random.nextBytes(video);

        int before = MessageDAO.findConversation(4).size();
        MessageDAO.sendFile(4, Message.IMAGE, "chest-xray.jpg", photo, "  chest x-ray  ");
        MessageDAO.sendFile(4, Message.VIDEO, "walk-test.mp4", video, "");
        List<Message> conversation = MessageDAO.findConversation(4);
        check("the two files are in the conversation", conversation.size() == before + 2);
        Message image = conversation.get(before);
        Message clip = conversation.get(before + 1);
        check("a photo keeps its type, name, caption and size", image.getKind().equals(Message.IMAGE)
                && image.getFileName().equals("chest-xray.jpg") && image.getBody().equals("chest x-ray")
                && image.getFileSize() == photo.length && image.hasFile());
        check("a video keeps its type, name and size", clip.getKind().equals(Message.VIDEO) && clip.getFileName().equals("walk-test.mp4")
                && clip.getFileSize() == video.length);
        check("a plain text message has no file", MessageDAO.findConversation(4).get(0).getKind().equals(Message.TEXT)
                && !MessageDAO.findConversation(4).get(0).hasFile() && MessageDAO.findConversation(4).get(0).getFileSize() == 0);
        check("the photo comes back exactly as it was sent", java.util.Arrays.equals(MessageDAO.findFile(image.getMessageId()), photo));
        check("a video of 2 MB comes back exactly as it was sent",
                java.util.Arrays.equals(MessageDAO.findFile(clip.getMessageId()), video));
        check("the list of the conversation does not carry the file itself", clip.getFileSize() == video.length);

        signIn("dr.bob");
        check("the receiver can open the file", java.util.Arrays.equals(MessageDAO.findFile(clip.getMessageId()), video));
        MessageDAO.Contact fromAlice = MessageDAO.findContacts().stream().filter(c -> c.userId() == alice).findFirst().orElseThrow();
        check("the staff list shows [Video] for a video", "[Video]".equals(fromAlice.lastMessage()));
        check("the files count as unread messages", MessageDAO.countUnread() >= 2);

        signIn("nurse.amy");
        refused("a third person cannot open the file", () -> MessageDAO.findFile(clip.getMessageId()));
        refused("a third person cannot pin the message", () -> MessageDAO.setPinned(clip.getMessageId(), true));
        check("a third person sees no pinned message of others", MessageDAO.findPinned(alice).isEmpty());

        signIn("dr.bob");
        MessageDAO.setPinned(image.getMessageId(), true);
        MessageDAO.setPinned(clip.getMessageId(), true);
        check("the receiver can pin messages", MessageDAO.findPinned(alice).size() == 2
                && MessageDAO.findPinned(alice).get(0).getMessageId() == image.getMessageId());
        signIn("dr.alice");
        check("the sender sees the pins of the other side too", MessageDAO.findPinned(4).size() == 2
                && MessageDAO.findConversation(4).stream().filter(Message::isPinned).count() == 2);
        MessageDAO.setPinned(image.getMessageId(), false);
        check("a message can be unpinned", MessageDAO.findPinned(4).size() == 1
                && MessageDAO.findPinned(4).get(0).getMessageId() == clip.getMessageId());
        MessageDAO.setPinned(clip.getMessageId(), false);
        check("nothing is pinned after both are unpinned", MessageDAO.findPinned(4).isEmpty());

        expectIllegal("a text message cannot be sent as a file", () -> MessageDAO.sendFile(4, Message.TEXT, "a.txt", new byte[]{1}, ""));
        expectIllegal("an unknown file type is refused", () -> MessageDAO.sendFile(4, "document", "a.pdf", new byte[]{1}, ""));
        expectIllegal("an empty file is refused", () -> MessageDAO.sendFile(4, Message.IMAGE, "a.jpg", new byte[0], ""));
        expectIllegal("a file that is too big is refused",
                () -> MessageDAO.sendFile(4, Message.VIDEO, "big.mp4", new byte[MessageDAO.MAX_FILE_BYTES + 1], ""));
        expectIllegal("a caption that is too long is refused",
                () -> MessageDAO.sendFile(4, Message.IMAGE, "a.jpg", new byte[]{1}, "x".repeat(MessageDAO.MAX_LENGTH + 1)));
        expectIllegal("a file to oneself is refused", () -> MessageDAO.sendFile(alice, Message.IMAGE, "a.jpg", new byte[]{1}, ""));
        expectIllegal("a file to an account that does not exist is refused",
                () -> MessageDAO.sendFile(99999, Message.IMAGE, "a.jpg", new byte[]{1}, ""));
        check("a refused file leaves nothing behind", MessageDAO.findConversation(4).size() == before + 2
                && Files.list(chatFolder).count() == 2);
        Files.delete(chatFolder.resolve(Files.list(chatFolder).filter(p -> p.toFile().length() == photo.length).findFirst().orElseThrow().getFileName()));
        check("a file that was removed from the disk gives a clear message", throwsState(() -> MessageDAO.findFile(image.getMessageId())));

        java.io.File folder = Files.createTempDirectory("vitalis-chat-test").toFile();
        try {
            java.awt.image.BufferedImage wide = new java.awt.image.BufferedImage(2000, 1000, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.io.File png = new java.io.File(folder, "wide.png");
            javax.imageio.ImageIO.write(wide, "png", png);
            byte[] small = util.ChatFiles.photoBytes(png);
            java.awt.image.BufferedImage back = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(small));
            check("a big photo is made smaller before it is sent", back.getWidth() == 1280 && back.getHeight() == 640);
            check("the sent photo is a JPG with the new name", util.ChatFiles.photoName(png).equals("wide.jpg"));
            java.io.File notPicture = new java.io.File(folder, "notes.png");
            Files.writeString(notPicture.toPath(), "not a picture");
            expectIllegal("a file that is not a picture is refused", () -> util.ChatFiles.photoBytes(notPicture));
            java.io.File text = new java.io.File(folder, "clip.txt");
            Files.writeString(text.toPath(), "hello");
            expectIllegal("a text file is not accepted as a video", () -> util.ChatFiles.videoBytes(text));
            java.io.File mp4 = new java.io.File(folder, "clip.MP4");
            Files.write(mp4.toPath(), new byte[]{1, 2, 3});
            check("a video file is read as it is", util.ChatFiles.videoBytes(mp4).length == 3);
            java.io.File huge = new java.io.File(folder, "huge.mp4");
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(huge, "rw")) {
                file.setLength(MessageDAO.MAX_FILE_BYTES + 1L);
            }
            expectIllegal("a video that is too big is refused", () -> util.ChatFiles.videoBytes(huge));
        } finally {
            try (var files = Files.list(folder.toPath())) {
                files.forEach(p -> p.toFile().delete());
            }
            folder.delete();
        }

        try (var left = Files.list(chatFolder)) {
            left.forEach(p -> p.toFile().delete());
        }
        chatFolder.toFile().delete();
        System.clearProperty("chat.folder");
        signIn("dr.alice");
        expectIllegal("a voice message is not a kind of file any more", () -> MessageDAO.sendFile(4, "voice", "voice-message.wav", new byte[]{1}, ""));
        check("sizes are shown in KB and MB", util.ChatFiles.size(2048).equals("2 KB") && util.ChatFiles.size(3 * 1024 * 1024).startsWith("3.0"));
    }

    private static void loginAndRoles() throws Exception {
        section("login and roles");
        Session.clear();
        User owner = UserDAO.authenticate("owner", "123456");
        check("owner can log in with the demo password", owner != null && "OWNER".equals(owner.getRole()));
        check("a wrong password is refused", UserDAO.authenticate("owner", "wrong") == null);
        check("an unknown user is refused", UserDAO.authenticate("nobody", "123456") == null);

        List<String> roles = new ArrayList<>();
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT role FROM users")) {
            while (rs.next()) {
                roles.add(rs.getString(1));
            }
        }
        check("only the 6 roles are used: " + roles,
                List.of("OWNER", "ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST", "INVENTORY").containsAll(roles));
        check("the old CASHIER role cannot be stored any more", insertFails(
                "INSERT INTO users (username, password_hash, full_name, role) VALUES ('c', 'x', 'C', 'CASHIER')"));
    }

    private static void historyIsProtected() throws Exception {
        section("history is protected");
        signIn("reception");
        expectIllegal("a patient with appointments, records and bills cannot be deleted", () -> PatientDAO.delete(1));
        signIn("owner");
        refused("the owner cannot delete a patient", () -> PatientDAO.delete(1));
        refused("the owner cannot register a patient", () -> PatientDAO.create(newPatient("Owner Patient")));
        expectIllegal("a doctor with appointments cannot be deleted", () -> UserDAO.delete(3));
        check("both are still there", count("SELECT COUNT(*) FROM patients WHERE patient_id = 1") == 1
                && count("SELECT COUNT(*) FROM users WHERE user_id = 3") == 1);
        int fresh = createPatientAs("reception", "Never Visited");
        PatientDAO.delete(fresh);
        check("a patient without history can be deleted", count("SELECT COUNT(*) FROM patients WHERE patient_id = " + fresh) == 0);
    }

    private static void doctorSeesOwnAppointmentsOnly() throws Exception {
        section("doctors");
        signIn("dr.alice");
        int alice = Session.getUser().getUserId();
        List<Appointment> own = AppointmentDAO.findAll("", alice);
        check("a doctor's list has only that doctor's visits",
                !own.isEmpty() && own.stream().allMatch(a -> a.getDoctorId() == alice));
        signIn("reception");
        check("the whole hospital has more visits than one doctor",
                AppointmentDAO.findAll("", null).size() > own.size());
    }

    private static void departmentScope() throws Exception {
        section("department scope");
        signIn("nurse.amy");
        check("a nurse sees the patients of the own department", PatientDAO.findAll("").stream().anyMatch(p -> p.getPatientId() == 4));
        check("a nurse does not see the patients of another department", PatientDAO.findAll("").stream().noneMatch(p -> p.getPatientId() == 5));
        check("a patient who was in both departments is seen by both", PatientDAO.findAll("").stream().anyMatch(p -> p.getPatientId() == 6));
        check("a nurse sees only the visits with the doctors of the own department",
                AppointmentDAO.findAll("", null).stream().allMatch(a -> a.getDoctorId() == 2 || a.getDoctorId() == 3)
                        && !AppointmentDAO.findAll("", null).isEmpty());
        check("a nurse sees only the vital signs of the own department's patients",
                VitalDAO.findAll("").stream().anyMatch(v -> v.getPatientId() == 4) && VitalDAO.findAll("").stream().noneMatch(v -> v.getPatientId() == 5));
        check("a nurse sees the records of the own department's doctors only",
                MedicalRecordDAO.findAll("", null).stream().allMatch(r -> r.getDoctorId() == 3 || r.getDoctorId() == 2));
        check("the drop-down of the patients in the hospital is limited too",
                PatientDAO.findInHospital().stream().noneMatch(p -> p.getPatientId() == 5));

        signIn("dr.bob");
        check("a doctor of another department sees David Lee but not Ei Ei Mon",
                PatientDAO.findAll("").stream().anyMatch(p -> p.getPatientId() == 5) && PatientDAO.findAll("").stream().noneMatch(p -> p.getPatientId() == 4));
        check("his appointments are those of department 3 only",
                AppointmentDAO.findAll("", null).stream().allMatch(a -> a.getDoctorId() == 4) && !AppointmentDAO.findAll("", null).isEmpty());
        check("his records are those of department 3 only",
                MedicalRecordDAO.findAll("", null).stream().allMatch(r -> r.getDoctorId() == 4) && !MedicalRecordDAO.findAll("", null).isEmpty());
        check("his vitals are those of department 3 patients",
                VitalDAO.findAll("").stream().anyMatch(v -> v.getPatientId() == 5) && VitalDAO.findAll("").stream().noneMatch(v -> v.getPatientId() == 4));
        check("his own list is the list of the patients he has to treat", AppointmentDAO.findOpenForDoctor(4).stream().allMatch(a -> a.getDoctorId() == 4));

        signIn("admin");
        check("the department admin is limited like a doctor", PatientDAO.findAll("").stream().noneMatch(p -> p.getPatientId() == 5)
                && AppointmentDAO.findAll("", null).stream().allMatch(a -> a.getDoctorId() == 2 || a.getDoctorId() == 3));

        for (String unlimited : new String[]{"owner", "reception"}) {
            signIn(unlimited);
            check("the " + unlimited + " sees every department",
                    PatientDAO.findAll("").stream().anyMatch(p -> p.getPatientId() == 4) && PatientDAO.findAll("").stream().anyMatch(p -> p.getPatientId() == 5));
        }

        signIn("reception");
        check("a department admin is listed among the doctors", DoctorDAO.findActive().stream().anyMatch(d -> d.getUserId() == 2));
        int bookedPatient = createPatientAs("reception", "Admin Booked");
        Appointment withAdmin = newAppointment(bookedPatient, 2);
        withAdmin.setAppointmentTime(LocalTime.of(23, 50));
        check("an appointment can be booked with a department admin", AppointmentDAO.create(withAdmin) > 0);
    }

    private static void dashboardData() throws Exception {
        section("dashboard data");
        signIn("owner");
        Map<String, Integer> week = ReportDAO.visitsPerDay(7, null);
        check("the visit chart has one point for each of the last 7 days", week.size() == 7);
        int monthTotal = ReportDAO.visitStatusThisMonth(null).values().stream().mapToInt(Integer::intValue).sum();
        check("the visit status of the month never counts a status twice",
                ReportDAO.visitStatusThisMonth(null).keySet().stream().distinct().count()
                        == ReportDAO.visitStatusThisMonth(null).size() && monthTotal >= 0);
        check("the department overview lists every department",
                ReportDAO.departmentOverview().size() == DepartmentDAO.findAll().size());
        check("today's visits are all of today and none is cancelled",
                AppointmentDAO.findToday(null).stream().allMatch(a -> a.getAppointmentDate().equals(LocalDate.now())
                        && !"Cancelled".equals(a.getStatus())));
        check("items that expire soon are all inside the next 30 days",
                InventoryDAO.findExpiringSoon(30).size() == ReportDAO.countExpiringSoon(30)
                        && InventoryDAO.findExpiringSoon(30).stream().allMatch(i -> i.getExpiryDate() != null
                        && !i.getExpiryDate().isBefore(LocalDate.now()) && !i.getExpiryDate().isAfter(LocalDate.now().plusDays(30))));
        check("a user without a department has no department staff", ReportDAO.countStaffInDepartment(null) == 0);
        int ownerWeek = week.values().stream().mapToInt(Integer::intValue).sum();
        int ownerInHospital = AppointmentDAO.findInHospital().size();

        signIn("dr.alice");
        int aliceId = Session.getUser().getUserId();
        check("a doctor sees only her own visits today",
                AppointmentDAO.findToday(aliceId).stream().allMatch(a -> a.getDoctorId() == aliceId));
        check("a doctor's department visits are part of the hospital visits",
                ReportDAO.visitsPerDay(7, null).values().stream().mapToInt(Integer::intValue).sum() <= ownerWeek);
        check("a doctor's own visits are part of her department visits",
                ReportDAO.visitsPerDay(7, aliceId).values().stream().mapToInt(Integer::intValue).sum()
                        <= ReportDAO.visitsPerDay(7, null).values().stream().mapToInt(Integer::intValue).sum());
        check("the patients in the hospital of a doctor are the same as in the patient list",
                AppointmentDAO.findInHospital().stream().map(h -> h.patientId()).distinct().count()
                        == PatientDAO.findInHospital().size()
                        && AppointmentDAO.findInHospital().size() <= ownerInHospital);

        signIn("nurse.amy");
        check("a nurse sees the same in-hospital patients as the patient list",
                AppointmentDAO.findInHospital().stream().map(h -> h.patientId()).distinct().count()
                        == PatientDAO.findInHospital().size());
        signIn("admin");
        check("a department admin counts the staff of the own department",
                ReportDAO.countStaffInDepartment(Session.getUser().getDepartmentId()) > 0);
    }

    private static void validators() {
        section("input checks");
        check("money 12.5 is accepted", Validators.requireMoney("12.5", "Amount") == 12.5);
        expectIllegal("zero is not a valid price", () -> Validators.requireMoney("0", "Amount"));
        expectIllegal("text is not a number", () -> Validators.requireMoney("abc", "Amount"));
        check("an empty optional amount is 0", Validators.optionalNonNegative("", "Fee") == 0);
        expectIllegal("a negative fee is refused", () -> Validators.optionalNonNegative("-5", "Fee"));
        check("age 30 is accepted", Validators.requireInt("30", "Age", 0, 150) == 30);
        expectIllegal("age 200 is refused", () -> Validators.requireInt("200", "Age", 0, 150));
        expectIllegal("an empty text is refused", () -> Validators.requireText("  ", "Name"));
        expectIllegal("a short password is refused", () -> Validators.requirePassword("12345"));
        check("a 6 character password is accepted", Validators.requirePassword("123456").equals("123456"));
    }

    private static void section(String title) {
        System.out.println("\n== " + title);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok      " + name);
        } else {
            failures.add(name);
            System.out.println("  FAILED  " + name);
        }
    }

    private static boolean throwsState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void refused(String name, Runnable action) {
        try {
            action.run();
            check(name, false);
        } catch (SecurityException e) {
            check(name, true);
        } catch (RuntimeException e) {
            System.out.println("          (unexpected: " + e + ")");
            check(name, false);
        }
    }

    private static void expectIllegal(String name, Runnable action) {
        try {
            action.run();
            check(name, false);
        } catch (IllegalArgumentException e) {
            check(name, true);
        } catch (RuntimeException e) {
            System.out.println("          (unexpected: " + e + ")");
            check(name, false);
        }
    }

    private static void signIn(String username) throws SQLException {
        try (Connection con = rootConnection(TEST_DB);
             PreparedStatement ps = con.prepareStatement("SELECT user_id, role, full_name, department_id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int department = rs.getInt(4);
                Integer departmentId = rs.wasNull() ? null : department;
                Session.setUser(new User(rs.getInt(1), username, rs.getString(3), rs.getString(2), departmentId, null, null, true));
            }
        }
    }

    private static int createPatientAs(String username, String name) throws Exception {
        signIn(username);
        PatientDAO.create(newPatient(name));
        return latestPatientId();
    }

    private static Patient newPatient(String name) {
        Patient patient = new Patient();
        patient.setName(name);
        patient.setAge(30);
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setGender("Female");
        patient.setPhone("09-000000000");
        patient.setAddress("Test street");
        patient.setBloodGroup("A+");
        return patient;
    }

    private static Appointment newAppointment(int patientId, int doctorId) {
        Appointment appointment = new Appointment();
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        appointment.setAppointmentDate(LocalDate.now());
        appointment.setAppointmentTime(LocalTime.of(9, 0));
        appointment.setReason("test");
        appointment.setStatus("Scheduled");
        return appointment;
    }

    private static Vital newVital(int patientId, int nurseId) {
        Vital vital = new Vital();
        vital.setPatientId(patientId);
        vital.setNurseId(nurseId);
        vital.setBloodPressure("120/80");
        vital.setTemperature(36.8);
        vital.setPulse(70);
        vital.setNotes("");
        return vital;
    }

    private static Appointment findVisit(int patientId, int appointmentId) {
        return AppointmentDAO.findByPatient(patientId).stream()
                .filter(a -> a.getAppointmentId() == appointmentId).findFirst().orElseThrow();
    }

    private static int latestPatientIdByName(String name) {
        return PatientDAO.findAll(name).stream().filter(p -> p.getName().equals(name))
                .mapToInt(Patient::getPatientId).max().orElseThrow();
    }

    private static int latestPatientId() {
        return PatientDAO.findAll("").stream().mapToInt(Patient::getPatientId).max().orElseThrow();
    }

    private static int completedVisit(int patientId, int doctorId) {
        int visit = AppointmentDAO.startWalkIn(patientId, doctorId, null);
        AppointmentDAO.updateStatus(visit, "Completed");
        return visit;
    }

    private static Billing medicine(int itemId, Integer quantity) {
        Billing request = new Billing();
        request.setInventoryId(itemId);
        request.setQuantity(quantity);
        return request;
    }

    private static InventoryItem findRoom(String name) {
        return RoomDAO.findAll().stream().filter(r -> r.getItemName().equals(name)).findFirst().orElseThrow();
    }

    private static double decimal(String sql) throws SQLException {
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getDouble(1);
        }
    }

    private static boolean inHospital(int patientId) {
        return PatientDAO.findInHospital().stream().anyMatch(p -> p.getPatientId() == patientId);
    }

    private static int stock(int itemId) throws SQLException {
        return (int) count("SELECT stock_quantity FROM inventory WHERE item_id = " + itemId);
    }

    private static long count(String sql) throws SQLException {
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean insertFails(String sql) throws SQLException {
        try (Connection con = rootConnection(TEST_DB); Statement st = con.createStatement()) {
            st.executeUpdate(sql);
            return false;
        } catch (SQLException expected) {
            return true;
        }
    }

    private static Connection rootConnection(String database) throws SQLException {
        String url = database == null ? SERVER : DB_URL;
        return DriverManager.getConnection(url, "root", "");
    }

    private static void createTestDatabase() throws Exception {
        String script = Files.readString(Path.of("sql", "hospital_db.sql")).replace("hospital_db", TEST_DB);
        if (script.replace(TEST_DB, "").contains("hospital_db")) {
            throw new IllegalStateException("Refusing to run: the script still names hospital_db.");
        }
        try (Connection con = rootConnection(null)) {
            for (String statement : splitStatements(script)) {
                try (Statement st = con.createStatement()) {
                    st.execute(statement);
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not prepare the test database. Is MySQL running in XAMPP? " + e.getMessage());
            System.exit(2);
        }
        System.setProperty("db.url", DB_URL);
        System.setProperty("db.user", "root");
        System.setProperty("db.password", "");
    }

    private static void dropTestDatabase() throws SQLException {
        try (Connection con = rootConnection(null); Statement st = con.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inText = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (!inText && c == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                while (i < script.length() && script.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                inText = !inText;
            }
            if (c == ';' && !inText) {
                if (!current.toString().isBlank()) {
                    statements.add(current.toString().trim());
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
