package controller;

import dao.AppointmentDAO;
import dao.MedicalRecordDAO;
import dao.PatientDAO;
import dao.VitalDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.Appointment;
import model.MedicalRecord;
import model.Patient;
import model.Vital;
import util.Formats;

public class PatientHistoryController {

    @FXML
    private TextField txtSearch;
    @FXML
    private Label lblSelected;

    @FXML
    private TableView<Patient> patients;
    @FXML
    private TableColumn<Patient, Integer> colId;
    @FXML
    private TableColumn<Patient, String> colName;
    @FXML
    private TableColumn<Patient, String> colBirth;
    @FXML
    private TableColumn<Patient, Integer> colAge;
    @FXML
    private TableColumn<Patient, String> colGender;
    @FXML
    private TableColumn<Patient, String> colNrc;
    @FXML
    private TableColumn<Patient, String> colPhone;
    @FXML
    private TableColumn<Patient, String> colEmail;

    @FXML
    private TableView<Appointment> visits;
    @FXML
    private TableColumn<Appointment, String> colVisitDate;
    @FXML
    private TableColumn<Appointment, String> colVisitDoctor;
    @FXML
    private TableColumn<Appointment, String> colVisitReason;
    @FXML
    private TableColumn<Appointment, String> colVisitType;
    @FXML
    private TableColumn<Appointment, String> colVisitRoom;
    @FXML
    private TableColumn<Appointment, String> colVisitStatus;

    @FXML
    private TableView<MedicalRecord> records;
    @FXML
    private TableColumn<MedicalRecord, String> colRecDate;
    @FXML
    private TableColumn<MedicalRecord, String> colRecDoctor;
    @FXML
    private TableColumn<MedicalRecord, String> colRecDiagnosis;
    @FXML
    private TableColumn<MedicalRecord, String> colRecPrescription;
    @FXML
    private TableColumn<MedicalRecord, String> colRecNotes;

    @FXML
    private TableView<Vital> vitals;
    @FXML
    private TableColumn<Vital, String> colVitDate;
    @FXML
    private TableColumn<Vital, String> colVitBp;
    @FXML
    private TableColumn<Vital, String> colVitTemp;
    @FXML
    private TableColumn<Vital, Integer> colVitPulse;
    @FXML
    private TableColumn<Vital, String> colVitNurse;
    @FXML
    private TableColumn<Vital, String> colVitNotes;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getPatientId()));
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getName()));
        colBirth.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getDateOfBirth() == null ? "" : Formats.date(cd.getValue().getDateOfBirth())));
        colAge.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getAge()));
        colGender.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGender()));
        colNrc.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNrc()));
        colPhone.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPhone()));
        colEmail.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getEmail()));

        colVisitDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.date(cd.getValue().getAppointmentDate())
                + " " + Formats.time(cd.getValue().getAppointmentTime())));
        colVisitDoctor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDoctorName()));
        colVisitReason.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getReason()));
        colVisitType.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getVisitType()));
        colVisitRoom.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getRoom()));
        colVisitStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));

        colRecDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().getCreatedAt())));
        colRecDoctor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDoctorName()));
        colRecDiagnosis.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDiagnosis()));
        colRecPrescription.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPrescription()));
        colRecNotes.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNotes()));

        colVitDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().getRecordedAt())));
        colVitBp.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBloodPressure()));
        colVitTemp.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTemperature() + " °C"));
        colVitPulse.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getPulse()));
        colVitNurse.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNurseName()));
        colVitNotes.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNotes()));

        for (TableView<?> table : new TableView<?>[]{patients, visits, records, vitals}) {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }
        patients.setPlaceholder(new Label("No patients found."));
        visits.setPlaceholder(new Label("No visits."));
        records.setPlaceholder(new Label("No medical records."));
        vitals.setPlaceholder(new Label("No vital signs."));

        patients.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showHistory(newValue));
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            patients.getItems().setAll(PatientDAO.findAll(txtSearch.getText()));
            patients.refresh();
        } catch (RuntimeException e) {
            lblSelected.setText("Could not load the patients: " + e.getMessage());
        }
    }

    private void showHistory(Patient patient) {
        if (patient == null) {
            lblSelected.setText("Select a patient above");
            visits.getItems().clear();
            records.getItems().clear();
            vitals.getItems().clear();
            return;
        }
        try {
            lblSelected.setText("History of " + patient.getDisplayName());
            visits.getItems().setAll(AppointmentDAO.findByPatient(patient.getPatientId()));
            records.getItems().setAll(MedicalRecordDAO.findByPatient(patient.getPatientId()));
            vitals.getItems().setAll(VitalDAO.findByPatient(patient.getPatientId()));
        } catch (RuntimeException e) {
            lblSelected.setText("Could not load the history: " + e.getMessage());
        }
    }
}
