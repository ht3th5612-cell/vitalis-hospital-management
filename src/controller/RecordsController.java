package controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import util.AlertUtil;
import util.AppNavigator;

import java.io.IOException;

public class RecordsController {

    @FXML
    private TabPane tabs;

    @FXML
    private void initialize() {
        addTab("Patient history", "/view/patient-history.fxml");
        addTab("In hospital now", "/view/current-patients.fxml");
        addTab("Bills", "/view/billing.fxml");
    }

    private void addTab(String title, String fxml) {
        try {
            Parent screen = AppNavigator.load(fxml);
            screen.getStyleClass().remove("page");
            Tab tab = new Tab(title, screen);
            tabs.getTabs().add(tab);
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.error("Page error", "Could not open " + title + ".\n\n" + e.getMessage());
        }
    }
}
