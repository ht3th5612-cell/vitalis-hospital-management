package controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import dao.MessageDAO;
import javafx.util.Duration;
import dao.UserDAO;
import util.AvatarView;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import util.IconFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.User;
import util.AlertUtil;
import util.AppNavigator;
import util.Session;
import util.UiFactory;

import java.io.IOException;
import java.util.List;
import util.Permissions;

public class MainController {

    private record NavItem(String group, String title, String fxml, String permission) {
    }

    private static final List<String> INSIDE_RECORDS = List.of("/view/patients.fxml", "/view/billing.fxml",
            "/view/medical-records.fxml", "/view/vitals.fxml");

    private static final List<NavItem> NAV_ITEMS = List.of(
            new NavItem("OVERVIEW", "Dashboard", "/view/dashboard.fxml", null),
            new NavItem("OVERVIEW", "Records", "/view/records.fxml", Permissions.RECORDS_OVERVIEW),
            new NavItem("OVERVIEW", "Messages", "/view/messages.fxml", null),

            new NavItem("FRONT DESK", "Patients", "/view/patients.fxml", Permissions.PATIENT_VIEW),
            new NavItem("FRONT DESK", "Appointments", "/view/appointments.fxml", Permissions.APPOINTMENT_VIEW),
            new NavItem("FRONT DESK", "Checkout", "/view/billing.fxml", Permissions.BILL_VIEW),

            new NavItem("CLINICAL", "Consultation", "/view/consultation.fxml", Permissions.RECORD_WRITE),
            new NavItem("CLINICAL", "Medical Records", "/view/medical-records.fxml", Permissions.RECORD_VIEW),
            new NavItem("CLINICAL", "Patient Vitals", "/view/vitals.fxml", Permissions.VITAL_VIEW),

            new NavItem("ADMINISTRATION", "Departments", "/view/departments.fxml", Permissions.DEPARTMENT_MANAGE),
            new NavItem("ADMINISTRATION", "Staff", "/view/users.fxml", Permissions.STAFF_MANAGE),
            new NavItem("ADMINISTRATION", "Rooms", "/view/rooms.fxml", Permissions.ROOM_MANAGE),
            new NavItem("ADMINISTRATION", "Inventory", "/view/inventory.fxml", Permissions.INVENTORY_VIEW),
            new NavItem("ADMINISTRATION", "Reports", "/view/reports.fxml", Permissions.REPORT_VIEW)
    );

    private static final NavItem PROFILE = new NavItem("ACCOUNT", "My profile", "/view/profile.fxml", null);

    @FXML
    private VBox navBox;

    @FXML
    private StackPane contentArea;

    @FXML
    private Label lblUserName;

    @FXML
    private Label lblUserRole;

    @FXML
    private StackPane avatarBox;

    private Button activeButton;
    private Button messagesButton;
    private final StackPane messagesBadge = new StackPane();
    private final Timeline badgeTimer = new Timeline();

    @FXML
    private void initialize() {
        User user = Session.getUser();
        if (user == null) {
            return;
        }

        showUserCard();
        Session.setOnUserChanged(this::showUserCard);
        badgeTimer.getKeyFrames().setAll(new KeyFrame(Duration.seconds(4), e -> showUnreadBadge()));
        badgeTimer.setCycleCount(Timeline.INDEFINITE);

        Button first = buildNavigation();
        if (first == null) {
            AlertUtil.error("Access denied", "This account has no modules assigned.");
            signOut();
            return;
        }
        first.fire();
        showUnreadBadge();
        badgeTimer.play();
    }

    private Button buildNavigation() {
        Button firstNameButton = null;
        String currentGroup = null;

        for (NavItem item : NAV_ITEMS) {
            if (item.permission() != null && !Permissions.has(item.permission())) {
                continue;
            }
            if (INSIDE_RECORDS.contains(item.fxml()) && Permissions.has(Permissions.RECORDS_OVERVIEW)) {
                continue;
            }

            if (!item.group().equals(currentGroup)) {
                Label groupLabel = new Label(item.group());
                groupLabel.getStyleClass().add("nav-section");
                navBox.getChildren().add(groupLabel);
                currentGroup = item.group();
            }

            Button button = new Button(item.title());
            button.getStyleClass().add("nav-button");
            button.setAlignment(Pos.CENTER_LEFT);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setGraphic(navRow(item, button));
            button.setOnAction(e -> showModule(item, button));
            navBox.getChildren().add(button);

            if (firstNameButton == null) {
                firstNameButton = button;
            }
        }
        return firstNameButton;
    }

    private void showModule(NavItem item, Button button) {
        try {
            Parent root = AppNavigator.load(item.fxml());
            contentArea.getChildren().setAll(scrollable(root));

            if (activeButton != null) {
                activeButton.getStyleClass().remove("nav-button-active");
            }
            activeButton = button;
            if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
                activeButton.getStyleClass().add("nav-button-active");
            }
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.error("Page error", "Could not open " + item.title() + ".\n\n" + e.getMessage());
        }
    }

    private static ScrollPane scrollable(Parent screen) {
        if (screen instanceof Region region) {
            region.setMinHeight(Region.USE_PREF_SIZE);
        }
        ScrollPane scroll = new ScrollPane(screen);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("form-scroll");
        return scroll;
    }

    private void showUserCard() {
        User user = Session.getUser();
        if (user == null) {
            return;
        }
        lblUserName.setText(user.getFullName());
        String department = user.getDepartmentName() == null ? "" : " - " + user.getDepartmentName();
        lblUserRole.setText(UiFactory.roleTitle(user.getRole()) + department);
        byte[] photo = null;
        try {
            photo = UserDAO.findPhoto(user.getUserId());
        } catch (RuntimeException e) {
        }
        avatarBox.getChildren().setAll(AvatarView.create(38, user.getFullName(), photo));
    }

    @FXML
    private void handleOpenProfile() {
        showModule(PROFILE, null);
    }

    @FXML
    private void handleLogout() {
        if (!AlertUtil.confirm("Sign out", "Do you want to sign out of the system?")) {
            return;
        }
        signOut();
    }

    private HBox navRow(NavItem item, Button button) {
        Label text = new Label(item.title());
        text.getStyleClass().add("nav-txt");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(12, IconFactory.nav(item.fxml()), text, grow);
        row.setAlignment(Pos.CENTER_LEFT);
        if ("/view/messages.fxml".equals(item.fxml())) {
            messagesButton = button;
            row.getChildren().add(messagesBadge);
        }
        return row;
    }

    private void showUnreadBadge() {
        if (Session.getUser() == null || messagesButton == null) {
            badgeTimer.stop();
            return;
        }
        try {
            int unread = MessageDAO.countUnread();
            if (unread == 0) {
                messagesBadge.getChildren().clear();
            } else {
                Label badge = new Label(unread > 99 ? "99+" : String.valueOf(unread));
                badge.getStyleClass().add("nav-badge");
                messagesBadge.getChildren().setAll(badge);
            }
        } catch (RuntimeException e) {
            messagesBadge.getChildren().clear();
        }
    }

    private void signOut() {
        badgeTimer.stop();
        Session.clear();
        Stage stage = (Stage) contentArea.getScene().getWindow();
        AppNavigator.showLogin(stage);
    }

}
