package controller;

import dao.UserDAO;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import util.AvatarView;
import util.ImageUtil;
import model.User;
import util.AlertUtil;
import util.Session;
import util.UiFactory;
import util.Validators;

public class ProfileController {

    private static final String NO_GENDER = "Not set";

    @FXML
    private StackPane photoBox;
    @FXML
    private Label lblPhotoStatus;
    @FXML
    private Label lblAccount;
    @FXML
    private TextField txtFullName;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtEmail;
    @FXML
    private DatePicker dpBirth;
    @FXML
    private ComboBox<String> cmbGender;
    @FXML
    private PasswordField txtProfilePassword;
    @FXML
    private Label lblProfileStatus;
    @FXML
    private PasswordField txtOldPassword;
    @FXML
    private PasswordField txtNewPassword;
    @FXML
    private PasswordField txtConfirmPassword;
    @FXML
    private Label lblPasswordStatus;

    @FXML
    private void initialize() {
        cmbGender.getItems().setAll(NO_GENDER, "Male", "Female", "Other");
        load();
    }

    @FXML
    private void handleSaveProfile() {
        try {
            User changes = new User();
            changes.setFullName(Validators.requireText(txtFullName.getText(), "Name"));
            changes.setPhone(Validators.optional(txtPhone.getText()));
            changes.setEmail(Validators.optionalEmail(txtEmail.getText()));
            changes.setDateOfBirth(dpBirth.getValue());
            changes.setGender(NO_GENDER.equals(cmbGender.getValue()) ? null : cmbGender.getValue());
            UserDAO.updateOwnProfile(changes, txtProfilePassword.getText());
            txtProfilePassword.clear();
            load();
            Session.notifyUserChanged();
            UiFactory.setStatus(lblProfileStatus, "Your details were saved.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check your details");
            txtProfilePassword.clear();
        }
    }

    @FXML
    private void handleChangePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose your picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        java.io.File file = chooser.showOpenDialog(photoBox.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            UserDAO.updateOwnPhoto(ImageUtil.toAvatarPng(file));
            load();
            Session.notifyUserChanged();
            UiFactory.setStatus(lblPhotoStatus, "Your picture was saved.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the picture");
        }
    }

    @FXML
    private void handleRemovePhoto() {
        try {
            UserDAO.updateOwnPhoto(null);
            load();
            Session.notifyUserChanged();
            UiFactory.setStatus(lblPhotoStatus, "Your picture was removed.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the picture");
        }
    }

    @FXML
    private void handleChangePassword() {
        try {
            if (!txtNewPassword.getText().equals(txtConfirmPassword.getText())) {
                throw new IllegalArgumentException("The two new passwords are not the same.");
            }
            UserDAO.changeOwnPassword(txtOldPassword.getText(), txtNewPassword.getText());
            txtOldPassword.clear();
            txtNewPassword.clear();
            txtConfirmPassword.clear();
            UiFactory.setStatus(lblPasswordStatus, "Your password was changed. Use it the next time you sign in.", true);
        } catch (RuntimeException e) {
            AlertUtil.failure(e, "Check the password");
            txtOldPassword.clear();
        }
    }

    private void load() {
        User me = Session.getUser() == null ? null : UserDAO.findById(Session.getUser().getUserId());
        if (me == null) {
            return;
        }
        lblAccount.setText("Username: " + me.getUsername() + "    |    " + me.getRoleLabel()
                + (me.getDepartmentName() == null ? "" : "    |    " + me.getDepartmentName())
                + (me.getNrc() == null ? "" : "    |    NRC " + me.getNrc()));
        photoBox.getChildren().setAll(AvatarView.create(88, me.getFullName(), UserDAO.findPhoto(me.getUserId())));
        txtFullName.setText(me.getFullName());
        txtPhone.setText(me.getPhone());
        txtEmail.setText(me.getEmail());
        dpBirth.setValue(me.getDateOfBirth());
        cmbGender.setValue(me.getGender() == null ? NO_GENDER : me.getGender());
    }
}
