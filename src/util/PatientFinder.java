package util;

import dao.PatientDAO;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.Patient;

public final class PatientFinder {

    private PatientFinder() {
    }

    public static Patient choose() {
        TextField txtName = new TextField();
        txtName.setPromptText("Name (a part is enough)");
        DatePicker dpBirth = new DatePicker();
        dpBirth.setPromptText("Date of birth");
        TextField txtNrc = new TextField();
        txtNrc.setPromptText("NRC number");
        ComboBox<String> cmbGender = new ComboBox<>();
        cmbGender.getItems().setAll("Any gender", "Male", "Female", "Other");
        cmbGender.getSelectionModel().selectFirst();

        TableView<Patient> table = new TableView<>();
        table.setPrefHeight(230);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Name", 150, p -> p.getName()));
        table.getColumns().add(column("Date of birth", 100, p -> p.getDateOfBirth() == null ? "" : Formats.date(p.getDateOfBirth())));
        table.getColumns().add(column("Gender", 70, p -> p.getGender()));
        table.getColumns().add(column("NRC", 130, p -> p.getNrc() == null ? "" : p.getNrc()));
        table.getColumns().add(column("Phone", 110, p -> p.getPhone()));

        Label lblHint = new Label("Type a name, a date of birth or an NRC number. The gender narrows the result.");
        lblHint.setWrapText(true);

        Runnable search = () -> {
            try {
                String gender = "Any gender".equals(cmbGender.getValue()) ? null : cmbGender.getValue();
                var found = PatientDAO.search(txtName.getText(), dpBirth.getValue(), txtNrc.getText(), gender);
                table.getItems().setAll(found);
                boolean typed = !txtName.getText().isBlank() || dpBirth.getValue() != null || !txtNrc.getText().isBlank();
                lblHint.setText(!typed ? "Type a name, a date of birth or an NRC number. The gender narrows the result."
                        : found.isEmpty() ? "Nobody found. Check the spelling, or register the patient at the Patients screen."
                        : found.size() + " found" + (found.size() == PatientDAO.SEARCH_LIMIT ? " (the first "
                        + PatientDAO.SEARCH_LIMIT + "): type more to narrow it." : ". Select the patient."));
            } catch (RuntimeException e) {
                lblHint.setText("Could not search: " + e.getMessage());
            }
        };
        txtName.textProperty().addListener((obs, oldValue, newValue) -> search.run());
        txtNrc.textProperty().addListener((obs, oldValue, newValue) -> search.run());
        dpBirth.valueProperty().addListener((obs, oldValue, newValue) -> search.run());
        cmbGender.valueProperty().addListener((obs, oldValue, newValue) -> search.run());

        HBox row = new HBox(8, txtName, dpBirth, txtNrc, cmbGender);
        txtName.setPrefWidth(190);
        dpBirth.setPrefWidth(140);
        txtNrc.setPrefWidth(170);
        VBox content = new VBox(10, row, lblHint, table);
        content.setPadding(new Insets(8));
        content.setPrefWidth(720);

        ButtonType select = new ButtonType("Select patient", ButtonBar.ButtonData.OK_DONE);
        Dialog<Patient> dialog = new Dialog<>();
        dialog.setTitle("Find a patient");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(select, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(select).disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(button -> button == select ? table.getSelectionModel().getSelectedItem() : null);
        return dialog.showAndWait().orElse(null);
    }

    private static TableColumn<Patient, String> column(String title, double width,
                                                       java.util.function.Function<Patient, String> value) {
        TableColumn<Patient, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cd -> new ReadOnlyStringWrapper(value.apply(cd.getValue())));
        return column;
    }
}
