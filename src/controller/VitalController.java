package controller;

import dao.PatientDAO;
import dao.VitalDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.Patient;
import model.Vital;
import util.AlertUtil;
import util.Formats;
import util.Session;
import util.UiFactory;
import util.Validators;
import util.Permissions;

public class VitalController {

    @FXML
    private TableView<Vital> table;
    @FXML
    private TableColumn<Vital, Integer> colId;
    @FXML
    private TableColumn<Vital, String> colPatient;
    @FXML
    private TableColumn<Vital, String> colNurse;
    @FXML
    private TableColumn<Vital, String> colBp;
    @FXML
    private TableColumn<Vital, String> colTemp;
    @FXML
    private TableColumn<Vital, Integer> colPulse;
    @FXML
    private TableColumn<Vital, String> colRecorded;

    @FXML
    private TextField txtSearch;
    @FXML
    private VBox formCard;
    @FXML
    private ComboBox<Patient> cmbPatient;
    @FXML
    private TextField txtBp;
    @FXML
    private TextField txtTemp;
    @FXML
    private TextField txtPulse;
    @FXML
    private TextField txtNotes;
    @FXML
    private Label lblStatus;

    private Vital selected;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getVitalId()));
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPatientName()));
        colNurse.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNurseName()));
        colBp.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBloodPressure()));
        colTemp.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getTemperature() + " °C"));
        colPulse.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getPulse()));
        colRecorded.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().getRecordedAt())));

        table.setPlaceholder(new Label("No vital records found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> selected = newValue);
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        boolean canRecord = Permissions.has(Permissions.VITAL_RECORD);
        formCard.setVisible(canRecord);
        formCard.setManaged(canRecord);

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

            Vital vital = new Vital();
            vital.setPatientId(patient.getPatientId());
            vital.setNurseId(Session.getUser().getUserId());
            vital.setBloodPressure(Validators.requireText(txtBp.getText(), "Blood pressure"));
            vital.setTemperature(Validators.requireDoubleInRange(txtTemp.getText(), "Temperature", 30, 45));
            vital.setPulse(Validators.requireInt(txtPulse.getText(), "Pulse", 30, 220));
            vital.setNotes(Validators.optional(txtNotes.getText()));

            VitalDAO.create(vital);
            setStatus("Vitals recorded for " + patient.getName() + ".", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the vitals");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a vital record from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Delete vitals", "Delete vital record #" + selected.getVitalId() + "?")) {
            return;
        }
        int vitalId = selected.getVitalId();
        try {
            VitalDAO.delete(vitalId);
            refresh();
            clearForm();
            setStatus("Vital record deleted.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the vitals");
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
            table.getItems().setAll(VitalDAO.findAll(txtSearch.getText()));
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load vitals: " + e.getMessage(), false);
        }
    }

    private void clearForm() {
        selected = null;
        cmbPatient.getSelectionModel().clearSelection();
        txtBp.clear();
        txtTemp.clear();
        txtPulse.clear();
        txtNotes.clear();
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
