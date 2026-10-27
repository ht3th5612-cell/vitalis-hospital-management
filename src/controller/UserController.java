package controller;

import dao.DepartmentDAO;
import dao.DoctorDAO;
import dao.UserDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.Doctor;
import model.Department;
import model.User;
import util.AlertUtil;
import util.PasswordUtil;
import util.Session;
import util.UiFactory;
import util.Validators;
import util.Permissions;

public class UserController {

    private static final String NO_GENDER = "Not set";

    private final Department notAssigned = new Department(-1, "Not assigned", "");

    @FXML
    private TableView<User> table;
    @FXML
    private TableColumn<User, Integer> colId;
    @FXML
    private TableColumn<User, String> colUsername;
    @FXML
    private TableColumn<User, String> colFullName;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private TableColumn<User, String> colDepartment;
    @FXML
    private TableColumn<User, String> colPhone;
    @FXML
    private TableColumn<User, String> colSalary;
    @FXML
    private TableColumn<User, String> colStatus;

    @FXML
    private TextField txtSearch;
    @FXML
    private TextField txtUsername;
    @FXML
    private TextField txtPassword;
    @FXML
    private TextField txtFullName;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtSalary;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtNrc;
    @FXML
    private DatePicker dpBirth;
    @FXML
    private ComboBox<String> cmbGender;
    @FXML
    private VBox doctorBox;
    @FXML
    private TextField txtSpecialization;
    @FXML
    private TextField txtFee;
    @FXML
    private ComboBox<String> cmbRole;
    @FXML
    private ComboBox<Department> cmbDepartment;
    @FXML
    private CheckBox chkActive;
    @FXML
    private Label lblFormTitle;
    @FXML
    private Label lblStatus;

    private User selected;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getUserId()));
        colUsername.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getUsername()));
        colFullName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getFullName()));
        colRole.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getRoleLabel()));
        colDepartment.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDepartmentName()));
        colPhone.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPhone()));
        colSalary.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getSalaryText()));
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));

        if (Permissions.isDepartmentAdmin()) {
            cmbRole.getItems().addAll("DOCTOR", "NURSE");
            cmbRole.setValue("NURSE");
        } else {
            if (Permissions.has(Permissions.OWNER_ACCOUNTS)) {
                cmbRole.getItems().add("OWNER");
            }
            cmbRole.getItems().addAll("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST", "INVENTORY");
            cmbRole.setValue("RECEPTIONIST");
        }
        chkActive.setSelected(true);

        table.setPlaceholder(new Label("No staff accounts found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        cmbGender.getItems().setAll(NO_GENDER, "Male", "Female", "Other");
        cmbDepartment.setDisable(Permissions.isDepartmentAdmin());
        cmbRole.valueProperty().addListener((obs, oldValue, newValue) -> showDoctorFields());
        clearForm();
        loadDepartments();
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            String fullName = Validators.requireText(txtFullName.getText(), "Full name");
            String phone = Validators.optional(txtPhone.getText());
            double salary = Validators.optionalNonNegative(txtSalary.getText(), "Monthly salary");
            String role = Validators.requireText(cmbRole.getValue(), "Role");
            Department department = cmbDepartment.getValue();
            Integer departmentId = department == null || department.getDepartmentId() < 0
                    ? null : department.getDepartmentId();

            if (selected == null) {
                String username = Validators.requireText(txtUsername.getText(), "Username");
                String password = Validators.requirePassword(txtPassword.getText());
                User draft = new User();
                draft.setUsername(username);
                draft.setFullName(fullName);
                draft.setRole(role);
                draft.setPhone(phone);
                draft.setDepartmentId(departmentId);
                draft.setMonthlySalary(salary);
                readIdentity(draft);
                int newId = UserDAO.create(draft, password);
                if (isDoctorRole(role)) {
                    DoctorDAO.saveProfile(newId, departmentId, Validators.optional(txtSpecialization.getText()),
                            Validators.optionalNonNegative(txtFee.getText(), "Consultation fee"));
                }
                setStatus("Staff account created.", true);
            } else {
                User user = new User();
                user.setUserId(selected.getUserId());
                user.setFullName(fullName);
                user.setRole(role);
                user.setPhone(phone);
                user.setMonthlySalary(salary);
                readIdentity(user);
                user.setDepartmentId(departmentId);
                user.setActive(chkActive.isSelected());

                boolean resetPassword = !txtPassword.getText().isBlank();
                if (resetPassword) {
                    Validators.requirePassword(txtPassword.getText());
                }
                UserDAO.update(user);
                if (isDoctorRole(role)) {
                    DoctorDAO.saveProfile(selected.getUserId(), departmentId, Validators.optional(txtSpecialization.getText()),
                            Validators.optionalNonNegative(txtFee.getText(), "Consultation fee"));
                }
                if (resetPassword) {
                    UserDAO.updatePassword(selected.getUserId(), txtPassword.getText());
                }

                if (Session.getUser() != null && Session.getUser().getUserId() == selected.getUserId()) {
                    Session.getUser().setFullName(fullName);
                    Session.getUser().setPhone(phone);
                }
                setStatus("Staff account updated.", true);
            }
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the account");
        }
    }

    @FXML
    private void handleDismiss() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a staff account from the table first.");
            return;
        }
        if (Session.getUser() != null && selected.getUserId() == Session.getUser().getUserId()) {
            AlertUtil.warning("Not allowed", "You cannot dismiss the account you are signed in with.");
            return;
        }
        if (!AlertUtil.confirm("Dismiss staff member", "Dismiss " + selected.getFullName() + " (" + selected.getRoleLabel()
                + ")? The account becomes inactive and the salary is not counted any more.")) {
            return;
        }
        try {
            User user = selected.copy();
            user.setActive(false);
            UserDAO.update(user);
            refresh();
            clearForm();
            setStatus("Staff member dismissed.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the account");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a staff account from the table first.");
            return;
        }
        if (Session.getUser() != null && selected.getUserId() == Session.getUser().getUserId()) {
            AlertUtil.warning("Not allowed", "You cannot delete the account you are signed in with.");
            return;
        }
        if (!AlertUtil.confirm("Delete staff account",
                "Delete " + selected.getFullName() + " (" + selected.getRoleLabel() + ")? Only accounts with no history can be deleted; otherwise deactivate the account instead.")) {
            return;
        }
        try {
            UserDAO.delete(selected.getUserId());
            refresh();
            clearForm();
            setStatus("Staff account deleted.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the account");
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        loadDepartments();
        refresh();
    }

    private void loadDepartments() {
        try {
            Department chosen = cmbDepartment.getValue();
            cmbDepartment.getItems().setAll(notAssigned);
            cmbDepartment.getItems().addAll(DepartmentDAO.findAll());
            cmbDepartment.setValue(defaultDepartment());
            if (chosen != null) {
                cmbDepartment.getItems().stream()
                        .filter(d -> d.getDepartmentId() == chosen.getDepartmentId())
                        .findFirst().ifPresent(cmbDepartment::setValue);
            }
        } catch (RuntimeException e) {
            setStatus("Could not load departments: " + e.getMessage(), false);
        }
    }

    private void refresh() {
        try {
            table.getItems().setAll(UserDAO.findAll(txtSearch.getText()));
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load staff accounts: " + e.getMessage(), false);
        }
    }

    private void edit(User user) {
        selected = user;
        boolean isSelf = Session.getUser() != null && Session.getUser().getUserId() == user.getUserId();

        txtUsername.setText(user.getUsername());
        txtUsername.setDisable(true);
        txtPassword.clear();
        txtPassword.setPromptText("New password (leave blank to keep)");
        txtFullName.setText(user.getFullName());
        txtPhone.setText(user.getPhone());
        txtSalary.setText(String.format("%.2f", user.getMonthlySalary()));
        txtEmail.setText(user.getEmail());
        txtNrc.setText(user.getNrc());
        dpBirth.setValue(user.getDateOfBirth());
        cmbGender.setValue(user.getGender() == null ? NO_GENDER : user.getGender());
        if (!cmbRole.getItems().contains(user.getRole())) {
            cmbRole.getItems().add(0, user.getRole());
        }
        cmbRole.setValue(user.getRole());
        cmbRole.setDisable(isSelf);
        chkActive.setSelected(user.isActive());
        chkActive.setDisable(isSelf);

        if (user.getDepartmentId() == null) {
            cmbDepartment.setValue(notAssigned);
        } else {
            cmbDepartment.getItems().stream()
                    .filter(d -> d.getDepartmentId() == user.getDepartmentId())
                    .findFirst().ifPresent(cmbDepartment::setValue);
        }
        txtSpecialization.clear();
        txtFee.setText("0");
        if (isDoctorRole(user.getRole())) {
            DoctorDAO.findAll(user.getUsername()).stream().filter(d -> d.getUserId() == user.getUserId()).findFirst()
                    .ifPresent(this::fillDoctor);
        }
        showDoctorFields();
        lblFormTitle.setText("Edit staff account #" + user.getUserId());
        setStatus("", true);
    }

    private void clearForm() {
        selected = null;
        txtUsername.clear();
        txtUsername.setDisable(false);
        txtPassword.clear();
        txtPassword.setPromptText("Password (minimum " + PasswordUtil.MIN_LENGTH + " characters)");
        txtFullName.clear();
        txtPhone.clear();
        txtSalary.setText("0");
        txtEmail.clear();
        txtNrc.clear();
        dpBirth.setValue(null);
        cmbGender.setValue(NO_GENDER);
        txtSpecialization.clear();
        txtFee.setText("0");
        cmbRole.setValue(Permissions.isDepartmentAdmin() ? "NURSE" : "RECEPTIONIST");
        cmbRole.setDisable(false);
        chkActive.setSelected(true);
        chkActive.setDisable(false);
        cmbDepartment.setValue(defaultDepartment());
        showDoctorFields();
        lblFormTitle.setText("New staff account");
    }

    private void readIdentity(User user) {
        user.setDateOfBirth(dpBirth.getValue());
        user.setGender(NO_GENDER.equals(cmbGender.getValue()) ? null : cmbGender.getValue());
        user.setNrc(Validators.optional(txtNrc.getText()));
        user.setEmail(Validators.optionalEmail(txtEmail.getText()));
    }

    private static boolean isDoctorRole(String role) {
        return "DOCTOR".equals(role) || "ADMIN".equals(role);
    }

    private void fillDoctor(Doctor doctor) {
        txtSpecialization.setText(doctor.getSpecialization());
        txtFee.setText(String.format("%.2f", doctor.getConsultationFee()));
    }

    private void showDoctorFields() {
        boolean doctor = isDoctorRole(cmbRole.getValue());
        doctorBox.setVisible(doctor);
        doctorBox.setManaged(doctor);
    }

    private Department defaultDepartment() {
        if (Permissions.isDepartmentAdmin() && Session.getUser().getDepartmentId() != null) {
            return cmbDepartment.getItems().stream()
                    .filter(d -> d.getDepartmentId() == Session.getUser().getDepartmentId())
                    .findFirst().orElse(notAssigned);
        }
        return notAssigned;
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
