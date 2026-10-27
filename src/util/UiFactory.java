package util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BiPredicate;

public final class UiFactory {

    private UiFactory() {
    }

    public static Node statCard(String title, String value, String accentClass) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-label");

        VBox texts = new VBox(3, titleLabel, valueLabel);
        texts.setAlignment(Pos.CENTER_LEFT);
        StackPane tile = new StackPane(IconFactory.stat(title, 36));
        tile.getStyleClass().add("stat-icon-tile");
        HBox card = new HBox(14, tile, texts);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("stat-card", accentClass);
        card.setMinWidth(210);
        card.setPrefWidth(235);
        return card;
    }

    public static void setStatus(Label label, String message, boolean success) {
        label.setText(message);
        label.getStyleClass().removeAll("success-label", "error-label");
        label.getStyleClass().add(success ? "success-label" : "error-label");
    }

    public static <T> void refillKeepingChoice(ComboBox<T> combo, List<T> items, BiPredicate<T, T> sameItem) {
        T chosen = combo.getValue();
        combo.getItems().setAll(items);
        if (chosen != null) {
            items.stream().filter(item -> sameItem.test(item, chosen)).findFirst().ifPresent(combo::setValue);
        }
    }

    public static String roleTitle(String role) {
        if (role == null) {
            return "Staff";
        }
        return switch (role.toUpperCase()) {
            case "OWNER" -> "Hospital Owner (Super Admin)";
            case "ADMIN" -> "Department Admin";
            case "DOCTOR" -> "Doctor";
            case "NURSE" -> "Nurse";
            case "RECEPTIONIST" -> "Receptionist / Cashier";
            case "INVENTORY" -> "Inventory Manager";
            default -> role;
        };
    }

}
