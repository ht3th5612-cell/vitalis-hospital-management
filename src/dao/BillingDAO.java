package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Billing;
import util.Formats;
import util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class BillingDAO {

    public static final List<String> PAYMENT_METHODS = List.of("Cash", "Card", "Bank Transfer", "Insurance", "Other");

    private static final double MAX_TOTAL = 99_999_999.99;

    private static final String BASE_SELECT = """
            SELECT b.billing_id, b.patient_id, p.name AS patient_name, b.description, b.bill_type,
                   b.total_amount, b.payment_status, b.payment_method, b.created_at,
                   b.appointment_id, b.inventory_id, b.quantity, u.full_name AS billed_by_name
            FROM billings b
            JOIN patients p ON p.patient_id = b.patient_id
            LEFT JOIN users u ON u.user_id = b.billed_by
            """;

    private BillingDAO() {
    }

    public static List<Billing> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " WHERE (? = '' OR p.name LIKE ? OR b.description LIKE ? OR b.payment_status LIKE ?)"
                + " ORDER BY b.created_at DESC, b.billing_id DESC";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<Billing> bills = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bills.add(map(rs));
                }
            }
            return bills;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load bills.", e);
        }
    }

    public static Billing findById(int billingId) {
        List<Billing> found = query(BASE_SELECT + " WHERE b.billing_id = ?", billingId, "Could not load the bill.");
        return found.isEmpty() ? null : found.get(0);
    }

    public static List<Billing> findByVisit(int appointmentId) {
        return query(BASE_SELECT + " WHERE b.appointment_id = ? ORDER BY b.billing_id", appointmentId,
                "Could not load the bill of the visit.");
    }

    public static List<Billing> receiptLines(Billing line) {
        if (line.getAppointmentId() == null) {
            return List.of(line);
        }
        List<Billing> lines = findByVisit(line.getAppointmentId());
        return lines.isEmpty() ? List.of(line) : lines;
    }

    public static List<Billing> preview(int appointmentId, List<Billing> medicines) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return buildLines(con, appointmentId, medicines, false).lines;
        } catch (SQLException e) {
            throw new DatabaseException("Could not calculate the bill.", e);
        }
    }

    public static int checkout(int appointmentId, String paymentMethod, List<Billing> medicines, int billedBy) {
        Permissions.require(Permissions.BILL_CREATE);
        if (paymentMethod == null || !PAYMENT_METHODS.contains(paymentMethod)) {
            throw new IllegalArgumentException("Choose the payment method.");
        }
        String insert = """
                INSERT INTO billings
                    (patient_id, appointment_id, description, bill_type, total_amount, payment_status, payment_method,
                     billed_by, inventory_id, quantity)
                VALUES (?, ?, ?, ?, ?, 'Paid', ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Visit visit = buildLines(con, appointmentId, medicines, true);
                if ("Admitted".equals(visit.status)) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE appointments SET status = 'Discharged', discharged_at = NOW() WHERE appointment_id = ?")) {
                        ps.setInt(1, appointmentId);
                        ps.executeUpdate();
                    }
                }
                int firstId = 0;
                for (Billing line : visit.lines) {
                    if (line.getInventoryId() != null) {
                        sellFromStock(con, line.getInventoryId(), line.getQuantity());
                    }
                    try (PreparedStatement ps = con.prepareStatement(insert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, line.getPatientId());
                        ps.setInt(2, appointmentId);
                        ps.setString(3, line.getDescription());
                        ps.setString(4, line.getBillType());
                        ps.setDouble(5, line.getTotalAmount());
                        ps.setString(6, paymentMethod);
                        ps.setInt(7, billedBy);
                        DaoSupport.setNullableInt(ps, 8, line.getInventoryId());
                        DaoSupport.setNullableInt(ps, 9, line.getQuantity());
                        ps.executeUpdate();
                        if (firstId == 0) {
                            firstId = DaoSupport.generatedKey(ps);
                        }
                    }
                }
                con.commit();
                return firstId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the payment.", e);
        }
    }

    public static void markPaid(int billingId, String method) {
        Permissions.require(Permissions.BILL_CREATE);
        if (method == null || !PAYMENT_METHODS.contains(method)) {
            throw new IllegalArgumentException("Choose the payment method.");
        }
        String sql = "UPDATE billings SET payment_status = 'Paid', payment_method = ? "
                + "WHERE billing_id = ? AND payment_status = 'Pending'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, method);
            ps.setInt(2, billingId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Only a pending bill can be marked as paid.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the payment.", e);
        }
    }

    private static final class Visit {
        String status;
        final List<Billing> lines = new ArrayList<>();
    }

    private static Visit buildLines(Connection con, int appointmentId, List<Billing> medicines, boolean lock)
            throws SQLException {
        String sql = """
                SELECT a.patient_id, a.status, a.visit_type, a.room, a.admitted_at, a.discharged_at,
                       u.full_name AS doctor_name, COALESCE(d.consultation_fee, 0) AS fee
                FROM appointments a
                JOIN users u ON u.user_id = a.doctor_id
                LEFT JOIN doctors d ON d.user_id = a.doctor_id
                WHERE a.appointment_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        Visit visit = new Visit();
        int patientId;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("The visit was not found.");
                }
                visit.status = rs.getString("status");
                patientId = rs.getInt("patient_id");
                if (!List.of("Completed", "Discharged", "Admitted").contains(visit.status)) {
                    throw new IllegalArgumentException("A visit can be paid when the doctor has completed it (or the patient is "
                            + "admitted). This visit is " + visit.status + ".");
                }
                requireVisitNotBilledYet(con, appointmentId);

                double fee = rs.getDouble("fee");
                if (fee > 0) {
                    visit.lines.add(line(patientId, appointmentId, "Consultation - " + rs.getString("doctor_name"),
                            "Service", fee, null, null));
                }
                String room = rs.getString("room");
                LocalDateTime admittedAt = DaoSupport.getLocalDateTime(rs, "admitted_at");
                if ("Inpatient".equals(rs.getString("visit_type")) && room != null && admittedAt != null) {
                    LocalDateTime end = DaoSupport.getLocalDateTime(rs, "discharged_at");
                    long days = Math.max(1, ChronoUnit.DAYS.between(admittedAt.toLocalDate(),
                            (end == null ? LocalDateTime.now() : end).toLocalDate()));
                    double price = RoomDAO.priceOf(con, room);
                    if (price > 0) {
                        visit.lines.add(line(patientId, appointmentId,
                                "Room " + room + ": " + days + " day" + (days == 1 ? "" : "s") + " x " + Formats.money(price),
                                "Room", days * price, null, null));
                    }
                }
            }
        }
        if (medicines != null) {
            for (Billing request : medicines) {
                visit.lines.add(medicineLine(con, patientId, appointmentId, request));
            }
        }
        if (visit.lines.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to charge for this visit.");
        }
        double total = 0;
        for (Billing line : visit.lines) {
            total += line.getTotalAmount();
        }
        if (total > MAX_TOTAL) {
            throw new IllegalArgumentException("The total is too large (above 99,999,999.99).");
        }
        return visit;
    }

    private static Billing medicineLine(Connection con, int patientId, int appointmentId, Billing request)
            throws SQLException {
        Integer itemId = request.getInventoryId();
        Integer quantity = request.getQuantity();
        if (itemId == null || quantity == null || quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("Choose a medicine and a quantity of at least 1.");
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_name, category, unit_price, stock_quantity, expiry_date FROM inventory WHERE item_id = ?")) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || RoomDAO.CATEGORY.equals(rs.getString("category"))) {
                    throw new IllegalArgumentException("The inventory item was not found.");
                }
                String name = rs.getString("item_name");
                LocalDate expiry = DaoSupport.getLocalDate(rs, "expiry_date");
                if (expiry != null && expiry.isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException(name + " has expired and cannot be sold.");
                }
                if (quantity > rs.getInt("stock_quantity")) {
                    throw new IllegalArgumentException("Only " + rs.getInt("stock_quantity") + " of " + name + " in stock.");
                }
                double price = rs.getDouble("unit_price");
                if (price <= 0) {
                    throw new IllegalArgumentException(name + " has no price yet.");
                }
                return line(patientId, appointmentId, name + " x " + quantity, "Medicine", price * quantity, itemId, quantity);
            }
        }
    }

    private static Billing line(int patientId, int appointmentId, String description, String type, double amount,
                                Integer itemId, Integer quantity) {
        Billing line = new Billing();
        line.setPatientId(patientId);
        line.setAppointmentId(appointmentId);
        line.setDescription(description);
        line.setBillType(type);
        line.setTotalAmount(Math.round(amount * 100) / 100.0);
        line.setPaymentStatus("Paid");
        line.setInventoryId(itemId);
        line.setQuantity(quantity);
        return line;
    }

    private static void requireVisitNotBilledYet(Connection con, int appointmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT billing_id FROM billings WHERE appointment_id = ? LIMIT 1")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("This visit was already paid (bill #" + rs.getInt(1) + ").");
                }
            }
        }
    }

    private static void sellFromStock(Connection con, int itemId, Integer quantity) throws SQLException {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Enter the quantity to sell.");
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_name, expiry_date FROM inventory WHERE item_id = ? FOR UPDATE")) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("The inventory item was not found.");
                }
                LocalDate expiry = DaoSupport.getLocalDate(rs, "expiry_date");
                if (expiry != null && expiry.isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException(rs.getString("item_name") + " has expired and cannot be sold.");
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE inventory SET stock_quantity = stock_quantity - ? WHERE item_id = ? AND stock_quantity >= ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, itemId);
            ps.setInt(3, quantity);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("There is not enough stock of this item.");
            }
        }
    }

    private static List<Billing> query(String sql, int parameter, String errorMessage) {
        List<Billing> bills = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parameter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bills.add(map(rs));
                }
            }
            return bills;
        } catch (SQLException e) {
            throw new DatabaseException(errorMessage, e);
        }
    }

    private static Billing map(ResultSet rs) throws SQLException {
        Billing billing = new Billing(
                rs.getInt("billing_id"),
                rs.getInt("patient_id"),
                rs.getString("patient_name"),
                rs.getString("description"),
                rs.getDouble("total_amount"),
                rs.getString("payment_status"),
                rs.getString("payment_method"),
                DaoSupport.getLocalDateTime(rs, "created_at"));
        billing.setBillType(rs.getString("bill_type"));
        billing.setAppointmentId(DaoSupport.getInteger(rs, "appointment_id"));
        billing.setInventoryId(DaoSupport.getInteger(rs, "inventory_id"));
        billing.setQuantity(DaoSupport.getInteger(rs, "quantity"));
        billing.setBilledByName(rs.getString("billed_by_name"));
        return billing;
    }
}
