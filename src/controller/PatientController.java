package controller;

import dao.PatientDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.Patient;
import util.AlertUtil;
import util.UiFactory;
import util.Validators;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import util.Permissions;

public class PatientController {

    @FXML
    private TableView<Patient> table;
    @FXML
    private TableColumn<Patient, Integer> colId;
    @FXML
    private TableColumn<Patient, String> colName;
    @FXML
    private TableColumn<Patient, String> colBirth;
    @FXML
    private TableColumn<Patient, Integer> colAge;
    @FXML
    private TableColumn<Patient, String> colNrc;
    @FXML
    private TableColumn<Patient, String> colGender;
    @FXML
    private TableColumn<Patient, String> colPhone;
    @FXML
    private TableColumn<Patient, String> colBlood;

    @FXML
    private TextField txtSearch;
    @FXML
    private VBox formCard;
    @FXML
    private Button btnDelete;
    @FXML
    private TextField txtName;
    @FXML
    private DatePicker dpBirth;
    @FXML
    private TextField txtNrc;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtAddress;
    @FXML
    private ComboBox<String> cmbGender;
    @FXML
    private ComboBox<String> cmbBlood;
    @FXML
    private Label lblFormTitle;
    @FXML
    private Label lblStatus;

    private Patient selected;

    @FXML
    private void initialize() {
        cmbGender.getItems().setAll("Male", "Female", "Other");
        cmbBlood.getItems().setAll("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown");
        cmbGender.getSelectionModel().selectFirst();
        cmbBlood.getSelectionModel().selectFirst();

        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getPatientId()));
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getName()));
        colBirth.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getDateOfBirth() == null ? "" : util.Formats.date(cd.getValue().getDateOfBirth())));
        colNrc.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNrc()));
        colAge.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getAge()));
        colGender.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGender()));
        colPhone.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPhone()));
        colBlood.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBloodGroup()));

        table.setPlaceholder(new Label("No patients found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });

        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        formCard.setVisible(Permissions.has(Permissions.PATIENT_EDIT));
        formCard.setManaged(Permissions.has(Permissions.PATIENT_EDIT));
        btnDelete.setDisable(!Permissions.has(Permissions.PATIENT_DELETE));
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            Patient patient = new Patient();
            if (selected != null) {
                patient.setPatientId(selected.getPatientId());
            }
            patient.setName(Validators.requireText(txtName.getText(), "Full name"));
            patient.setDateOfBirth(dpBirth.getValue());
            patient.setNrc(Validators.optional(txtNrc.getText()));
            patient.setEmail(Validators.optionalEmail(txtEmail.getText()));
            patient.setGender(requiredSelection(cmbGender, "Gender"));
            patient.setPhone(Validators.requireText(txtPhone.getText(), "Phone"));
            patient.setAddress(Validators.optional(txtAddress.getText()));
            patient.setBloodGroup(requiredSelection(cmbBlood, "Blood group"));

            if (selected == null) {
                PatientDAO.create(patient);
                setStatus("Patient registered successfully.", true);
            } else {
                PatientDAO.update(patient);
                setStatus("Patient details updated.", true);
            }
            refresh();
            clearForm(false);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the form");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected",
                    "Select a patient from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Remove patient",
                "Remove " + selected.getName() + " from the active patient list? Their appointments, medical records and bills are kept.")) {
            return;
        }
        try {
            PatientDAO.delete(selected.getPatientId());
            refresh();
            clearForm(true);
            setStatus("Patient removed from the active list.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the form");
        }
    }

    @FXML
    private void handleClear() {
        clearForm(true);
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void edit(Patient patient) {
        selected = patient;
        txtName.setText(patient.getName());
        dpBirth.setValue(patient.getDateOfBirth());
        txtNrc.setText(patient.getNrc());
        txtEmail.setText(patient.getEmail());
        txtPhone.setText(patient.getPhone());
        txtAddress.setText(patient.getAddress());
        cmbGender.setValue(patient.getGender());
        cmbBlood.setValue(patient.getBloodGroup());
        lblFormTitle.setText("Edit patient P-" + (String.format("%04d", patient.getPatientId())));
        setStatus("", true);
    }

    private void clearForm(boolean resetTitle) {
        selected = null;
        txtName.clear();
        dpBirth.setValue(null);
        txtNrc.clear();
        txtEmail.clear();
        txtPhone.clear();
        txtAddress.clear();
        cmbGender.getSelectionModel().selectFirst();
        cmbBlood.getSelectionModel().selectFirst();
        if (resetTitle) {
            lblFormTitle.setText("New patient");
        }
    }

    private void refresh() {
        try {
            table.getItems().setAll(PatientDAO.findAll(txtSearch.getText()));
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load patients: " + e.getMessage(), false);
        }
    }

    private String requiredSelection(ComboBox<String> combo, String field) {
        String value = combo.getValue();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
