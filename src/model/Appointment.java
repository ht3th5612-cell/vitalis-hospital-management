package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import util.Formats;

public class Appointment {

    private int appointmentId;
    private int patientId;
    private String patientName;
    private int doctorId;
    private String doctorName;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String reason;
    private String status;
    private String visitType = "Outpatient";
    private String room;
    private LocalDateTime admittedAt;
    private LocalDateTime dischargedAt;
    private double consultationFee;

    public Appointment() {
    }

    public Appointment(int appointmentId, int patientId, String patientName, int doctorId, String doctorName,
                       LocalDate appointmentDate, LocalTime appointmentTime, String reason, String status) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.reason = reason;
        this.status = status;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(int appointmentId) {
        this.appointmentId = appointmentId;
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

    public int getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(int doctorId) {
        this.doctorId = doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public LocalDateTime getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(LocalDateTime admittedAt) {
        this.admittedAt = admittedAt;
    }

    public LocalDateTime getDischargedAt() {
        return dischargedAt;
    }

    public void setDischargedAt(LocalDateTime dischargedAt) {
        this.dischargedAt = dischargedAt;
    }

    public double getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(double consultationFee) {
        this.consultationFee = consultationFee;
    }

    public boolean isInpatient() {
        return "Inpatient".equals(visitType);
    }

    public long getStayDays() {
        if (admittedAt == null) {
            return 0;
        }
        LocalDateTime end = dischargedAt == null ? LocalDateTime.now() : dischargedAt;
        return Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(admittedAt.toLocalDate(), end.toLocalDate()));
    }

    @Override
    public String toString() {
        return Formats.date(appointmentDate) + " - " + doctorName + " (" + status + ")";
    }
}
