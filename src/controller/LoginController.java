package controller;

import database.DatabaseException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.User;
import util.AlertUtil;
import util.AppNavigator;
import util.ForgotPasswordDialog;
import util.Session;
import util.Validators;
import dao.UserDAO;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private Label lblError;

    @FXML
    private void initialize() {
        hideError();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        hideError();
        String username;
        String password;
        try {
            username = Validators.requireText(txtUsername.getText(), "Username");
            password = Validators.requireText(txtPassword.getText(), "Password");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }

        try {
            User user = UserDAO.authenticate(username, password);
            if (user == null) {
                showError("Invalid username or password.");
                txtPassword.clear();
                txtPassword.requestFocus();
                return;
            }

            Session.setUser(user);
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            AppNavigator.showMain(stage);
        } catch (DatabaseException e) {
            showError("Cannot reach the database. Start MySQL in XAMPP and import sql/hospital_db.sql.");
            AlertUtil.error("Database error", e.getMessage());
        } catch (RuntimeException e) {
            showError("Unexpected error: " + e.getMessage());
        }
    }

    @FXML
    private void handleForgotPassword() {
        ForgotPasswordDialog.show(txtUsername.getScene().getWindow(), txtUsername.getText());
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
