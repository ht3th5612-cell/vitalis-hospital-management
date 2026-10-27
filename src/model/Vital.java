package model;

import java.time.LocalDateTime;

public class Vital {

    private int vitalId;
    private int patientId;
    private String patientName;
    private int nurseId;
    private String nurseName;
    private String bloodPressure;
    private double temperature;
    private int pulse;
    private String notes;
    private LocalDateTime recordedAt;

    public Vital() {
    }

    public Vital(int vitalId, int patientId, String patientName, int nurseId, String nurseName,
                 String bloodPressure, double temperature, int pulse, String notes, LocalDateTime recordedAt) {
        this.vitalId = vitalId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.nurseId = nurseId;
        this.nurseName = nurseName;
        this.bloodPressure = bloodPressure;
        this.temperature = temperature;
        this.pulse = pulse;
        this.notes = notes;
        this.recordedAt = recordedAt;
    }

    public int getVitalId() {
        return vitalId;
    }

    public void setVitalId(int vitalId) {
        this.vitalId = vitalId;
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

    public int getNurseId() {
        return nurseId;
    }

    public void setNurseId(int nurseId) {
        this.nurseId = nurseId;
    }

    public String getNurseName() {
        return nurseName;
    }

    public void setNurseName(String nurseName) {
        this.nurseName = nurseName;
    }

    public String getBloodPressure() {
        return bloodPressure;
    }

    public void setBloodPressure(String bloodPressure) {
        this.bloodPressure = bloodPressure;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getPulse() {
        return pulse;
    }

    public void setPulse(int pulse) {
        this.pulse = pulse;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
