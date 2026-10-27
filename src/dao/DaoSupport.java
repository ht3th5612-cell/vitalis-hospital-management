package dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

final class DaoSupport {

    private static final int ER_ROW_IS_REFERENCED = 1451;

    private DaoSupport() {
    }

    static Integer scopeDepartment() {
        if (!util.Permissions.isDepartmentScoped()) {
            return null;
        }
        Integer own = util.Session.getUser().getDepartmentId();
        return own == null ? -1 : own;
    }

    static String doctorScope(String doctorUserAlias) {
        Integer department = scopeDepartment();
        return department == null ? "" : " AND " + doctorUserAlias + ".department_id = " + department;
    }

    static String doctorColumnScope(String doctorIdColumn) {
        Integer department = scopeDepartment();
        return department == null ? "" : " AND " + doctorIdColumn
                + " IN (SELECT user_id FROM users WHERE department_id = " + department + ")";
    }

    static String patientScope(String patientIdColumn) {
        Integer department = scopeDepartment();
        return department == null ? "" : " AND " + patientIdColumn + " IN (SELECT a2.patient_id FROM appointments a2 "
                + "JOIN users d2 ON d2.user_id = a2.doctor_id WHERE d2.department_id = " + department + ")";
    }

    static LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    static LocalTime getLocalTime(ResultSet rs, String column) throws SQLException {
        java.sql.Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    static LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        ps.setDate(index, value == null ? null : java.sql.Date.valueOf(value));
    }

    static int generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("The row was saved but no ID was returned.");
    }

    static String like(String term) {
        return "%" + (term == null ? "" : term.trim()) + "%";
    }

    static boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062;
    }

    static boolean isReferenced(SQLException e) {
        return e.getErrorCode() == ER_ROW_IS_REFERENCED;
    }
}
