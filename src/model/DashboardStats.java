package model;

public class DashboardStats {

    private final int patients;
    private final int activeDoctors;
    private final int todayAppointments;
    private final double revenue;
    private final int pendingBills;
    private final int lowStockItems;
    private final int admittedPatients;

    public DashboardStats(int patients, int activeDoctors, int todayAppointments,
                          double revenue, int pendingBills, int lowStockItems, int admittedPatients) {
        this.patients = patients;
        this.activeDoctors = activeDoctors;
        this.todayAppointments = todayAppointments;
        this.revenue = revenue;
        this.pendingBills = pendingBills;
        this.lowStockItems = lowStockItems;
        this.admittedPatients = admittedPatients;
    }

    public int getPatients() {
        return patients;
    }

    public int getActiveDoctors() {
        return activeDoctors;
    }

    public int getTodayAppointments() {
        return todayAppointments;
    }

    public double getRevenue() {
        return revenue;
    }

    public int getPendingBills() {
        return pendingBills;
    }

    public int getLowStockItems() {
        return lowStockItems;
    }

    public int getAdmittedPatients() {
        return admittedPatients;
    }
}
