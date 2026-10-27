package model;

public class Doctor {

    private int userId;
    private String username;
    private String fullName;
    private String phone;
    private Integer departmentId;
    private String departmentName;
    private String specialization;
    private double consultationFee;
    private boolean active;

    public Doctor() {
    }

    public Doctor(int userId, String username, String fullName, String phone, Integer departmentId,
                  String departmentName, String specialization, double consultationFee, boolean active) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.phone = phone;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.specialization = specialization;
        this.consultationFee = consultationFee;
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public double getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(double consultationFee) {
        this.consultationFee = consultationFee;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getStatus() {
        return active ? "Active" : "Inactive";
    }

    @Override
    public String toString() {
        String department = departmentName == null || departmentName.isBlank() ? "" : " - " + departmentName;
        return fullName + department;
    }
}
