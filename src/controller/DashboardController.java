package controller;

import dao.AppointmentDAO;
import dao.AppointmentDAO.InHospital;
import dao.InventoryDAO;
import dao.ReportDAO;
import dao.ReportDAO.MonthFinance;
import dao.RoomDAO;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import model.Appointment;
import model.DashboardStats;
import model.InventoryItem;
import model.User;
import util.DashboardWidgets;
import util.DashboardWidgets.Column;
import util.Formats;
import util.Session;
import util.UiFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static util.DashboardWidgets.areaChart;
import static util.DashboardWidgets.donut;
import static util.DashboardWidgets.evenRow;
import static util.DashboardWidgets.kpi;
import static util.DashboardWidgets.panel;
import static util.DashboardWidgets.row;
import static util.DashboardWidgets.table;

public class DashboardController {

    private static final int TABLE_ROWS = 6;
    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("dd MMM HH:mm");
    private static final String[] PALETTE = {"#16A34A", "#3B82F6", "#F59E0B", "#6366F1", "#EF4444", "#14B8A6", "#94A3B8"};

    @FXML
    private Label lblWelcome;

    @FXML
    private Label lblSubtitle;

    @FXML
    private Label lblRole;

    @FXML
    private Label lblDate;

    @FXML
    private VBox content;

    @FXML
    private void initialize() {
        User user = Session.getUser();
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
        lblWelcome.setText(greeting + ", " + (user == null ? "Staff" : user.getFullName()));
        lblSubtitle.setText("Here's your " + scopeName(user) + " overview");

        String department = user == null || user.getDepartmentName() == null ? "" : " | " + user.getDepartmentName();
        lblRole.setText(user == null ? "" : UiFactory.roleTitle(user.getRole()) + department);
        lblDate.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        User user = Session.getUser();
        if (user == null) {
            return;
        }
        try {
            content.getChildren().setAll(build(user));
        } catch (RuntimeException e) {
            Label error = new Label("Could not load live data. Check that MySQL is running.");
            error.getStyleClass().add("error-label");
            content.getChildren().setAll(error);
            e.printStackTrace();
        }
    }

    private static String scopeName(User user) {
        if (user == null) {
            return "hospital";
        }
        return switch (user.getRole()) {
            case "ADMIN" -> "department";
            case "DOCTOR" -> "clinic";
            case "NURSE" -> "ward";
            case "RECEPTIONIST" -> "front desk";
            case "INVENTORY" -> "stock";
            default -> "hospital";
        };
    }

    private List<Node> build(User user) {
        return switch (user.getRole()) {
            case "ADMIN" -> adminDashboard(user);
            case "DOCTOR" -> doctorDashboard(user);
            case "NURSE" -> nurseDashboard(user);
            case "RECEPTIONIST" -> receptionDashboard();
            case "INVENTORY" -> inventoryDashboard();
            default -> ownerDashboard();
        };
    }

    private List<Node> ownerDashboard() {
        DashboardStats stats = ReportDAO.loadStats();
        List<MonthFinance> months = ReportDAO.monthlyFinance();
        MonthFinance month = months.get(months.size() - 1);
        List<InHospital> inHospital = AppointmentDAO.findInHospital();

        Node kpis = evenRow(
                kpi("REGISTERED PATIENTS", String.valueOf(stats.getPatients()),
                        stats.getActiveDoctors() + " active doctors", "dash-patients.gif", "green"),
                kpi("PATIENTS IN HOSPITAL", String.valueOf(inHospital.size()),
                        stats.getAdmittedPatients() + " staying in rooms", "patients-admitted.gif", "blue"),
                kpi("INCOME THIS MONTH", Formats.money(month.income()),
                        "Expenses " + Formats.money(month.expense()), "dash-collected.gif", "amber"),
                kpi("PROFIT THIS MONTH", Formats.money(month.profit()),
                        stats.getLowStockItems() + " low stock items", "reports-financial.gif", "purple"));

        List<ReportDAO.DepartmentRow> departmentRows = ReportDAO.departmentOverview();
        Node departments = panel("Department Overview", departmentRows.size() + " departments",
                table(departmentRows, "No departments yet.",
                        Column.text("Department", ReportDAO.DepartmentRow::name),
                        Column.text("Staff", d -> String.valueOf(d.staff())),
                        Column.text("Visits this month", d -> String.valueOf(d.visitsThisMonth())),
                        Column.text("In hospital now", d -> String.valueOf(d.inHospital()))));

        return List.of(kpis,
                row(new double[]{64, 36}, visitsPanel(null), bedsPanel()),
                row(new double[]{64, 36}, departments, statusPanel(null)),
                row(new double[]{60, 40}, inHospitalPanel(inHospital, "full"), lowStockPanel()));
    }

    private List<Node> adminDashboard(User user) {
        List<InHospital> inHospital = AppointmentDAO.findInHospital();
        List<Appointment> today = AppointmentDAO.findToday(null);
        Map<String, Integer> status = ReportDAO.visitStatusThisMonth(null);

        Node kpis = evenRow(
                kpi("STAFF IN DEPARTMENT", String.valueOf(ReportDAO.countStaffInDepartment(user.getDepartmentId())),
                        "Doctors and nurses", "dash-medical-team.gif", "green"),
                kpi("VISITS TODAY", String.valueOf(today.size()), "In your department", "appt-today.gif", "blue"),
                kpi("PATIENTS IN HOSPITAL", String.valueOf(inHospital.size()),
                        "Not paid yet", "patients-admitted.gif", "amber"),
                kpi("VISITS THIS MONTH", String.valueOf(total(status)),
                        status.getOrDefault("Completed", 0) + " completed", "profile-record.gif", "purple"));

        return List.of(kpis,
                row(new double[]{64, 36}, visitsPanel(null), statusPanel(null)),
                row(new double[]{50, 50}, schedulePanel(today, true), inHospitalPanel(inHospital, "doctor")));
    }

    private List<Node> doctorDashboard(User user) {
        int me = user.getUserId();
        List<InHospital> inHospital = AppointmentDAO.findInHospital();
        List<Appointment> today = AppointmentDAO.findToday(me);

        Node kpis = evenRow(
                kpi("MY VISITS TODAY", String.valueOf(ReportDAO.countAppointmentsTodayForDoctor(me)),
                        today.size() + " not cancelled", "appt-today.gif", "blue"),
                kpi("MY OPEN PATIENTS", String.valueOf(AppointmentDAO.findOpenForDoctor(me).size()),
                        "Scheduled or admitted", "dash-patients.gif", "green"),
                kpi("PATIENTS IN HOSPITAL", String.valueOf(inHospital.size()),
                        "In your department", "patients-admitted.gif", "amber"),
                kpi("MY MEDICAL RECORDS", String.valueOf(ReportDAO.countRecordsForDoctor(me)),
                        "Written by you", "profile-record.gif", "purple"));

        return List.of(kpis,
                row(new double[]{64, 36}, visitsPanel(me), statusPanel(me)),
                row(new double[]{50, 50}, schedulePanel(today, false), inHospitalPanel(inHospital, "doctor")));
    }

    private List<Node> nurseDashboard(User user) {
        List<InHospital> inHospital = AppointmentDAO.findInHospital();
        List<Appointment> today = AppointmentDAO.findToday(null);
        long staying = inHospital.stream().filter(h -> "Admitted".equals(h.status())).count();

        Node kpis = evenRow(
                kpi("PATIENTS IN HOSPITAL", String.valueOf(inHospital.size()),
                        "In your department", "patients-admitted.gif", "blue"),
                kpi("STAYING IN ROOMS", String.valueOf(staying), "Need regular vitals", "dash-beds-occupied.gif", "green"),
                kpi("VITALS TODAY", String.valueOf(ReportDAO.countVitalsTodayForNurse(user.getUserId())),
                        "By you", "dash-clinic-today.gif", "amber"),
                kpi("VISITS TODAY", String.valueOf(today.size()), "In your department", "appt-today.gif", "purple"));

        return List.of(kpis,
                row(new double[]{64, 36}, visitsPanel(null), statusPanel(null)),
                row(new double[]{58, 42}, inHospitalPanel(inHospital, "nurse"), schedulePanel(today, true)));
    }

    private List<Node> receptionDashboard() {
        DashboardStats stats = ReportDAO.loadStats();
        List<InHospital> inHospital = AppointmentDAO.findInHospital();
        List<Appointment> today = AppointmentDAO.findToday(null);
        List<Appointment> toPay = AppointmentDAO.findToPay();

        Node kpis = evenRow(
                kpi("VISITS TODAY", String.valueOf(today.size()), stats.getPatients() + " registered patients",
                        "appt-today.gif", "blue"),
                kpi("PATIENTS IN HOSPITAL", String.valueOf(inHospital.size()),
                        stats.getAdmittedPatients() + " staying in rooms", "patients-admitted.gif", "green"),
                kpi("WAITING TO PAY", String.valueOf(toPay.size()), "Visits not paid yet",
                        "billing-outstanding.gif", "amber"),
                kpi("REVENUE COLLECTED", Formats.money(stats.getRevenue()),
                        stats.getPendingBills() + " pending bills", "billing-collected.gif", "purple"));

        Node checkout = panel("Waiting for Checkout", toPay.size() + " visits",
                table(top(toPay), "Nobody is waiting for checkout.",
                        Column.person("Patient", Appointment::getPatientName),
                        Column.text("Doctor", Appointment::getDoctorName),
                        Column.text("Date", a -> Formats.date(a.getAppointmentDate())),
                        Column.pill("Status", Appointment::getStatus)));

        return List.of(kpis,
                row(new double[]{64, 36}, visitsPanel(null), bedsPanel()),
                row(new double[]{54, 46}, schedulePanel(today, true), checkout));
    }

    private List<Node> inventoryDashboard() {
        List<InventoryItem> items = InventoryDAO.findAll(null);
        List<InventoryItem> expiring = InventoryDAO.findExpiringSoon(30);
        long outOfStock = items.stream().filter(i -> i.getStockQuantity() == 0).count();

        Node kpis = evenRow(
                kpi("INVENTORY ITEMS", String.valueOf(items.size()), "Medicines and supplies", "store-carried.gif", "green"),
                kpi("LOW STOCK ALERTS", String.valueOf(InventoryDAO.findLowStock().size()),
                        outOfStock + " out of stock", "store-waiting.gif", "amber"),
                kpi("EXPIRING IN 30 DAYS", String.valueOf(expiring.size()), "Check these first",
                        "store-expiring.gif", "purple"),
                kpi("TOTAL STOCK VALUE", Formats.money(items.stream()
                                .mapToDouble(i -> i.getStockQuantity() * i.getUnitCost()).sum()),
                        "At cost price", "dash-collected.gif", "blue"));

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            byCategory.merge(item.getCategory() == null ? "Other" : item.getCategory(), 1, Integer::sum);
        }
        List<String> names = new ArrayList<>(byCategory.keySet());
        Node categories = panel("Items by Category", names.size() + " categories",
                donut(byCategory, name -> PALETTE[names.indexOf(name) % PALETTE.length],
                        String.valueOf(items.size()), "Items"));

        Node expiringPanel = panel("Expiring Soon", expiring.size() + " items",
                table(top(expiring), "Nothing expires in the next 30 days.",
                        Column.text("Item", InventoryItem::getItemName),
                        Column.text("Expiry date", i -> Formats.date(i.getExpiryDate())),
                        Column.text("Stock", i -> String.valueOf(i.getStockQuantity())),
                        Column.pill("Status", i -> daysLeft(i) <= 7 ? "Expiring" : "Soon")));

        return List.of(kpis,
                row(new double[]{60, 40}, lowStockPanel(), categories),
                row(new double[]{100}, expiringPanel));
    }

    private Node visitsPanel(Integer doctorId) {
        return panel("Patient Visits", "Last 7 days", areaChart(ReportDAO.visitsPerDay(7, doctorId), "Visits"));
    }

    private Node statusPanel(Integer doctorId) {
        Map<String, Integer> status = ReportDAO.visitStatusThisMonth(doctorId);
        return panel("Visit Status", "This month",
                donut(status, DashboardWidgets::statusColor, String.valueOf(total(status)), "Visits"));
    }

    private Node bedsPanel() {
        int beds = RoomDAO.findAll().stream().mapToInt(InventoryItem::getStockQuantity).sum();
        int occupied = Math.min(beds, RoomDAO.occupiedBeds().values().stream().mapToInt(Integer::intValue).sum());
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("Occupied", occupied);
        values.put("Available", beds - occupied);
        int percent = beds == 0 ? 0 : Math.round(occupied * 100f / beds);
        return panel("Bed Occupancy", beds + " beds",
                donut(values, DashboardWidgets::statusColor, percent + "%", occupied + " / " + beds + " in use"));
    }

    private Node schedulePanel(List<Appointment> today, boolean withDoctor) {
        Node body = withDoctor
                ? table(top(today), "No visits today.",
                        Column.text("Time", a -> Formats.time(a.getAppointmentTime())),
                        Column.person("Patient", Appointment::getPatientName),
                        Column.text("Doctor", Appointment::getDoctorName),
                        Column.pill("Status", Appointment::getStatus))
                : table(top(today), "No visits today.",
                        Column.text("Time", a -> Formats.time(a.getAppointmentTime())),
                        Column.person("Patient", Appointment::getPatientName),
                        Column.text("Reason", a -> orDash(a.getReason())),
                        Column.pill("Status", Appointment::getStatus));
        return panel(withDoctor ? "Today's Visits" : "My Schedule Today", today.size() + " today", body);
    }

    private Node inHospitalPanel(List<InHospital> list, String layout) {
        List<InHospital> rows = top(list);
        Node body = switch (layout) {
            case "nurse" -> table(rows, "Nobody is in the hospital now.",
                    Column.person("Patient", InHospital::patient),
                    Column.text("Room", h -> orDash(h.room())),
                    Column.text("Latest vitals", h -> orDash(h.vitals())),
                    Column.pill("Status", InHospital::status));
            case "doctor" -> table(rows, "Nobody is in the hospital now.",
                    Column.person("Patient", InHospital::patient),
                    Column.text("Room", h -> orDash(h.room())),
                    Column.text("Diagnosis", h -> orDash(h.diagnosis())),
                    Column.pill("Status", InHospital::status));
            default -> table(rows, "Nobody is in the hospital now.",
                    Column.person("Patient", InHospital::patient),
                    Column.text("Doctor", InHospital::doctor),
                    Column.text("Room", h -> orDash(h.room())),
                    Column.text("Since", h -> h.since() == null ? "-" : h.since().format(SINCE)),
                    Column.pill("Status", InHospital::status));
        };
        return panel("Patients in Hospital", list.size() + " now", body);
    }

    private Node lowStockPanel() {
        List<InventoryItem> low = InventoryDAO.findLowStock();
        return panel("Low Stock", low.size() + " items",
                table(top(low), "Every item has enough stock.",
                        Column.text("Item", InventoryItem::getItemName),
                        Column.text("Stock / reorder", i -> i.getStockQuantity() + " / " + i.getReorderLevel()),
                        Column.pill("Status", i -> i.getStockQuantity() == 0 ? "Out of stock" : "Low stock")));
    }

    private static <T> List<T> top(List<T> list) {
        return list.size() > TABLE_ROWS ? list.subList(0, TABLE_ROWS) : list;
    }

    private static int total(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static long daysLeft(InventoryItem item) {
        return ChronoUnit.DAYS.between(java.time.LocalDate.now(), item.getExpiryDate());
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }
}
