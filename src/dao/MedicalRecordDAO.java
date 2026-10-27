package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.MedicalRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;

public final class MedicalRecordDAO {

    private static final String BASE_SELECT = """
            SELECT r.record_id, r.patient_id, p.name AS patient_name,
                   r.doctor_id, u.full_name AS doctor_name,
                   r.appointment_id, r.diagnosis, r.prescription, r.notes, r.created_at
            FROM medical_records r
            JOIN patients p ON p.patient_id = r.patient_id
            JOIN users u ON u.user_id = r.doctor_id
            """;

    private MedicalRecordDAO() {
    }

    public static List<MedicalRecord> findAll(String searchTerm, Integer doctorId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" WHERE (? = '' OR p.name LIKE ? OR u.full_name LIKE ? OR r.diagnosis LIKE ?)");
        sql.append(DaoSupport.doctorScope("u"));
        if (doctorId != null) {
            sql.append(" AND r.doctor_id = ?");
        }
        sql.append(" ORDER BY r.created_at DESC, r.record_id DESC");

        String term = searchTerm == null ? "" : searchTerm.trim();
        List<MedicalRecord> records = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            if (doctorId != null) {
                ps.setInt(5, doctorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(map(rs));
                }
            }
            return records;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load medical records.", e);
        }
    }

    public static List<MedicalRecord> findByPatient(int patientId) {
        String sql = BASE_SELECT + " WHERE r.patient_id = ? ORDER BY r.created_at DESC, r.record_id DESC";
        List<MedicalRecord> records = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(map(rs));
                }
            }
            return records;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the records of the patient.", e);
        }
    }

    public static void create(MedicalRecord record) {
        Permissions.require(Permissions.RECORD_WRITE);
        String sql = """
                INSERT INTO medical_records
                    (patient_id, doctor_id, appointment_id, diagnosis, prescription, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, record.getPatientId());
            ps.setInt(2, record.getDoctorId());
            DaoSupport.setNullableInt(ps, 3, record.getAppointmentId());
            ps.setString(4, record.getDiagnosis());
            ps.setString(5, record.getPrescription());
            ps.setString(6, record.getNotes());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the medical record.", e);
        }
    }

    private static MedicalRecord map(ResultSet rs) throws SQLException {
        return new MedicalRecord(
                rs.getInt("record_id"),
                rs.getInt("patient_id"),
                rs.getString("patient_name"),
                rs.getInt("doctor_id"),
                rs.getString("doctor_name"),
                DaoSupport.getInteger(rs, "appointment_id"),
                rs.getString("diagnosis"),
                rs.getString("prescription"),
                rs.getString("notes"),
                DaoSupport.getLocalDateTime(rs, "created_at"));
    }
}
