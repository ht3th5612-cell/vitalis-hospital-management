package model;

import java.time.LocalDateTime;

import util.Formats;

public class Billing {

    private int billingId;
    private int patientId;
    private String patientName;
    private String description;
    private String billType = "Service";
    private double totalAmount;
    private String paymentStatus;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private Integer appointmentId;
    private Integer inventoryId;
    private Integer quantity;
    private String billedByName;

    public Billing() {
    }

    public Billing(int billingId, int patientId, String patientName, String description, double totalAmount,
                   String paymentStatus, String paymentMethod, LocalDateTime createdAt) {
        this.billingId = billingId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.createdAt = createdAt;
    }

    public int getBillingId() {
        return billingId;
    }

    public void setBillingId(int billingId) {
        this.billingId = billingId;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBillType() {
        return billType;
    }

    public void setBillType(String billType) {
        this.billType = billType;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Integer appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Integer getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Integer inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getBilledByName() {
        return billedByName;
    }

    public void setBilledByName(String billedByName) {
        this.billedByName = billedByName;
    }

    public String getAmountText() {
        return Formats.money(totalAmount);
    }
}
