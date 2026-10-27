package util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;

public final class AvatarView {

    private AvatarView() {
    }

    public static Node create(double size, String fullName, byte[] photo) {
        return create(size, fullName, photo, false);
    }

    public static Node create(double size, String fullName, byte[] photo, boolean onLightBackground) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setAlignment(Pos.CENTER);
        if (photo != null && photo.length > 0) {
            Image image = new Image(new ByteArrayInputStream(photo), size * 2, size * 2, true, true);
            if (!image.isError()) {
                ImageView view = new ImageView(image);
                view.setFitWidth(size);
                view.setFitHeight(size);
                view.setClip(new Circle(size / 2, size / 2, size / 2));
                box.getChildren().add(view);
                return box;
            }
        }
        Label initials = new Label(initials(fullName));
        initials.getStyleClass().add("avatar");
        initials.setMinSize(size, size);
        initials.setPrefSize(size, size);
        initials.setStyle("-fx-font-size: " + Math.max(11, size * 0.36) + "px;");
        if (size > 60 || onLightBackground) {
            initials.setStyle(initials.getStyle() + " -fx-background-color: #16A34A; -fx-text-fill: white;");
        }
        box.getChildren().add(initials);
        return box;
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
