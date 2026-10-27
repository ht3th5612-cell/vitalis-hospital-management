package util;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.Instant;

public final class ForgotPasswordDialog {

    private static final int LEN = OtpService.CODE_LENGTH;

    private final Stage stage = new Stage();
    private final Scene scene;

    private final VBox emailPane = new VBox(14);
    private final TextField txtEmail = new TextField();
    private final Label lblEmailError = new Label();
    private final Button btnSend = new Button("Send code");

    private final VBox otpPane = new VBox(14);
    private final Label lblSentTo = new Label();
    private final TextField[] boxes = new TextField[LEN];
    private final HBox boxRow = new HBox(8);
    private final Label lblMessage = new Label();
    private final Label lblTimer = new Label();
    private final Button btnConfirm = new Button("Confirm");
    private final Hyperlink linkResend = new Hyperlink("Resend");
    private final Timeline countdown = new Timeline();
    private Instant deadline = Instant.now();

    private final VBox passwordPane = new VBox(14);
    private final PasswordField txtNew = new PasswordField();
    private final PasswordField txtConfirm = new PasswordField();
    private final Label lblPasswordError = new Label();
    private final Button btnChange = new Button("Change password");

    private String email = "";
    private String token;

    public static void show(Window owner, String textSoFar) {
        ForgotPasswordDialog dialog = new ForgotPasswordDialog(textSoFar);
        dialog.stage.initOwner(owner);
        dialog.stage.initModality(Modality.WINDOW_MODAL);
        dialog.stage.showAndWait();
    }

    private ForgotPasswordDialog(String textSoFar) {
        buildEmailPane(textSoFar);
        buildOtpPane();
        buildPasswordPane();

        StackPane panes = new StackPane(emailPane, otpPane, passwordPane);
        panes.getStyleClass().add("otp-root");
        panes.setPadding(new Insets(24, 28, 24, 28));
        scene = new Scene(panes, 400, 600);
        var css = ForgotPasswordDialog.class.getResource("/view/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("Forgot password");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnHidden(e -> countdown.stop());
        showPane(emailPane);
    }

    private void buildEmailPane(String textSoFar) {
        txtEmail.setPromptText("name@example.com");
        txtEmail.getStyleClass().add("otp-input");
        if (textSoFar != null && textSoFar.contains("@")) {
            txtEmail.setText(textSoFar.trim());
        }
        txtEmail.setOnAction(e -> sendCode(false));
        btnSend.getStyleClass().add("otp-button");
        btnSend.setMaxWidth(Double.MAX_VALUE);
        btnSend.setOnAction(e -> sendCode(false));
        lblEmailError.getStyleClass().add("otp-error");
        lblEmailError.setWrapText(true);
        lblEmailError.setMinHeight(Region.USE_PREF_SIZE);

        emailPane.getChildren().addAll(
                backButton(e -> stage.close()),
                title("Forgot password"),
                subtitle("Enter the e-mail address of your staff account. We will send you a verification code. "
                        + "(The owner saves the address on the Staff screen.)"),
                spacer(10), label("E-MAIL ADDRESS"), txtEmail, lblEmailError, btnSend);
    }

    private void sendCode(boolean resend) {
        Button button = resend ? null : btnSend;
        String typed = txtEmail.getText();
        if (button != null) {
            button.setText("Sending...");
            button.setDisable(true);
        }
        PauseTransition pause = new PauseTransition(Duration.millis(60));
        pause.setOnFinished(e -> {
            try {
                scene.setCursor(Cursor.WAIT);
                OtpService.request(resend ? email : typed);
                if (!resend) {
                    email = typed.trim().toLowerCase();
                }
                startOtpStep();
            } catch (RuntimeException ex) {
                if (resend) {
                    setMessage(ex.getMessage(), false);
                } else {
                    lblEmailError.setText(ex.getMessage());
                }
            } finally {
                scene.setCursor(Cursor.DEFAULT);
                if (button != null) {
                    button.setText("Send code");
                    button.setDisable(false);
                }
            }
        });
        pause.play();
    }

    private void buildOtpPane() {
        for (int i = 0; i < LEN; i++) {
            boxes[i] = digitBox(i);
        }
        boxRow.setAlignment(Pos.CENTER);
        boxRow.getChildren().addAll(boxes);

        lblSentTo.getStyleClass().add("otp-sub");
        lblSentTo.setWrapText(true);
        lblSentTo.setMinHeight(Region.USE_PREF_SIZE);
        Hyperlink change = new Hyperlink("Change");
        change.setOnAction(e -> {
            countdown.stop();
            showPane(emailPane);
        });
        VBox sent = new VBox(2, lblSentTo, change);

        lblMessage.setWrapText(true);
        lblMessage.setMinHeight(Region.USE_PREF_SIZE);
        lblMessage.setAlignment(Pos.CENTER);
        lblMessage.setMaxWidth(Double.MAX_VALUE);
        lblTimer.getStyleClass().add("otp-timer");
        lblTimer.setMaxWidth(Double.MAX_VALUE);
        lblTimer.setAlignment(Pos.CENTER);

        btnConfirm.getStyleClass().add("otp-button");
        btnConfirm.setMaxWidth(Double.MAX_VALUE);
        btnConfirm.setOnAction(e -> confirmCode());

        linkResend.setOnAction(e -> sendCode(true));
        HBox resend = new HBox(4, label("If you didn't receive a code?"), linkResend);
        resend.setAlignment(Pos.CENTER);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        otpPane.getChildren().addAll(
                backButton(e -> {
                    countdown.stop();
                    showPane(emailPane);
                }),
                title("OTP Verification"), sent, spacer(8), boxRow, lblMessage, lblTimer, btnConfirm, grow, resend);

        countdown.getKeyFrames().setAll(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
        countdown.setCycleCount(Timeline.INDEFINITE);
    }

    private TextField digitBox(int index) {
        TextField box = new TextField();
        box.getStyleClass().add("otp-box");
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(46, 54);
        box.setTextFormatter(new TextFormatter<String>(change -> {
            String typed = change.getText();
            if (typed.isEmpty()) {
                return change;
            }
            String digits = typed.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return null;
            }
            if (digits.length() > 1) {
                for (int i = 0; i < digits.length() && index + i < LEN; i++) {
                    boxes[index + i].setText(String.valueOf(digits.charAt(i)));
                }
                boxes[Math.min(LEN - 1, index + digits.length())].requestFocus();
                return null;
            }
            change.setText(digits);
            change.setRange(0, box.getText().length());
            return change;
        }));
        box.textProperty().addListener((obs, oldValue, newValue) -> {
            clearResult();
            if (!newValue.isEmpty() && index < LEN - 1) {
                boxes[index + 1].requestFocus();
            }
        });
        box.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE && box.getText().isEmpty() && index > 0) {
                boxes[index - 1].clear();
                boxes[index - 1].requestFocus();
            } else if (e.getCode() == KeyCode.LEFT && index > 0) {
                boxes[index - 1].requestFocus();
            } else if (e.getCode() == KeyCode.RIGHT && index < LEN - 1) {
                boxes[index + 1].requestFocus();
            } else if (e.getCode() == KeyCode.ENTER) {
                confirmCode();
            }
        });
        box.setOnMouseClicked(e -> box.selectAll());
        return box;
    }

    private void startOtpStep() {
        token = null;
        lblSentTo.setText("We have sent the verification code to your email address " + mask(email));
        for (TextField box : boxes) {
            box.clear();
            box.setDisable(false);
        }
        clearResult();
        btnConfirm.setText("Confirm");
        btnConfirm.getStyleClass().remove("otp-button-success");
        btnConfirm.setDisable(false);
        linkResend.setDisable(false);
        deadline = Instant.now().plusSeconds(OtpService.VALID_MINUTES * 60L);
        updateTimer();
        countdown.playFromStart();
        showPane(otpPane);
        boxes[0].requestFocus();
    }

    private void updateTimer() {
        long left = Math.max(0, deadline.getEpochSecond() - Instant.now().getEpochSecond());
        if (left == 0) {
            lblTimer.setText("The code has expired. Press Resend to get a new one.");
            countdown.stop();
        } else {
            lblTimer.setText(String.format("The code will expire in %02d:%02d", left / 60, left % 60));
        }
    }

    private void confirmCode() {
        if (btnConfirm.getStyleClass().contains("otp-button-success")) {
            return;
        }
        StringBuilder code = new StringBuilder();
        for (TextField box : boxes) {
            code.append(box.getText());
        }
        if (code.length() < LEN) {
            setMessage("Enter the " + LEN + "-digit code.", false);
            shake();
            return;
        }
        try {
            token = OtpService.verify(email, code.toString());
            markVerified();
        } catch (RuntimeException e) {
            markWrong(e.getMessage());
        }
    }

    private void markVerified() {
        for (TextField box : boxes) {
            box.getStyleClass().remove("otp-box-error");
            box.getStyleClass().add("otp-box-success");
            box.setDisable(true);
        }
        setMessage("✓  Verified successfully", true);
        btnConfirm.setText("✓");
        btnConfirm.getStyleClass().add("otp-button-success");
        linkResend.setDisable(true);
        countdown.stop();
        PauseTransition pause = new PauseTransition(Duration.millis(900));
        pause.setOnFinished(e -> {
            showPane(passwordPane);
            txtNew.requestFocus();
        });
        pause.play();
    }

    private void markWrong(String message) {
        for (TextField box : boxes) {
            if (!box.getStyleClass().contains("otp-box-error")) {
                box.getStyleClass().add("otp-box-error");
            }
        }
        setMessage("ⓘ  " + message, false);
        shake();
    }

    private void clearResult() {
        for (TextField box : boxes) {
            if (box != null) {
                box.getStyleClass().removeAll("otp-box-error", "otp-box-success");
            }
        }
        lblMessage.setText("");
    }

    private void setMessage(String message, boolean success) {
        lblMessage.getStyleClass().removeAll("otp-error", "otp-success");
        lblMessage.getStyleClass().add(success ? "otp-success" : "otp-error");
        lblMessage.setText(message);
    }

    private void shake() {
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(boxRow.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60), new KeyValue(boxRow.translateXProperty(), -10)),
                new KeyFrame(Duration.millis(120), new KeyValue(boxRow.translateXProperty(), 10)),
                new KeyFrame(Duration.millis(180), new KeyValue(boxRow.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(240), new KeyValue(boxRow.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(300), new KeyValue(boxRow.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(360), new KeyValue(boxRow.translateXProperty(), 0)));
        shake.play();
    }

    private void buildPasswordPane() {
        txtNew.setPromptText("New password (minimum " + PasswordUtil.MIN_LENGTH + " characters)");
        txtConfirm.setPromptText("Repeat the new password");
        txtNew.getStyleClass().add("otp-input");
        txtConfirm.getStyleClass().add("otp-input");
        txtConfirm.setOnAction(e -> changePassword());
        btnChange.getStyleClass().add("otp-button");
        btnChange.setMaxWidth(Double.MAX_VALUE);
        btnChange.setOnAction(e -> changePassword());
        lblPasswordError.getStyleClass().add("otp-error");
        lblPasswordError.setWrapText(true);
        lblPasswordError.setMinHeight(Region.USE_PREF_SIZE);

        passwordPane.getChildren().addAll(
                backButton(e -> stage.close()),
                title("New password"),
                subtitle("Your code was accepted. Choose a new password for your account."),
                spacer(10), label("NEW PASSWORD"), txtNew, label("REPEAT THE NEW PASSWORD"), txtConfirm,
                lblPasswordError, btnChange);
    }

    private void changePassword() {
        try {
            if (!txtNew.getText().equals(txtConfirm.getText())) {
                throw new IllegalArgumentException("The two passwords are not the same.");
            }
            OtpService.setNewPassword(email, token, txtNew.getText());
            lblPasswordError.setText("");
            btnChange.setText("✓  Password changed");
            btnChange.getStyleClass().add("otp-button-success");
            btnChange.setDisable(true);
            txtNew.setDisable(true);
            txtConfirm.setDisable(true);
            PauseTransition pause = new PauseTransition(Duration.millis(1500));
            pause.setOnFinished(e -> stage.close());
            pause.play();
        } catch (RuntimeException e) {
            lblPasswordError.setText(e.getMessage());
        }
    }

    private void showPane(Node pane) {
        for (Node node : new Node[]{emailPane, otpPane, passwordPane}) {
            node.setVisible(node == pane);
            node.setManaged(node == pane);
        }
    }

    private static Button backButton(javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button back = new Button("←");
        back.getStyleClass().add("otp-back");
        back.setOnAction(action);
        return back;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("otp-title");
        return label;
    }

    private static Label subtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("otp-sub");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("otp-sub");
        return label;
    }

    private static Region spacer(double height) {
        Region region = new Region();
        region.setMinHeight(height);
        region.setPrefHeight(height);
        return region;
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String ending = dot >= 0 ? domain.substring(dot) : "";
        return local.substring(0, Math.min(2, local.length())) + "*".repeat(Math.max(3, local.length() - 2)) + "@***" + ending;
    }
}
