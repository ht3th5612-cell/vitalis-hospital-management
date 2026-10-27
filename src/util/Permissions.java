package util;

import model.User;

import java.util.Map;
import java.util.Set;

public final class Permissions {

    public static final String PATIENT_VIEW = "PATIENT_VIEW";
    public static final String PATIENT_EDIT = "PATIENT_EDIT";
    public static final String PATIENT_DELETE = "PATIENT_DELETE";

    public static final String APPOINTMENT_VIEW = "APPOINTMENT_VIEW";
    public static final String APPOINTMENT_BOOK = "APPOINTMENT_BOOK";
    public static final String APPOINTMENT_UPDATE = "APPOINTMENT_UPDATE";

    public static final String VITAL_VIEW = "VITAL_VIEW";
    public static final String VITAL_RECORD = "VITAL_RECORD";

    public static final String RECORD_VIEW = "RECORD_VIEW";
    public static final String RECORD_WRITE = "RECORD_WRITE";

    public static final String BILL_VIEW = "BILL_VIEW";
    public static final String BILL_CREATE = "BILL_CREATE";

    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_RESTOCK = "INVENTORY_RESTOCK";
    public static final String INVENTORY_EDIT = "INVENTORY_EDIT";
    public static final String ROOM_MANAGE = "ROOM_MANAGE";

    public static final String DOCTOR_MANAGE = "DOCTOR_MANAGE";
    public static final String DEPARTMENT_MANAGE = "DEPARTMENT_MANAGE";
    public static final String STAFF_MANAGE = "STAFF_MANAGE";
    public static final String OWNER_ACCOUNTS = "OWNER_ACCOUNTS";
    public static final String RECORDS_OVERVIEW = "RECORDS_OVERVIEW";
    public static final String REPORT_VIEW = "REPORT_VIEW";

    private static final Map<String, Set<String>> ROLES = Map.of(
            "OWNER", Set.of(
                    PATIENT_VIEW,
                    VITAL_VIEW, RECORD_VIEW,
                    BILL_VIEW, RECORDS_OVERVIEW,
                    INVENTORY_VIEW, INVENTORY_RESTOCK, INVENTORY_EDIT, ROOM_MANAGE,
                    DOCTOR_MANAGE, DEPARTMENT_MANAGE, STAFF_MANAGE, OWNER_ACCOUNTS, REPORT_VIEW),

            "ADMIN", Set.of(
                    PATIENT_VIEW,
                    APPOINTMENT_VIEW, APPOINTMENT_UPDATE,
                    VITAL_VIEW,
                    RECORD_VIEW, RECORD_WRITE,
                    DOCTOR_MANAGE, STAFF_MANAGE),

            "RECEPTIONIST", Set.of(
                    PATIENT_VIEW, PATIENT_EDIT, PATIENT_DELETE,
                    APPOINTMENT_VIEW, APPOINTMENT_BOOK, APPOINTMENT_UPDATE,
                    BILL_VIEW, BILL_CREATE,
                    INVENTORY_VIEW),

            "DOCTOR", Set.of(
                    PATIENT_VIEW,
                    APPOINTMENT_VIEW, APPOINTMENT_UPDATE,
                    VITAL_VIEW,
                    RECORD_VIEW, RECORD_WRITE),

            "NURSE", Set.of(
                    PATIENT_VIEW,
                    APPOINTMENT_VIEW,
                    VITAL_VIEW, VITAL_RECORD,
                    RECORD_VIEW),

            "INVENTORY", Set.of(
                    INVENTORY_VIEW, INVENTORY_RESTOCK, INVENTORY_EDIT));

    private Permissions() {
    }

    public static boolean has(String permission) {
        User user = Session.getUser();
        if (user == null || user.getRole() == null) {
            return false;
        }
        return ROLES.getOrDefault(user.getRole().toUpperCase(), Set.of()).contains(permission);
    }

    public static boolean isDepartmentScoped() {
        User user = Session.getUser();
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().toUpperCase();
        return role.equals("DOCTOR") || role.equals("NURSE") || role.equals("ADMIN");
    }

    public static boolean isDepartmentAdmin() {
        User user = Session.getUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    public static void requireEither(String first, String second) {
        if (!has(first) && !has(second)) {
            require(first);
        }
    }

    public static void require(String permission) {
        User user = Session.getUser();
        if (user == null) {
            throw new SecurityException("Please sign in first.");
        }
        if (!has(permission)) {
            throw new SecurityException("Your role (" + user.getRoleLabel() + ") is not allowed to do this.");
        }
    }
}
