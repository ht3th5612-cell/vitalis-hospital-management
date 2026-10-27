package controller;

import dao.AppointmentDAO;
import dao.DoctorDAO;
import dao.PatientDAO;
import dao.RoomDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.Appointment;
import model.Doctor;
import model.InventoryItem;
import model.Patient;
import util.AlertUtil;
import util.Formats;
import util.Permissions;
import util.Session;
import util.PatientFinder;
import util.UiFactory;
import util.Validators;

import java.time.LocalDate;

public class AppointmentController {

    @FXML
    private TableView<Appointment> table;
    @FXML
    private TableColumn<Appointment, Integer> colId;
    @FXML
    private TableColumn<Appointment, String> colPatient;
    @FXML
    private TableColumn<Appointment, String> colDoctor;
    @FXML
    private TableColumn<Appointment, String> colDate;
    @FXML
    private TableColumn<Appointment, String> colTime;
    @FXML
    private TableColumn<Appointment, String> colType;
    @FXML
    private TableColumn<Appointment, String> colRoom;
    @FXML
    private TableColumn<Appointment, String> colStatus;

    @FXML
    private TextField txtSearch;
    @FXML
    private ScrollPane formCard;
    @FXML
    private Label lblPatient;
    @FXML
    private ComboBox<Doctor> cmbDoctor;
    @FXML
    private DatePicker dpDate;
    @FXML
    private TextField txtTime;
    @FXML
    private TextField txtReason;
    @FXML
    private ComboBox<String> cmbStatus;
    @FXML
    private ComboBox<String> cmbRoom;
    @FXML
    private Label lblRooms;
    @FXML
    private Button btnBook;
    @FXML
    private Button btnWalkIn;
    @FXML
    private Button btnUpdateStatus;
    @FXML
    private Button btnAdmit;
    @FXML
    private Button btnDischarge;
    @FXML
    private Button btnDelete;
    @FXML
    private Label lblStatus;

    private Appointment selected;
    private Patient chosenPatient;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getAppointmentId()));
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPatientName()));
        colDoctor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDoctorName()));
        colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.date(cd.getValue().getAppointmentDate())));
        colTime.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.time(cd.getValue().getAppointmentTime())));
        colType.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getVisitType()));
        colRoom.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getRoom()));
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));

        cmbStatus.getItems().setAll("Scheduled", "Completed", "Cancelled", "No-Show");
        cmbStatus.getSelectionModel().selectFirst();
        dpDate.setValue(LocalDate.now());

        table.setPlaceholder(new Label("No appointments found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        applyPermissions();
        loadChoices();
        refresh();
    }

    private void applyPermissions() {
        boolean canBook = Permissions.has(Permissions.APPOINTMENT_BOOK);
        boolean canUpdate = Permissions.has(Permissions.APPOINTMENT_UPDATE);
        formCard.setVisible(canBook || canUpdate);
        formCard.setManaged(canBook || canUpdate);
        btnBook.setDisable(!canBook);
        btnWalkIn.setDisable(!canBook);
        btnDelete.setDisable(!canBook);
        btnUpdateStatus.setDisable(!canUpdate);
        btnAdmit.setDisable(!canUpdate);
        btnDischarge.setDisable(!canUpdate);
    }

    @FXML
    private void handleBook() {
        try {
            Patient patient = chosenPatient;
            Doctor doctor = cmbDoctor.getValue();
            if (patient == null) {
                throw new IllegalArgumentException("Press Find patient and choose the patient (register a new one at the Patients screen).");
            }
            if (doctor == null) {
                throw new IllegalArgumentException("Select a doctor.");
            }
            if (dpDate.getValue() == null) {
                throw new IllegalArgumentException("Select an appointment date.");
            }

            Appointment appointment = new Appointment();
            appointment.setPatientId(patient.getPatientId());
            appointment.setDoctorId(doctor.getUserId());
            appointment.setAppointmentDate(dpDate.getValue());
            appointment.setAppointmentTime(Formats.parseTime(txtTime.getText()));
            appointment.setReason(Validators.optional(txtReason.getText()));
            appointment.setStatus(cmbStatus.getValue());

            AppointmentDAO.create(appointment);
            setStatus("Appointment booked for " + patient.getName() + ".", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the appointment");
        }
    }

    @FXML
    private void handleWalkIn() {
        try {
            Patient patient = chosenPatient;
            Doctor doctor = cmbDoctor.getValue();
            if (patient == null) {
                throw new IllegalArgumentException("Press Find patient and choose the patient (register a new one at the Patients screen).");
            }
            if (doctor == null) {
                throw new IllegalArgumentException("Select a doctor.");
            }
            AppointmentDAO.startWalkIn(patient.getPatientId(), doctor.getUserId(), Validators.optional(txtReason.getText()));
            setStatus(patient.getName() + " is waiting for " + doctor.getFullName() + " now.", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the visit");
        }
    }

    @FXML
    private void handleFindPatient() {
        Patient found = PatientFinder.choose();
        if (found != null) {
            setPatient(found);
        }
    }

    private void setPatient(Patient patient) {
        chosenPatient = patient;
        lblPatient.setText(patient == null ? "No patient chosen" : patient.getDisplayName()
                + (patient.getNrc() == null ? "" : ", NRC " + patient.getNrc()));
    }

    @FXML
    private void handleUpdateStatus() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select an appointment from the table first.");
            return;
        }
        try {
            AppointmentDAO.updateStatus(selected.getAppointmentId(), cmbStatus.getValue());
            setStatus("Appointment status changed to " + cmbStatus.getValue() + ".", true);
            refresh();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the appointment");
        }
    }

    @FXML
    private void handleAdmit() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a scheduled appointment from the table first.");
            return;
        }
        if (!"Scheduled".equals(selected.getStatus())) {
            AlertUtil.warning("Check the admission", "Appointment #" + selected.getAppointmentId() + " of "
                    + selected.getPatientName() + " is " + selected.getStatus()
                    + ", not Scheduled. Select the row whose Status is Scheduled.");
            return;
        }
        try {
            String room = Validators.requireText(cmbRoom.getValue(), "Room / bed");
            AppointmentDAO.admit(selected.getAppointmentId(), room);
            setStatus(selected.getPatientName() + " admitted to " + room + ".", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the admission");
        }
    }

    @FXML
    private void handleDischarge() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select an admitted patient from the table first.");
            return;
        }
        if (!"Admitted".equals(selected.getStatus())) {
            AlertUtil.warning("Check the discharge", "Appointment #" + selected.getAppointmentId() + " of "
                    + selected.getPatientName() + " is " + selected.getStatus()
                    + ", not Admitted. Select the row whose Status is Admitted.");
            return;
        }
        try {
            AppointmentDAO.discharge(selected.getAppointmentId());
            setStatus(selected.getPatientName() + " discharged. The stay can be billed now.", true);
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the discharge");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select an appointment from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Delete appointment", "Delete this appointment permanently?")) {
            return;
        }
        try {
            AppointmentDAO.delete(selected.getAppointmentId());
            refresh();
            clearForm();
            setStatus("Appointment deleted.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the appointment");
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        loadChoices();
        refresh();
    }

    private void loadChoices() {
        try {
            UiFactory.refillKeepingChoice(cmbDoctor, DoctorDAO.findActive(),
                    (a, b) -> a.getUserId() == b.getUserId());
        } catch (RuntimeException e) {
            setStatus("Could not load patients or doctors: " + e.getMessage(), false);
        }
    }

    private void refresh() {
        try {
            table.getItems().setAll(AppointmentDAO.findAll(txtSearch.getText(), null));
            table.refresh();
            loadRooms();
        } catch (RuntimeException e) {
            setStatus("Could not load appointments: " + e.getMessage(), false);
        }
    }

    private void loadRooms() {
        String chosen = cmbRoom.getValue();
        cmbRoom.getItems().setAll(RoomDAO.freeRoomNames());
        if (chosen != null) {
            cmbRoom.setValue(chosen);
        }
        int beds = RoomDAO.findAll().stream().mapToInt(InventoryItem::getStockQuantity).sum();
        int used = RoomDAO.occupiedBeds().values().stream().mapToInt(Integer::intValue).sum();
        var occupied = AppointmentDAO.findOccupiedRooms();
        lblRooms.setText("Free beds: " + (beds - used) + " of " + beds
                + (occupied.isEmpty() ? "." : ". In a room now: " + String.join(", ", occupied)));
    }

    private void edit(Appointment appointment) {
        selected = appointment;
        setPatient(PatientDAO.findById(appointment.getPatientId()));
        cmbDoctor.getItems().stream()
                .filter(d -> d.getUserId() == appointment.getDoctorId())
                .findFirst().ifPresent(cmbDoctor::setValue);
        dpDate.setValue(appointment.getAppointmentDate());
        txtTime.setText(Formats.time(appointment.getAppointmentTime()));
        txtReason.setText(appointment.getReason());
        cmbRoom.setValue(appointment.getRoom());
        cmbStatus.setValue(appointment.getStatus());
        setStatus("Editing appointment #" + appointment.getAppointmentId() + ".", true);
    }

    private void clearForm() {
        selected = null;
        setPatient(null);
        cmbDoctor.getSelectionModel().clearSelection();
        dpDate.setValue(LocalDate.now());
        txtTime.clear();
        txtReason.clear();
        cmbRoom.getSelectionModel().clearSelection();
        cmbStatus.getSelectionModel().selectFirst();
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
