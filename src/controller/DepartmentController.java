package controller;

import dao.DepartmentDAO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import model.Department;
import util.AlertUtil;
import util.UiFactory;
import util.Validators;

public class DepartmentController {

    @FXML
    private TableView<Department> table;
    @FXML
    private TableColumn<Department, Integer> colId;
    @FXML
    private TableColumn<Department, String> colName;
    @FXML
    private TableColumn<Department, String> colDescription;

    @FXML
    private TextField txtName;
    @FXML
    private TextArea txtDescription;
    @FXML
    private Label lblFormTitle;
    @FXML
    private Label lblStatus;

    private Department selected;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getDepartmentId()));
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDepartmentName()));
        colDescription.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDescription()));

        table.setPlaceholder(new Label("No departments found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                edit(newValue);
            }
        });
        refresh();
    }

    @FXML
    private void handleSave() {
        try {
            String name = Validators.requireText(txtName.getText(), "Department name");
            String description = Validators.optional(txtDescription.getText());

            if (selected == null) {
                DepartmentDAO.create(name, description);
                setStatus("Department added.", true);
            } else {
                Department updated = new Department(selected.getDepartmentId(), name, description);
                DepartmentDAO.update(updated);
                setStatus("Department updated.", true);
            }
            refresh();
            clearForm();
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the department");
        }
    }

    @FXML
    private void handleDelete() {
        if (selected == null) {
            AlertUtil.warning("Nothing selected", "Select a department from the table first.");
            return;
        }
        if (!AlertUtil.confirm("Delete department",
                "Delete " + selected.getDepartmentName() + "? Staff linked to it will be unassigned.")) {
            return;
        }
        try {
            DepartmentDAO.delete(selected.getDepartmentId());
            refresh();
            clearForm();
            setStatus("Department deleted.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the department");
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void edit(Department department) {
        selected = department;
        txtName.setText(department.getDepartmentName());
        txtDescription.setText(department.getDescription());
        lblFormTitle.setText("Edit department #" + department.getDepartmentId());
        setStatus("", true);
    }

    private void refresh() {
        try {
            table.getItems().setAll(DepartmentDAO.findAll());
            table.refresh();
        } catch (RuntimeException e) {
            setStatus("Could not load departments: " + e.getMessage(), false);
        }
    }

    private void clearForm() {
        selected = null;
        txtName.clear();
        txtDescription.clear();
        lblFormTitle.setText("New department");
    }

    private void setStatus(String message, boolean success) {
        UiFactory.setStatus(lblStatus, message, success);
    }
}
