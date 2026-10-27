package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Doctor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;

public final class DoctorDAO {

    private static final String BASE_SELECT = """
            SELECT u.user_id, u.username, u.full_name, u.phone, u.active,
                   COALESCE(d.department_id, u.department_id) AS department_id,
                   dep.department_name,
                   d.specialization,
                   COALESCE(d.consultation_fee, 0) AS consultation_fee
            FROM users u
            LEFT JOIN doctors d ON d.user_id = u.user_id
            LEFT JOIN departments dep ON dep.department_id = COALESCE(d.department_id, u.department_id)
            WHERE u.role IN ('DOCTOR', 'ADMIN')
            """;

    private DoctorDAO() {
    }

    public static List<Doctor> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " AND (? = '' OR u.full_name LIKE ? OR u.username LIKE ? OR d.specialization LIKE ?"
                + " OR dep.department_name LIKE ?)"
                + (Permissions.isDepartmentAdmin()
                        ? " AND COALESCE(d.department_id, u.department_id) <=> " + UserDAO.ownDepartmentOrZero() : "")
                + " ORDER BY u.full_name";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<Doctor> doctors = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            ps.setString(5, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    doctors.add(map(rs));
                }
            }
            return doctors;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load doctors.", e);
        }
    }

    public static List<Doctor> findActive() {
        String sql = BASE_SELECT + " AND u.active = 1 ORDER BY u.full_name";
        List<Doctor> doctors = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                doctors.add(map(rs));
            }
            return doctors;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load doctors.", e);
        }
    }

    public static void saveProfile(int userId, Integer departmentId, String specialization, double consultationFee) {
        Permissions.require(Permissions.DOCTOR_MANAGE);
        if (Double.isNaN(consultationFee) || consultationFee < 0 || consultationFee > 99_999_999.99) {
            throw new IllegalArgumentException("The consultation fee must be between 0 and 99,999,999.99.");
        }
        String sql = """
                INSERT INTO doctors (user_id, department_id, specialization, consultation_fee)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    department_id = VALUES(department_id),
                    specialization = VALUES(specialization),
                    consultation_fee = VALUES(consultation_fee)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            UserDAO.requireManageable(con, userId);
            ps.setInt(1, userId);
            DaoSupport.setNullableInt(ps, 2, departmentId);
            ps.setString(3, specialization);
            ps.setDouble(4, Math.round(consultationFee * 100) / 100.0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the doctor profile.", e);
        }
    }

    private static Doctor map(ResultSet rs) throws SQLException {
        return new Doctor(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("phone"),
                DaoSupport.getInteger(rs, "department_id"),
                rs.getString("department_name"),
                rs.getString("specialization"),
                rs.getDouble("consultation_fee"),
                rs.getBoolean("active"));
    }
}
