package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.DashboardStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportDAO {

    private ReportDAO() {
    }

    public static DashboardStats loadStats() {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM patients) AS patients,
                    (SELECT COUNT(*) FROM users WHERE role = 'DOCTOR' AND active = 1) AS active_doctors,
                    (SELECT COUNT(*) FROM appointments WHERE appointment_date = CURDATE()) AS today_appointments,
                    (SELECT COALESCE(SUM(total_amount), 0) FROM billings WHERE payment_status = 'Paid') AS revenue,
                    (SELECT COUNT(*) FROM billings WHERE payment_status = 'Pending') AS pending_bills,
                    (SELECT COUNT(*) FROM inventory WHERE category <> 'Room' AND stock_quantity <= reorder_level) AS low_stock,
                    (SELECT COUNT(*) FROM appointments WHERE status = 'Admitted') AS admitted
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new DashboardStats(
                        rs.getInt("patients"),
                        rs.getInt("active_doctors"),
                        rs.getInt("today_appointments"),
                        rs.getDouble("revenue"),
                        rs.getInt("pending_bills"),
                        rs.getInt("low_stock"),
                        rs.getInt("admitted"));
            }
            return new DashboardStats(0, 0, 0, 0, 0, 0, 0);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load summary.", e);
        }
    }

    public static Map<String, Double> monthlyRevenue() {
        String sql = """
                SELECT DATE_FORMAT(created_at, '%b %Y') AS month_label,
                       DATE_FORMAT(created_at, '%Y-%m') AS sort_key,
                       SUM(total_amount) AS amount
                FROM billings
                WHERE payment_status = 'Paid'
                  AND created_at >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 5 MONTH)
                GROUP BY month_label, sort_key
                ORDER BY sort_key
                """;
        Map<String, Double> result = new LinkedHashMap<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("month_label"), rs.getDouble("amount"));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load revenue report.", e);
        }
    }

    public record MonthFinance(String label, double income, double salary, double medicineCost) {
        public double expense() {
            return salary + medicineCost;
        }

        public double profit() {
            return income - expense();
        }
    }

    public static List<MonthFinance> monthlyFinance() {
        String since = "created_at >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 5 MONTH)";
        Map<String, Double> income = sumByMonth("SELECT DATE_FORMAT(created_at, '%Y-%m'), SUM(total_amount) FROM billings "
                + "WHERE payment_status = 'Paid' AND " + since + " GROUP BY 1");
        Map<String, Double> cost = sumByMonth("SELECT DATE_FORMAT(b.created_at, '%Y-%m'), SUM(b.quantity * i.unit_cost) "
                + "FROM billings b JOIN inventory i ON i.item_id = b.inventory_id "
                + "WHERE b.payment_status = 'Paid' AND b." + since + " GROUP BY 1");
        double salary = salaryTotal();
        List<MonthFinance> months = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int back = 5; back >= 0; back--) {
            YearMonth month = now.minusMonths(back);
            String key = month.toString();
            months.add(new MonthFinance(month.format(util.Formats.MONTH),
                    round(income.getOrDefault(key, 0.0)), salary, round(cost.getOrDefault(key, 0.0))));
        }
        return months;
    }

    public static double salaryTotal() {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(monthly_salary), 0) FROM users WHERE active = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the salaries.", e);
        }
    }

    public record MedicineUsage(String name, int quantity, double sales, double cost) {
    }

    public static List<MedicineUsage> medicineUsage(boolean thisMonthOnly) {
        String sql = "SELECT i.item_name, SUM(b.quantity), SUM(b.total_amount), SUM(b.quantity * i.unit_cost) "
                + "FROM billings b JOIN inventory i ON i.item_id = b.inventory_id "
                + "WHERE b.payment_status = 'Paid' "
                + (thisMonthOnly ? "AND b.created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') " : "")
                + "GROUP BY i.item_id, i.item_name ORDER BY SUM(b.quantity) DESC";
        List<MedicineUsage> usage = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usage.add(new MedicineUsage(rs.getString(1), rs.getInt(2), round(rs.getDouble(3)), round(rs.getDouble(4))));
            }
            return usage;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the medicine usage.", e);
        }
    }

    private static Map<String, Double> sumByMonth(String sql) {
        Map<String, Double> sums = new HashMap<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sums.put(rs.getString(1), rs.getDouble(2));
            }
            return sums;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the finance report.", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    public static Map<String, Integer> visitsPerDay(int days, Integer doctorId) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        String sql = "SELECT a.appointment_date, COUNT(*) FROM appointments a JOIN users u ON u.user_id = a.doctor_id "
                + "WHERE a.status <> 'Cancelled' AND a.appointment_date >= ?"
                + (doctorId == null ? "" : " AND a.doctor_id = ?")
                + DaoSupport.doctorScope("u") + " GROUP BY a.appointment_date";
        Map<LocalDate, Integer> counts = new HashMap<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            if (doctorId != null) {
                ps.setInt(2, doctorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getDate(1).toLocalDate(), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the visits per day.", e);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = from.plusDays(i);
            result.put(day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")), counts.getOrDefault(day, 0));
        }
        return result;
    }

    public static Map<String, Integer> visitStatusThisMonth(Integer doctorId) {
        String sql = "SELECT a.status, COUNT(*) FROM appointments a JOIN users u ON u.user_id = a.doctor_id "
                + "WHERE a.appointment_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')"
                + (doctorId == null ? "" : " AND a.doctor_id = ?")
                + DaoSupport.doctorScope("u") + " GROUP BY a.status";
        Map<String, Integer> counts = new HashMap<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (doctorId != null) {
                ps.setInt(1, doctorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the visit status.", e);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String status : List.of("Scheduled", "Admitted", "Completed", "Discharged", "Cancelled")) {
            if (counts.getOrDefault(status, 0) > 0) {
                result.put(status, counts.get(status));
            }
        }
        return result;
    }

    public record DepartmentRow(String name, int staff, int visitsThisMonth, int inHospital) {
    }

    public static List<DepartmentRow> departmentOverview() {
        String sql = """
                SELECT d.department_name,
                       (SELECT COUNT(*) FROM users u WHERE u.department_id = d.department_id AND u.active = 1),
                       (SELECT COUNT(*) FROM appointments a JOIN users u ON u.user_id = a.doctor_id
                        WHERE u.department_id = d.department_id AND a.status <> 'Cancelled'
                          AND a.appointment_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')),
                       (SELECT COUNT(*) FROM appointments a JOIN users u ON u.user_id = a.doctor_id
                        WHERE u.department_id = d.department_id AND a.status = 'Admitted')
                FROM departments d
                ORDER BY d.department_name
                """;
        List<DepartmentRow> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new DepartmentRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
            }
            return rows;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the departments.", e);
        }
    }

    public static int countStaffInDepartment(Integer departmentId) {
        return departmentId == null ? 0
                : scalarInt("SELECT COUNT(*) FROM users WHERE active = 1 AND department_id = ?", departmentId);
    }

    public static int countAppointmentsTodayForDoctor(int doctorId) {
        return scalarInt("SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND appointment_date = CURDATE()", doctorId);
    }

    public static int countRecordsForDoctor(int doctorId) {
        return scalarInt("SELECT COUNT(*) FROM medical_records WHERE doctor_id = ?", doctorId);
    }

    public static int countVitalsTodayForNurse(int nurseId) {
        return scalarInt("SELECT COUNT(*) FROM patient_vitals WHERE nurse_id = ? AND DATE(recorded_at) = CURDATE()", nurseId);
    }

    public static int countInventoryItems() {
        return scalarInt("SELECT COUNT(*) FROM inventory WHERE category <> 'Room'");
    }

    public static int countExpiringSoon(int days) {
        String sql = """
                SELECT COUNT(*) FROM inventory
                WHERE category <> 'Room' AND expiry_date IS NOT NULL
                  AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load summary.", e);
        }
    }

    private static int scalarInt(String sql) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load summary.", e);
        }
    }

    private static int scalarInt(String sql, int parameter) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parameter);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load summary.", e);
        }
    }
}
