package controller;

import dao.MedicalRecordDAO;
import dao.PatientDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.MedicalRecord;
import model.Patient;
import util.AlertUtil;
import util.Formats;
import util.Session;
import util.UiFactory;
import util.Validators;
import util.Permissions;

public class MedicalRecordController {

    @FXML
    private TableView<MedicalRecord> table;
    @FXML
    private TableColumn<MedicalRecord, Integer> colId;
    @FXML
    private TableColumn<MedicalRecord, String> colPatient;
    @FXML
    private TableColumn<MedicalRecord, String> colDoctor;
    @FXML
    private TableColumn<MedicalRecord, String> colDiagnosis;
    @FXML
    private TableColumn<MedicalRecord, String> colPrescription;
    @FXML
    private TableColumn<MedicalRecord, String> colDate;

    @FXML
    private TextField txtSearch;
    @FXML
    private VBox formCard;
    @FXML
    private ComboBox<Patient> cmbPatient;
    @FXML
    private TextArea txtDiagnosis;
    @FXML
    private TextArea txtPrescription;
    @FXML
    private TextArea txtNotes;
    @FXML
    private Label lblStatus;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getRecordId()));
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPatientName()));
        colDoctor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDoctorName()));
        colDiagnosis.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDiagnosis()));
        colPrescription.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPrescription()));
        colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().getCreatedAt())));

        table.setPlaceholder(new Label("No medical records found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                setStatus("Record #" + newValue.getRecordId() + " selected.", true);
            }
        });
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        boolean canCreate = Permissions.has(Permissions.RECORD_WRITE);
        formCard.setVisible(canCreate);
        formCard.setManaged(canCreate);

        loadPatients();
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            Patient patient = cmbPatient.getValue();
            if (patient == null) {
                throw new IllegalArgumentException("Select a patient (register one first if the list is empty).");
            }

            MedicalRecord record = new MedicalRecord();
            record.setPatientId(patient.getPatientId());
            record.setDoctorId(Session.getUser().getUserId());
            record.setDiagnosis(Validators.requireText(txtDiagnosis.getText(), "Diagnosis"));
            record.setPrescription(Validators.optional(txtPrescription.getText()));
            record.setNotes(Validators.optional(txtNotes.getText()));

            MedicalRecordDAO.create(record);
            setStatus("Medical record saved for " + patient.getName() + ".", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the record");
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        loadPatients();
        refresh();
    }

    private void loadPatients() {
        try {
            UiFactory.refillKeepingChoice(cmbPatient, PatientDAO.findInHospital(),
                    (a, b) -> a.getPatientId() == b.getPatientId());
        } catch (RuntimeException e) {
            setStatus("Could not load patients: " + e.getMessage(), false);
        }
    }

    private void refresh() {
        try {
            table.getItems().setAll(MedicalRecordDAO.findAll(txtSearch.getText(), null));
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load medical records: " + e.getMessage(), false);
        }
    }

    private void clearForm() {
        cmbPatient.getSelectionModel().clearSelection();
        txtDiagnosis.clear();
        txtPrescription.clear();
        txtNotes.clear();
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
