package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Vital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;

public final class VitalDAO {

    private static final String BASE_SELECT = """
            SELECT v.vital_id, v.patient_id, p.name AS patient_name,
                   v.nurse_id, u.full_name AS nurse_name,
                   v.blood_pressure, v.temperature, v.pulse, v.notes, v.recorded_at
            FROM patient_vitals v
            JOIN patients p ON p.patient_id = v.patient_id
            JOIN users u ON u.user_id = v.nurse_id
            """;

    private VitalDAO() {
    }

    public static List<Vital> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " WHERE (? = '' OR p.name LIKE ? OR u.full_name LIKE ?)"
                + DaoSupport.patientScope("v.patient_id")
                + " ORDER BY v.recorded_at DESC, v.vital_id DESC";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<Vital> vitals = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vitals.add(map(rs));
                }
            }
            return vitals;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load vital records.", e);
        }
    }

    public static List<Vital> findByPatient(int patientId) {
        String sql = BASE_SELECT + " WHERE v.patient_id = ? ORDER BY v.recorded_at DESC, v.vital_id DESC";
        List<Vital> vitals = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vitals.add(map(rs));
                }
            }
            return vitals;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the vital signs of the patient.", e);
        }
    }

    public static Vital findLatest(int patientId) {
        String sql = BASE_SELECT + " WHERE v.patient_id = ? ORDER BY v.recorded_at DESC, v.vital_id DESC LIMIT 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the vital signs of the patient.", e);
        }
    }

    public static void create(Vital vital) {
        Permissions.require(Permissions.VITAL_RECORD);
        String sql = """
                INSERT INTO patient_vitals
                    (patient_id, nurse_id, blood_pressure, temperature, pulse, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, vital.getPatientId());
            ps.setInt(2, vital.getNurseId());
            ps.setString(3, vital.getBloodPressure());
            ps.setDouble(4, vital.getTemperature());
            ps.setInt(5, vital.getPulse());
            ps.setString(6, vital.getNotes());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the vital record.", e);
        }
    }

    public static void delete(int vitalId) {
        Permissions.require(Permissions.VITAL_RECORD);
        String sql = "DELETE FROM patient_vitals WHERE vital_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, vitalId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the vital record.", e);
        }
    }

    private static Vital map(ResultSet rs) throws SQLException {
        return new Vital(
                rs.getInt("vital_id"),
                rs.getInt("patient_id"),
                rs.getString("patient_name"),
                rs.getInt("nurse_id"),
                rs.getString("nurse_name"),
                rs.getString("blood_pressure"),
                rs.getDouble("temperature"),
                rs.getInt("pulse"),
                rs.getString("notes"),
                DaoSupport.getLocalDateTime(rs, "recorded_at"));
    }
}
