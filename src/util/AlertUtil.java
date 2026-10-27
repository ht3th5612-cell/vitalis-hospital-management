package util;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

public final class AlertUtil {

    private AlertUtil() {
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void warning(String title, String message) {
        show(Alert.AlertType.WARNING, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    public static void failure(RuntimeException e, String checkTitle) {
        if (e instanceof SecurityException) {
            warning("Not allowed", e.getMessage());
        } else if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            warning(checkTitle, e.getMessage());
        } else {
            error("Database error", e.getMessage());
        }
    }

    public static boolean confirm(String title, String message) {
        boolean destructive = title.startsWith("Delete") || title.startsWith("Remove") || title.startsWith("Dismiss");
        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert alert = build(Alert.AlertType.CONFIRMATION, title, message, yes, no);
        alert.getDialogPane().lookupButton(yes).getStyleClass().add(destructive ? "button-danger" : "button-success");
        alert.getDialogPane().lookupButton(no).getStyleClass().add("button-secondary");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    private static void show(Alert.AlertType type, String title, String message) {
        build(type, title, message).showAndWait();
    }

    private static Alert build(Alert.AlertType type, String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(type, "", buttons);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setGraphic(null);

        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (owner != null) {
            alert.initOwner(owner);
        }

        Label symbol = new Label(switch (type) {
            case CONFIRMATION -> "?";
            case WARNING -> "!";
            case ERROR -> "×";
            default -> "i";
        });
        symbol.getStyleClass().add("dialog-symbol");
        StackPane badge = new StackPane(symbol);
        badge.getStyleClass().addAll("dialog-badge", "dialog-badge-" + type.name().toLowerCase());
        badge.setMinSize(48, 48);
        badge.setMaxSize(48, 48);

        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-heading");
        Label text = new Label(message);
        text.getStyleClass().add("muted");
        text.setWrapText(true);
        text.setMaxWidth(320);
        VBox texts = new VBox(6, heading, text);
        texts.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(18, badge, texts);
        content.setAlignment(Pos.CENTER_LEFT);

        DialogPane pane = alert.getDialogPane();
        pane.setContent(content);
        pane.setMinWidth(420);
        pane.getStyleClass().add("app-dialog");
        var css = AlertUtil.class.getResource("/view/style.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        return alert;
    }
}
