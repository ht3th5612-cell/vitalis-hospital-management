package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {

    private int userId;
    private String username;
    private String fullName;
    private String role;
    private Integer departmentId;
    private String departmentName;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String nrc;
    private String email;
    private double monthlySalary;
    private boolean active;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(int userId, String username, String fullName, String role, Integer departmentId,
                String departmentName, String phone, boolean active) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.phone = phone;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNrc() {
        return nrc;
    }

    public void setNrc(String nrc) {
        this.nrc = nrc;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public User copy() {
        User copy = new User(userId, username, fullName, role, departmentId, departmentName, phone, active);
        copy.dateOfBirth = dateOfBirth;
        copy.gender = gender;
        copy.nrc = nrc;
        copy.email = email;
        copy.monthlySalary = monthlySalary;
        copy.createdAt = createdAt;
        return copy;
    }

    public double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public String getSalaryText() {
        return util.Formats.money(monthlySalary);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return active ? "Active" : "Inactive";
    }

    public String getRoleLabel() {
        if (role == null) {
            return "";
        }
        return switch (role.toUpperCase()) {
            case "OWNER" -> "Owner (Super Admin)";
            case "ADMIN" -> "Department Admin";
            case "DOCTOR" -> "Doctor";
            case "NURSE" -> "Nurse";
            case "RECEPTIONIST" -> "Receptionist / Cashier";
            case "INVENTORY" -> "Inventory Staff";
            default -> role;
        };
    }

    @Override
    public String toString() {
        return fullName == null ? username : fullName;
    }
}
