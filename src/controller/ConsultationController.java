package controller;

import dao.AppointmentDAO;
import dao.MedicalRecordDAO;
import dao.PatientDAO;
import dao.RoomDAO;
import dao.VitalDAO;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import model.Appointment;
import model.MedicalRecord;
import model.Patient;
import model.Vital;
import util.AlertUtil;
import util.Formats;
import util.Permissions;
import util.Session;
import util.PatientFinder;
import util.UiFactory;
import util.Validators;

import java.util.List;

public class ConsultationController {

    @FXML
    private TableView<Appointment> table;
    @FXML
    private TableColumn<Appointment, String> colPatient;
    @FXML
    private TableColumn<Appointment, String> colDate;
    @FXML
    private TableColumn<Appointment, String> colTime;
    @FXML
    private TableColumn<Appointment, String> colReason;
    @FXML
    private TableColumn<Appointment, String> colStatus;
    @FXML
    private Label lblWalkIn;

    @FXML
    private Label lblPatient;
    @FXML
    private Label lblVitals;
    @FXML
    private ListView<String> lstHistory;
    @FXML
    private TextField txtDiagnosis;
    @FXML
    private TextArea txtPrescription;
    @FXML
    private TextArea txtNotes;
    @FXML
    private ComboBox<String> cmbRoom;
    @FXML
    private Button btnComplete;
    @FXML
    private Button btnAdmit;
    @FXML
    private Button btnNote;
    @FXML
    private Label lblStatus;

    private Appointment selected;
    private Patient walkInPatient;

    @FXML
    private void initialize() {
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPatientName()));
        colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.date(cd.getValue().getAppointmentDate())));
        colTime.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.time(cd.getValue().getAppointmentTime())));
        colReason.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getReason()));
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));

        table.setPlaceholder(new Label("No patients are waiting."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> show(newValue));

        boolean canWrite = Permissions.has(Permissions.RECORD_WRITE);
        btnComplete.setDisable(!canWrite);
        btnAdmit.setDisable(!canWrite || !Permissions.has(Permissions.APPOINTMENT_UPDATE));
        btnNote.setDisable(!canWrite);

        loadChoices();
        refresh(null);
    }

    @FXML
    private void handleComplete() {
        try {
            Appointment visit = requireVisit("Scheduled", "Only a visit that is waiting (Scheduled) can be completed. "
                    + "An admitted patient is discharged at the Checkout.");
            MedicalRecord record = readRecord(visit);
            AppointmentDAO.updateStatus(visit.getAppointmentId(), "Completed");
            MedicalRecordDAO.create(record);
            finished(visit.getPatientName() + ": visit completed. The patient can pay at the reception.");
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the consultation");
        }
    }

    @FXML
    private void handleAdmit() {
        try {
            Appointment visit = requireVisit("Scheduled", "Only a visit that is waiting (Scheduled) can be admitted.");
            String room = Validators.requireText(cmbRoom.getValue(), "Room");
            MedicalRecord record = readRecord(visit);
            AppointmentDAO.admit(visit.getAppointmentId(), room);
            MedicalRecordDAO.create(record);
            finished(visit.getPatientName() + " admitted to " + room + ".");
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the admission");
        }
    }

    @FXML
    private void handleNote() {
        try {
            Appointment visit = requireVisit(null, null);
            MedicalRecordDAO.create(readRecord(visit));
            finished("Note saved for " + visit.getPatientName() + ".");
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the note");
        }
    }

    @FXML
    private void handleWalkIn() {
        try {
            Patient patient = walkInPatient;
            if (patient == null) {
                throw new IllegalArgumentException("Press Find patient and choose the patient. A new patient is registered at the reception first.");
            }
            int visitId = AppointmentDAO.startWalkIn(patient.getPatientId(), Session.getUser().getUserId(), null);
            setWalkInPatient(null);
            refresh(visitId);
            setStatus(patient.getName() + " can be seen now.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the visit");
        }
    }

    @FXML
    private void handleFindPatient() {
        Patient found = PatientFinder.choose();
        if (found != null) {
            setWalkInPatient(found);
        }
    }

    private void setWalkInPatient(Patient patient) {
        walkInPatient = patient;
        lblWalkIn.setText(patient == null ? "No patient chosen" : patient.getDisplayName());
    }

    @FXML
    private void handleRefresh() {
        loadChoices();
        refresh(selected == null ? null : selected.getAppointmentId());
    }

    private Appointment requireVisit(String requiredStatus, String message) {
        if (selected == null) {
            throw new IllegalArgumentException("Select a patient from the list first.");
        }
        if (requiredStatus != null && !requiredStatus.equals(selected.getStatus())) {
            throw new IllegalArgumentException(message);
        }
        return selected;
    }

    private MedicalRecord readRecord(Appointment visit) {
        MedicalRecord record = new MedicalRecord();
        record.setPatientId(visit.getPatientId());
        record.setDoctorId(Session.getUser().getUserId());
        record.setAppointmentId(visit.getAppointmentId());
        record.setDiagnosis(Validators.requireText(txtDiagnosis.getText(), "Diagnosis"));
        record.setPrescription(Validators.optional(txtPrescription.getText()));
        record.setNotes(Validators.optional(txtNotes.getText()));
        return record;
    }

    private void finished(String message) {
        txtDiagnosis.clear();
        txtPrescription.clear();
        txtNotes.clear();
        refresh(null);
        setStatus(message, true);
    }

    private void loadChoices() {
        try {
            cmbRoom.getItems().setAll(RoomDAO.freeRoomNames());
        } catch (RuntimeException e) {
            setStatus("Could not load the patients or rooms: " + e.getMessage(), false);
        }
    }

    private void refresh(Integer selectVisitId) {
        try {
            List<Appointment> visits = AppointmentDAO.findOpenForDoctor(Session.getUser().getUserId());
            table.getItems().setAll(visits);
            table.refresh();
            if (selectVisitId != null) {
                visits.stream().filter(v -> v.getAppointmentId() == selectVisitId).findFirst()
                        .ifPresent(v -> table.getSelectionModel().select(v));
            }
            if (table.getSelectionModel().getSelectedItem() == null) {
                show(null);
            }
        } catch (RuntimeException e) {
            setStatus("Could not load the patients: " + e.getMessage(), false);
        }
    }

    private void show(Appointment visit) {
        selected = visit;
        if (visit == null) {
            lblPatient.setText("Select a patient from the list.");
            lblVitals.setText("");
            lstHistory.getItems().clear();
            return;
        }
        try {
            Patient patient = PatientDAO.findById(visit.getPatientId());
            lblPatient.setText(visit.getPatientName() + (patient == null ? "" : " - " + patient.getAge() + " years, "
                    + patient.getGender() + ", blood group " + (patient.getBloodGroup() == null ? "?" : patient.getBloodGroup()))
                    + "\nVisit reason: " + (visit.getReason() == null ? "-" : visit.getReason()));
            Vital vital = VitalDAO.findLatest(visit.getPatientId());
            lblVitals.setText(vital == null
                    ? "No vital signs recorded yet (a nurse can record them)."
                    : "Latest vital signs: BP " + vital.getBloodPressure() + ", temperature " + vital.getTemperature()
                    + ", pulse " + vital.getPulse() + " (" + Formats.dateTime(vital.getRecordedAt()) + ")");
            lstHistory.getItems().setAll(MedicalRecordDAO.findByPatient(visit.getPatientId()).stream()
                    .map(r -> Formats.date(r.getCreatedAt().toLocalDate()) + " - " + r.getDiagnosis()
                            + " (" + r.getDoctorName() + ")")
                    .toList());
        } catch (RuntimeException e) {
            setStatus("Could not load the patient: " + e.getMessage(), false);
        }
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
