package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;
import util.Validators;

public final class PatientDAO {

    public static final int SEARCH_LIMIT = 50;

    private static final String BASE_SELECT = """
            SELECT patient_id, name, age, date_of_birth, gender, nrc, phone, email, address, blood_group, created_at
            FROM patients
            """;

    private PatientDAO() {
    }

    public static List<Patient> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " WHERE (? = '' OR name LIKE ? OR phone LIKE ? OR blood_group LIKE ? OR nrc LIKE ? OR email LIKE ?)"
                + DaoSupport.patientScope("patient_id")
                + " ORDER BY patient_id DESC";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<Patient> patients = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            ps.setString(5, DaoSupport.like(term));
            ps.setString(6, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    patients.add(map(rs));
                }
            }
            return patients;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load patients.", e);
        }
    }

    public static Patient findById(int patientId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + " WHERE patient_id = ?")) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the patient.", e);
        }
    }

    public static List<Patient> search(String name, LocalDate dateOfBirth, String nrc, String gender) {
        String nameTerm = name == null ? "" : name.trim();
        String nrcTerm = normalizeNrc(nrc);
        if (nameTerm.isEmpty() && dateOfBirth == null && nrcTerm == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1 = 1");
        List<Object> values = new ArrayList<>();
        if (!nameTerm.isEmpty()) {
            sql.append(" AND name LIKE ?");
            values.add(DaoSupport.like(nameTerm));
        }
        if (dateOfBirth != null) {
            sql.append(" AND date_of_birth = ?");
            values.add(java.sql.Date.valueOf(dateOfBirth));
        }
        if (nrcTerm != null) {
            sql.append(" AND UPPER(REPLACE(nrc, ' ', '')) LIKE ?");
            values.add("%" + nrcTerm.replace(" ", "").toUpperCase() + "%");
        }
        if (gender != null && !gender.isBlank()) {
            sql.append(" AND gender = ?");
            values.add(gender);
        }
        sql.append(" ORDER BY name, patient_id LIMIT ").append(SEARCH_LIMIT);
        List<Patient> patients = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    patients.add(map(rs));
                }
            }
            return patients;
        } catch (SQLException e) {
            throw new DatabaseException("Could not search the patients.", e);
        }
    }

    public static List<Patient> findInHospital() {
        String sql = BASE_SELECT + """
                 WHERE patient_id IN (
                     SELECT a.patient_id FROM appointments a
                     WHERE ((a.status = 'Scheduled' AND a.appointment_date <= CURDATE())
                        OR a.status = 'Admitted'
                        OR (a.status IN ('Completed', 'Discharged')
                            AND NOT EXISTS (SELECT 1 FROM billings b WHERE b.appointment_id = a.appointment_id)))
                 """ + DaoSupport.doctorColumnScope("a.doctor_id") + """
                 )
                 ORDER BY name
                 """;
        List<Patient> patients = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                patients.add(map(rs));
            }
            return patients;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the patients in the hospital.", e);
        }
    }

    public static void create(Patient patient) {
        Permissions.require(Permissions.PATIENT_EDIT);
        checkIdentity(patient);
        String sql = """
                INSERT INTO patients (name, age, date_of_birth, gender, nrc, phone, address, blood_group, email)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            requireNoDuplicate(con, patient, 0);
            bind(ps, patient);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not register the patient.", e);
        }
    }

    public static void update(Patient patient) {
        Permissions.require(Permissions.PATIENT_EDIT);
        checkIdentity(patient);
        String sql = """
                UPDATE patients
                SET name = ?, age = ?, date_of_birth = ?, gender = ?, nrc = ?, phone = ?, address = ?, blood_group = ?, email = ?
                WHERE patient_id = ?
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            requireNoDuplicate(con, patient, patient.getPatientId());
            bind(ps, patient);
            ps.setInt(10, patient.getPatientId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the patient.", e);
        }
    }

    public static void delete(int patientId) {
        Permissions.require(Permissions.PATIENT_DELETE);
        String sql = "DELETE FROM patients WHERE patient_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (DaoSupport.isReferenced(e)) {
                throw new IllegalArgumentException(
                        "This patient has appointments, records or bills, so it cannot be deleted.");
            }
            throw new DatabaseException("Could not delete the patient.", e);
        }
    }

    private static void checkIdentity(Patient patient) {
        LocalDate birth = patient.getDateOfBirth();
        if (birth == null) {
            throw new IllegalArgumentException("Date of birth is required.");
        }
        if (birth.isAfter(LocalDate.now()) || birth.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new IllegalArgumentException("The date of birth is not possible.");
        }
        String nrc = normalizeNrc(patient.getNrc());
        if (nrc != null && nrc.length() > 30) {
            throw new IllegalArgumentException("The NRC can have at most 30 characters.");
        }
        patient.setNrc(nrc);
        patient.setEmail(Validators.optionalEmail(patient.getEmail()));
        patient.setAge(Period.between(birth, LocalDate.now()).getYears());
    }

    static String normalizeNrc(String nrc) {
        if (nrc == null || nrc.isBlank()) {
            return null;
        }
        return nrc.trim().replaceAll("\\s+", " ");
    }

    private static void requireNoDuplicate(Connection con, Patient patient, int ownId) throws SQLException {
        if (patient.getNrc() != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT patient_id FROM patients WHERE UPPER(REPLACE(nrc, ' ', '')) = ? AND patient_id <> ? LIMIT 1")) {
                ps.setString(1, patient.getNrc().replace(" ", "").toUpperCase());
                ps.setInt(2, ownId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("A patient with this NRC is already registered (P-"
                                + String.format("%04d", rs.getInt(1)) + ").");
                    }
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT patient_id FROM patients WHERE LOWER(TRIM(name)) = LOWER(?) AND date_of_birth = ? "
                        + "AND patient_id <> ? LIMIT 1")) {
            ps.setString(1, patient.getName().trim());
            ps.setDate(2, java.sql.Date.valueOf(patient.getDateOfBirth()));
            ps.setInt(3, ownId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("This patient is already registered (P-"
                            + String.format("%04d", rs.getInt(1)) + ") with the same name and date of birth.");
                }
            }
        }
    }

    private static void bind(PreparedStatement ps, Patient patient) throws SQLException {
        ps.setString(1, patient.getName());
        ps.setInt(2, patient.getAge());
        ps.setDate(3, java.sql.Date.valueOf(patient.getDateOfBirth()));
        ps.setString(4, patient.getGender());
        ps.setString(5, patient.getNrc());
        ps.setString(6, patient.getPhone());
        ps.setString(7, patient.getAddress());
        ps.setString(8, patient.getBloodGroup());
        ps.setString(9, patient.getEmail());
    }

    private static Patient map(ResultSet rs) throws SQLException {
        Patient patient = new Patient(
                rs.getInt("patient_id"),
                rs.getString("name"),
                rs.getInt("age"),
                rs.getString("gender"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("blood_group"));
        patient.setDateOfBirth(DaoSupport.getLocalDate(rs, "date_of_birth"));
        patient.setNrc(rs.getString("nrc"));
        patient.setEmail(rs.getString("email"));
        patient.setCreatedAt(DaoSupport.getLocalDateTime(rs, "created_at"));
        return patient;
    }
}
