package controller;

import dao.AppointmentDAO;
import dao.AppointmentDAO.InHospital;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import util.Formats;

public class CurrentPatientsController {

    @FXML
    private TableView<InHospital> table;
    @FXML
    private TableColumn<InHospital, String> colPatient;
    @FXML
    private TableColumn<InHospital, String> colDoctor;
    @FXML
    private TableColumn<InHospital, String> colStatus;
    @FXML
    private TableColumn<InHospital, String> colRoom;
    @FXML
    private TableColumn<InHospital, String> colSince;
    @FXML
    private TableColumn<InHospital, String> colVitals;
    @FXML
    private TableColumn<InHospital, String> colDiagnosis;
    @FXML
    private Label lblCount;

    @FXML
    private void initialize() {
        colPatient.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().patient()));
        colDoctor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().doctor()));
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().status()));
        colRoom.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().room()));
        colSince.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.dateTime(cd.getValue().since())));
        colVitals.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().vitals()));
        colDiagnosis.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().diagnosis()));
        table.setPlaceholder(new Label("Nobody is in the hospital at the moment."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            table.getItems().setAll(AppointmentDAO.findInHospital());
            table.refresh();
            lblCount.setText(table.getItems().size() + " patient(s)");
        } catch (RuntimeException e) {
            lblCount.setText("Could not load: " + e.getMessage());
        }
    }
}
