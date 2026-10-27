package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.User;
import util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import util.Permissions;
import util.Session;
import util.Validators;

public final class UserDAO {

    private static final String BASE_SELECT = """
            SELECT u.user_id, u.username, u.password_hash, u.full_name, u.role,
                   u.department_id, d.department_name, u.phone, u.date_of_birth, u.gender, u.nrc, u.email,
                   u.monthly_salary, u.active, u.created_at
            FROM users u
            LEFT JOIN departments d ON d.department_id = u.department_id
            """;

    private static final int MAX_WRONG_PASSWORDS = 5;
    private static final int MAX_PHOTO_BYTES = 500_000;
    private static final Map<Integer, Integer> WRONG_PASSWORDS = new HashMap<>();

    private UserDAO() {
    }

    public static User authenticate(String username, String password) {
        String sql = BASE_SELECT + " WHERE u.username = ? AND u.active = 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && PasswordUtil.verify(password, rs.getString("password_hash"))) {
                    User user = map(rs);
                    WRONG_PASSWORDS.remove(user.getUserId());
                    return user;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Login failed because the database could not be reached.", e);
        }
    }

    public static void updateOwnProfile(User changes, String currentPassword) {
        User me = requireSignedIn();
        String fullName = changes.getFullName() == null ? "" : changes.getFullName().trim();
        if (fullName.isEmpty() || fullName.length() > 100) {
            throw new IllegalArgumentException("The name is required (at most 100 characters).");
        }
        String newPhone = blankToNull(changes.getPhone());
        if (newPhone != null && newPhone.length() > 30) {
            throw new IllegalArgumentException("The phone number can have at most 30 characters.");
        }
        String newEmail = Validators.optionalEmail(changes.getEmail());
        try (Connection con = DatabaseConnection.getConnection()) {
            String hash;
            String oldPhone;
            String oldEmail;
            String nrc;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT password_hash, phone, email, nrc FROM users WHERE user_id = ?")) {
                ps.setInt(1, me.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("The account was not found.");
                    }
                    hash = rs.getString(1);
                    oldPhone = blankToNull(rs.getString(2));
                    oldEmail = rs.getString(3) == null ? null : rs.getString(3).toLowerCase();
                    nrc = rs.getString(4);
                }
            }
            if (!Objects.equals(newPhone, oldPhone) || !Objects.equals(newEmail, oldEmail)) {
                requireCurrentPassword(me.getUserId(), currentPassword, hash,
                        "Enter your current password to change the phone number or the e-mail address.");
            }
            changes.setNrc(nrc);
            prepareIdentity(con, changes, me.getUserId());
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE users SET full_name = ?, phone = ?, date_of_birth = ?, gender = ?, email = ? WHERE user_id = ?")) {
                ps.setString(1, fullName);
                ps.setString(2, newPhone);
                DaoSupport.setNullableDate(ps, 3, changes.getDateOfBirth());
                ps.setString(4, changes.getGender());
                ps.setString(5, changes.getEmail());
                ps.setInt(6, me.getUserId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the profile.", e);
        }
        me.setFullName(fullName);
        me.setPhone(newPhone);
        me.setEmail(changes.getEmail());
        me.setDateOfBirth(changes.getDateOfBirth());
        me.setGender(changes.getGender());
    }

    public static void changeOwnPassword(String oldPassword, String newPassword) {
        User me = requireSignedIn();
        try (Connection con = DatabaseConnection.getConnection()) {
            String hash;
            try (PreparedStatement ps = con.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
                ps.setInt(1, me.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("The account was not found.");
                    }
                    hash = rs.getString(1);
                }
            }
            requireCurrentPassword(me.getUserId(), oldPassword, hash, "Enter your current password.");
            PasswordUtil.checkPolicy(newPassword);
            if (newPassword.equals(oldPassword)) {
                throw new IllegalArgumentException("The new password must be different from the current password.");
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                ps.setString(1, PasswordUtil.hash(newPassword));
                ps.setInt(2, me.getUserId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not change the password.", e);
        }
    }

    public static byte[] findPhoto(int userId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT photo FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the picture.", e);
        }
    }

    public static void updateOwnPhoto(byte[] png) {
        User me = requireSignedIn();
        if (png != null && png.length > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("The picture is too big.");
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE users SET photo = ? WHERE user_id = ?")) {
            ps.setBytes(1, png);
            ps.setInt(2, me.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the picture.", e);
        }
    }

    public static User findById(int userId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + " WHERE u.user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the account.", e);
        }
    }

    private static void requireCurrentPassword(int userId, String typed, String hash, String missingMessage) {
        if (WRONG_PASSWORDS.getOrDefault(userId, 0) >= MAX_WRONG_PASSWORDS) {
            throw new IllegalArgumentException("Too many wrong passwords. Sign out and sign in again.");
        }
        if (typed == null || typed.isEmpty()) {
            throw new IllegalArgumentException(missingMessage);
        }
        if (!PasswordUtil.verify(typed, hash)) {
            WRONG_PASSWORDS.merge(userId, 1, Integer::sum);
            throw new IllegalArgumentException("The current password is wrong.");
        }
        WRONG_PASSWORDS.remove(userId);
    }

    private static User requireSignedIn() {
        User me = Session.getUser();
        if (me == null) {
            throw new SecurityException("Please sign in first.");
        }
        return me;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public static User findForPasswordReset(String email) {
        String sql = BASE_SELECT + " WHERE LOWER(u.email) = ? AND u.active = 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email == null ? "" : email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not look up the account.", e);
        }
    }

    public static void resetPasswordAfterOtp(int userId, String plainPassword) {
        PasswordUtil.checkPolicy(plainPassword);
        WRONG_PASSWORDS.remove(userId);
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
            ps.setString(1, PasswordUtil.hash(plainPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not change the password.", e);
        }
    }

    public static List<User> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " WHERE (? = '' OR u.username LIKE ? OR u.full_name LIKE ? OR u.role LIKE ?)"
                + (Permissions.isDepartmentAdmin()
                        ? " AND u.role IN ('DOCTOR', 'NURSE') AND u.department_id <=> " + ownDepartmentOrZero() : "")
                + " ORDER BY u.full_name";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<User> users = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            ps.setString(4, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(map(rs));
                }
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load staff accounts.", e);
        }
    }

    public static int create(String username, String password, String fullName, String role,
                             String phone, Integer departmentId, double monthlySalary) {
        User draft = new User();
        draft.setUsername(username);
        draft.setFullName(fullName);
        draft.setRole(role);
        draft.setPhone(phone);
        draft.setDepartmentId(departmentId);
        draft.setMonthlySalary(monthlySalary);
        return create(draft, password);
    }

    public static int create(User draft, String password) {
        String role = draft.getRole();
        Integer departmentId = draft.getDepartmentId();
        Permissions.require(Permissions.STAFF_MANAGE);
        requireManageable(role, departmentId);
        requireDepartmentFor(role, departmentId);
        requireSalary(draft.getMonthlySalary());
        PasswordUtil.checkPolicy(password);
        String sql = """
                INSERT INTO users (username, password_hash, full_name, role, phone, department_id, monthly_salary, active,
                                   date_of_birth, gender, nrc, email)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            prepareIdentity(con, draft, 0);
            ps.setString(1, draft.getUsername());
            ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, draft.getFullName());
            ps.setString(4, role);
            ps.setString(5, draft.getPhone());
            DaoSupport.setNullableInt(ps, 6, departmentId);
            ps.setDouble(7, Math.round(draft.getMonthlySalary() * 100) / 100.0);
            bindIdentity(ps, 8, draft);
            ps.executeUpdate();
            return DaoSupport.generatedKey(ps);
        } catch (SQLException e) {
            if (DaoSupport.isDuplicateKey(e)) {
                throw new IllegalArgumentException("That username is already in use.");
            }
            throw new DatabaseException("Could not save the staff account.", e);
        }
    }

    public static void update(User user) {
        Permissions.require(Permissions.STAFF_MANAGE);
        requireManageable(user.getRole(), user.getDepartmentId());
        requireDepartmentFor(user.getRole(), user.getDepartmentId());
        requireSalary(user.getMonthlySalary());
        String sql = "UPDATE users SET full_name = ?, role = ?, phone = ?, department_id = ?, active = ?, monthly_salary = ?, "
                + "date_of_birth = ?, gender = ?, nrc = ?, email = ? WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getRole());
            ps.setString(3, user.getPhone());
            DaoSupport.setNullableInt(ps, 4, user.getDepartmentId());
            ps.setBoolean(5, user.isActive());
            ps.setDouble(6, Math.round(user.getMonthlySalary() * 100) / 100.0);
            prepareIdentity(con, user, user.getUserId());
            bindIdentity(ps, 7, user);
            ps.setInt(11, user.getUserId());
            requireManageable(con, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the staff account.", e);
        }
    }

    public static void updatePassword(int userId, String plainPassword) {
        Permissions.require(Permissions.STAFF_MANAGE);
        PasswordUtil.checkPolicy(plainPassword);
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hash(plainPassword));
            ps.setInt(2, userId);
            requireManageable(con, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not reset the password.", e);
        }
    }

    public static void delete(int userId) {
        Permissions.require(Permissions.STAFF_MANAGE);
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            requireManageable(con, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (DaoSupport.isReferenced(e)) {
                throw new IllegalArgumentException(
                        "This account is used in appointments, records or bills and cannot be deleted. Deactivate it instead.");
            }
            throw new DatabaseException("Could not delete the staff account.", e);
        }
    }

    private static void requireOwnerRightsFor(String targetRole) {
        if ("OWNER".equalsIgnoreCase(targetRole)) {
            Permissions.require(Permissions.OWNER_ACCOUNTS);
        }
    }

    static void requireManageable(String targetRole, Integer targetDepartmentId) {
        requireOwnerRightsFor(targetRole);
        if (Permissions.isDepartmentAdmin()) {
            Integer own = Session.getUser().getDepartmentId();
            boolean staff = "DOCTOR".equalsIgnoreCase(targetRole) || "NURSE".equalsIgnoreCase(targetRole);
            if (!staff || own == null || !own.equals(targetDepartmentId)) {
                throw new SecurityException("A department admin can only manage the doctors and nurses of the own department.");
            }
        }
    }

    static void requireManageable(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT role, department_id FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("The account was not found.");
                }
                requireManageable(rs.getString(1), DaoSupport.getInteger(rs, "department_id"));
            }
        }
    }

    static int ownDepartmentOrZero() {
        Integer own = Session.getUser() == null ? null : Session.getUser().getDepartmentId();
        return own == null ? 0 : own;
    }

    private static void prepareIdentity(Connection con, User user, int ownId) throws SQLException {
        java.time.LocalDate birth = user.getDateOfBirth();
        if (birth != null && (birth.isAfter(java.time.LocalDate.now()) || birth.isBefore(java.time.LocalDate.of(1900, 1, 1)))) {
            throw new IllegalArgumentException("The date of birth is not possible.");
        }
        String gender = user.getGender();
        if (gender != null && !java.util.List.of("Male", "Female", "Other").contains(gender)) {
            throw new IllegalArgumentException("Gender must be Male, Female or Other.");
        }
        String nrc = PatientDAO.normalizeNrc(user.getNrc());
        if (nrc != null && nrc.length() > 30) {
            throw new IllegalArgumentException("The NRC can have at most 30 characters.");
        }
        user.setNrc(nrc);
        user.setEmail(Validators.optionalEmail(user.getEmail()));
        requireUnused(con, "UPPER(REPLACE(nrc, ' ', ''))", nrc == null ? null : nrc.replace(" ", "").toUpperCase(), ownId,
                "A staff member with this NRC already exists.");
        requireUnused(con, "LOWER(email)", user.getEmail(), ownId, "This e-mail address is already used by another staff member.");
    }

    private static void requireUnused(Connection con, String column, String value, int ownId, String message)
            throws SQLException {
        if (value == null) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT user_id FROM users WHERE " + column + " = ? AND user_id <> ? LIMIT 1")) {
            ps.setString(1, value);
            ps.setInt(2, ownId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }

    private static void bindIdentity(PreparedStatement ps, int first, User user) throws SQLException {
        DaoSupport.setNullableDate(ps, first, user.getDateOfBirth());
        ps.setString(first + 1, user.getGender());
        ps.setString(first + 2, user.getNrc());
        ps.setString(first + 3, user.getEmail());
    }

    private static void requireDepartmentFor(String role, Integer departmentId) {
        boolean needsDepartment = "DOCTOR".equalsIgnoreCase(role) || "NURSE".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role);
        if (needsDepartment && departmentId == null) {
            throw new IllegalArgumentException("Choose a department: a doctor, nurse or department admin belongs to one.");
        }
    }

    private static void requireSalary(double salary) {
        if (Double.isNaN(salary) || salary < 0 || salary > 99_999_999.99) {
            throw new IllegalArgumentException("The monthly salary must be between 0 and 99,999,999.99.");
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("role"),
                DaoSupport.getInteger(rs, "department_id"),
                rs.getString("department_name"),
                rs.getString("phone"),
                rs.getBoolean("active"));
        user.setMonthlySalary(rs.getDouble("monthly_salary"));
        user.setDateOfBirth(DaoSupport.getLocalDate(rs, "date_of_birth"));
        user.setGender(rs.getString("gender"));
        user.setNrc(rs.getString("nrc"));
        user.setEmail(rs.getString("email"));
        user.setCreatedAt(DaoSupport.getLocalDateTime(rs, "created_at"));
        return user;
    }
}
