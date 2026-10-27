package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Appointment;
import util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class AppointmentDAO {

    private static final String BASE_SELECT = """
            SELECT a.appointment_id, a.patient_id, p.name AS patient_name,
                   a.doctor_id, u.full_name AS doctor_name,
                   a.appointment_date, a.appointment_time, a.reason, a.status,
                   a.visit_type, a.room, a.admitted_at, a.discharged_at,
                   COALESCE(d.consultation_fee, 0) AS consultation_fee
            FROM appointments a
            JOIN patients p ON p.patient_id = a.patient_id
            JOIN users u ON u.user_id = a.doctor_id
            LEFT JOIN doctors d ON d.user_id = a.doctor_id
            """;

    private AppointmentDAO() {
    }

    public static List<Appointment> findAll(String searchTerm, Integer doctorId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" WHERE (? = '' OR p.name LIKE ? OR u.full_name LIKE ? OR a.status LIKE ? OR a.room LIKE ?)");
        sql.append(DaoSupport.doctorScope("u"));
        if (doctorId != null) {
            sql.append(" AND a.doctor_id = ?");
        }
        sql.append(" ORDER BY a.appointment_date DESC, a.appointment_time DESC");

        String term = searchTerm == null ? "" : searchTerm.trim();
        List<Appointment> appointments = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            ps.setString(5, DaoSupport.like(term));
            if (doctorId != null) {
                ps.setInt(6, doctorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appointments.add(map(rs));
                }
            }
            return appointments;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load appointments.", e);
        }
    }

    public static List<Appointment> findRecent(int limit, Integer doctorId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" WHERE 1 = 1");
        if (doctorId != null) {
            sql.append(" AND a.doctor_id = ?");
        }
        sql.append(DaoSupport.doctorScope("u"));
        sql.append(" ORDER BY a.appointment_date DESC, a.appointment_time DESC LIMIT ?");

        List<Appointment> appointments = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int index = 1;
            if (doctorId != null) {
                ps.setInt(index++, doctorId);
            }
            ps.setInt(index, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appointments.add(map(rs));
                }
            }
            return appointments;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load recent appointments.", e);
        }
    }

    public static List<Appointment> findToday(Integer doctorId) {
        String sql = BASE_SELECT + " WHERE a.appointment_date = CURDATE() AND a.status <> 'Cancelled'"
                + (doctorId == null ? "" : " AND a.doctor_id = ?")
                + DaoSupport.doctorScope("u") + " ORDER BY a.appointment_time";
        return queryList(sql, doctorId, "Could not load today's visits.");
    }

    public static List<Appointment> findByPatient(int patientId) {
        String sql = BASE_SELECT + " WHERE a.patient_id = ? AND a.status <> 'Cancelled' "
                + "ORDER BY a.appointment_date DESC, a.appointment_time DESC";
        List<Appointment> visits = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    visits.add(map(rs));
                }
            }
            return visits;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the visits of the patient.", e);
        }
    }

    public record InHospital(int appointmentId, int patientId, String patient, String doctor, String status, String room,
                             java.time.LocalDateTime since, String vitals, String diagnosis) {
    }

    public static List<InHospital> findInHospital() {
        String sql = """
                SELECT a.appointment_id, a.patient_id, p.name, u.full_name, a.status, a.room,
                       COALESCE(a.admitted_at, TIMESTAMP(a.appointment_date, a.appointment_time)) AS since,
                       (SELECT CONCAT('BP ', v.blood_pressure, ', ', v.temperature, ' C, pulse ', v.pulse)
                        FROM patient_vitals v WHERE v.patient_id = a.patient_id
                        ORDER BY v.recorded_at DESC, v.vital_id DESC LIMIT 1) AS vitals,
                       (SELECT r.diagnosis FROM medical_records r WHERE r.appointment_id = a.appointment_id
                        ORDER BY r.created_at DESC, r.record_id DESC LIMIT 1) AS diagnosis
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                JOIN users u ON u.user_id = a.doctor_id
                WHERE ((a.status = 'Scheduled' AND a.appointment_date <= CURDATE())
                   OR a.status = 'Admitted'
                   OR (a.status IN ('Completed', 'Discharged')
                       AND NOT EXISTS (SELECT 1 FROM billings b WHERE b.appointment_id = a.appointment_id)))
                """ + DaoSupport.doctorScope("u") + " ORDER BY since";
        List<InHospital> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new InHospital(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), DaoSupport.getLocalDateTime(rs, "since"), rs.getString("vitals"),
                        rs.getString("diagnosis")));
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the patients in the hospital.", e);
        }
    }

    public static Appointment findById(int appointmentId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + " WHERE a.appointment_id = ?")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the visit.", e);
        }
    }

    public static List<Appointment> findOpenForDoctor(int doctorId) {
        String sql = BASE_SELECT + " WHERE a.doctor_id = ? AND a.status IN ('Scheduled', 'Admitted') "
                + "ORDER BY a.appointment_date, a.appointment_time";
        return queryList(sql, doctorId, "Could not load the patients of the doctor.");
    }

    public static List<Appointment> findToPay() {
        String sql = BASE_SELECT + " WHERE a.status IN ('Completed', 'Discharged', 'Admitted') "
                + "AND NOT EXISTS (SELECT 1 FROM billings b WHERE b.appointment_id = a.appointment_id) "
                + "ORDER BY a.appointment_date DESC, a.appointment_time DESC";
        return queryList(sql, null, "Could not load the visits to pay.");
    }

    private static List<Appointment> queryList(String sql, Integer parameter, String errorMessage) {
        List<Appointment> visits = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (parameter != null) {
                ps.setInt(1, parameter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    visits.add(map(rs));
                }
            }
            return visits;
        } catch (SQLException e) {
            throw new DatabaseException(errorMessage, e);
        }
    }

    public static int startWalkIn(int patientId, int doctorId, String reason) {
        Permissions.requireEither(Permissions.APPOINTMENT_BOOK, Permissions.RECORD_WRITE);
        String sql = """
                INSERT INTO appointments
                    (patient_id, doctor_id, appointment_date, appointment_time, reason, status)
                VALUES (?, ?, ?, ?, ?, 'Scheduled')
                """;
        try (Connection con = DatabaseConnection.getConnection()) {
            requireDoctorActive(con, doctorId);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT room FROM appointments WHERE patient_id = ? AND status = 'Admitted' LIMIT 1")) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("This patient is admitted now (room " + rs.getString(1) + ").");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT appointment_id FROM appointments WHERE patient_id = ? AND doctor_id = ? "
                            + "AND appointment_date = CURDATE() AND status = 'Scheduled' LIMIT 1")) {
                ps.setInt(1, patientId);
                ps.setInt(2, doctorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, patientId);
                ps.setInt(2, doctorId);
                ps.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
                ps.setTime(4, java.sql.Time.valueOf(LocalTime.now().withSecond(0).withNano(0)));
                ps.setString(5, reason == null || reason.isBlank() ? "Walk-in" : reason);
                ps.executeUpdate();
                return DaoSupport.generatedKey(ps);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not start the visit.", e);
        }
    }

    public static int create(Appointment appointment) {
        Permissions.require(Permissions.APPOINTMENT_BOOK);
        if (appointment.getAppointmentDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The appointment date is in the past.");
        }
        String sql = """
                INSERT INTO appointments
                    (patient_id, doctor_id, appointment_date, appointment_time, reason, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            requireDoctorActive(con, appointment.getDoctorId());
            requireSlotFree(con, appointment.getDoctorId(), appointment.getPatientId(),
                    appointment.getAppointmentDate(), appointment.getAppointmentTime(), 0);
            ps.setInt(1, appointment.getPatientId());
            ps.setInt(2, appointment.getDoctorId());
            ps.setDate(3, java.sql.Date.valueOf(appointment.getAppointmentDate()));
            ps.setTime(4, java.sql.Time.valueOf(appointment.getAppointmentTime()));
            ps.setString(5, appointment.getReason());
            ps.setString(6, "Scheduled");
            ps.executeUpdate();
            return DaoSupport.generatedKey(ps);
        } catch (SQLException e) {
            throw new DatabaseException("Could not book the appointment.", e);
        }
    }

    public static void updateStatus(int appointmentId, String status) {
        Permissions.require(Permissions.APPOINTMENT_UPDATE);
        String sql = "UPDATE appointments SET status = ? WHERE appointment_id = ? "
                + "AND status NOT IN ('Admitted', 'Discharged')";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            requireStillFreeWhenReopened(con, appointmentId, status);
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "An admitted or discharged visit cannot be changed here. Use Admit / Discharge.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the appointment.", e);
        }
    }

    public static void admit(int appointmentId, String room) {
        Permissions.require(Permissions.APPOINTMENT_UPDATE);
        if (room == null || room.isBlank()) {
            throw new IllegalArgumentException("A room or bed is needed to admit a patient.");
        }
        String sql = """
                UPDATE appointments
                SET status = 'Admitted', visit_type = 'Inpatient', room = ?, admitted_at = NOW()
                WHERE appointment_id = ? AND status = 'Scheduled'
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String roomName = requireBedFree(con, room, appointmentId);
            requirePatientNotAdmitted(con, appointmentId);
            ps.setString(1, roomName);
            ps.setInt(2, appointmentId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Only a scheduled appointment can be admitted.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not admit the patient.", e);
        }
    }

    public static void discharge(int appointmentId) {
        Permissions.require(Permissions.APPOINTMENT_UPDATE);
        String sql = "UPDATE appointments SET status = 'Discharged', discharged_at = NOW() "
                + "WHERE appointment_id = ? AND status = 'Admitted'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Only an admitted patient can be discharged.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not discharge the patient.", e);
        }
    }

    public static List<String> findOccupiedRooms() {
        String sql = "SELECT a.room, p.name FROM appointments a JOIN patients p ON p.patient_id = a.patient_id "
                + "WHERE a.status = 'Admitted' ORDER BY a.room";
        List<String> rooms = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(rs.getString(1) + " (" + rs.getString(2) + ")");
            }
            return rooms;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the occupied rooms.", e);
        }
    }

    public static void delete(int appointmentId) {
        Permissions.require(Permissions.APPOINTMENT_BOOK);
        String sql = "DELETE FROM appointments WHERE appointment_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the appointment.", e);
        }
    }

    private static void requireDoctorActive(Connection con, int doctorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT active FROM users WHERE user_id = ? AND role IN ('DOCTOR', 'ADMIN')")) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new IllegalArgumentException("This doctor is not active, choose another doctor.");
                }
            }
        }
    }

    private static void requireSlotFree(Connection con, int doctorId, int patientId, LocalDate date, LocalTime time,
                                        int ownId) throws SQLException {
        String sql = "SELECT doctor_id FROM appointments WHERE appointment_date = ? AND appointment_time = ? "
                + "AND status NOT IN ('Cancelled', 'No-Show') AND appointment_id <> ? "
                + "AND (doctor_id = ? OR patient_id = ?) LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.setTime(2, java.sql.Time.valueOf(time));
            ps.setInt(3, ownId);
            ps.setInt(4, doctorId);
            ps.setInt(5, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException(rs.getInt(1) == doctorId
                            ? "The doctor already has an appointment at this date and time."
                            : "The patient already has an appointment at this date and time.");
                }
            }
        }
    }

    private static void requireStillFreeWhenReopened(Connection con, int appointmentId, String newStatus)
            throws SQLException {
        if (!"Scheduled".equals(newStatus) && !"Completed".equals(newStatus)) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT doctor_id, patient_id, appointment_date, appointment_time, status FROM appointments "
                        + "WHERE appointment_id = ?")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && ("Cancelled".equals(rs.getString("status")) || "No-Show".equals(rs.getString("status")))) {
                    requireSlotFree(con, rs.getInt("doctor_id"), rs.getInt("patient_id"),
                            DaoSupport.getLocalDate(rs, "appointment_date"), DaoSupport.getLocalTime(rs, "appointment_time"),
                            appointmentId);
                }
            }
        }
    }

    private static String requireBedFree(Connection con, String room, int appointmentId) throws SQLException {
        String name;
        int beds;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_name, stock_quantity FROM inventory WHERE category = 'Room' AND LOWER(item_name) = LOWER(?)")) {
            ps.setString(1, room.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Unknown room. Choose one from the list.");
                }
                name = rs.getString(1);
                beds = rs.getInt(2);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM appointments WHERE status = 'Admitted' AND room = ? AND appointment_id <> ?")) {
            ps.setString(1, name);
            ps.setInt(2, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= beds) {
                    throw new IllegalArgumentException("Room " + name + " is full (" + beds + " bed" + (beds == 1 ? "" : "s") + ").");
                }
            }
        }
        return name;
    }

    private static void requirePatientNotAdmitted(Connection con, int appointmentId) throws SQLException {
        String sql = "SELECT b.room FROM appointments a JOIN appointments b ON b.patient_id = a.patient_id "
                + "WHERE a.appointment_id = ? AND b.status = 'Admitted' AND b.appointment_id <> a.appointment_id LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("This patient is already admitted (room " + rs.getString(1)
                            + "). Discharge first.");
                }
            }
        }
    }

    private static Appointment map(ResultSet rs) throws SQLException {
        Appointment appointment = new Appointment(
                rs.getInt("appointment_id"),
                rs.getInt("patient_id"),
                rs.getString("patient_name"),
                rs.getInt("doctor_id"),
                rs.getString("doctor_name"),
                DaoSupport.getLocalDate(rs, "appointment_date"),
                DaoSupport.getLocalTime(rs, "appointment_time"),
                rs.getString("reason"),
                rs.getString("status"));
        appointment.setVisitType(rs.getString("visit_type"));
        appointment.setRoom(rs.getString("room"));
        appointment.setAdmittedAt(DaoSupport.getLocalDateTime(rs, "admitted_at"));
        appointment.setDischargedAt(DaoSupport.getLocalDateTime(rs, "discharged_at"));
        appointment.setConsultationFee(rs.getDouble("consultation_fee"));
        return appointment;
    }
}
