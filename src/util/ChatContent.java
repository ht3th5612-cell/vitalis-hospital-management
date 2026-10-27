package util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Message;

import java.io.ByteArrayInputStream;
import java.util.function.Supplier;

public final class ChatContent {

    private static final double THUMB_WIDTH = 280;
    private static final double THUMB_HEIGHT = 240;

    private ChatContent() {
    }

    public static Node photo(Message message, byte[] data) {
        Image thumb = new Image(new ByteArrayInputStream(data), THUMB_WIDTH, THUMB_HEIGHT, true, true);
        ImageView view = new ImageView(thumb);
        view.getStyleClass().add("chat-photo");
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("chat-photo-frame");
        frame.setOnMouseClicked(e -> showFullPhoto(message, data));
        return frame;
    }

    private static void showFullPhoto(Message message, byte[] data) {
        Image full = new Image(new ByteArrayInputStream(data));
        ImageView view = new ImageView(full);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(full.getWidth(), 900));
        view.setFitHeight(Math.min(full.getHeight(), 620));
        Button save = new Button("Save");
        save.setOnAction(e -> {
            try {
                ChatFiles.save(save.getScene().getWindow(), message.getFileName(), data);
            } catch (RuntimeException ex) {
                AlertUtil.warning("Save photo", ex.getMessage());
            }
        });
        HBox bar = new HBox(save);
        bar.setAlignment(Pos.CENTER_RIGHT);
        VBox page = new VBox(12, view, bar);
        page.getStyleClass().add("page");
        Stage stage = new Stage();
        stage.setTitle(message.getFileName());
        javafx.scene.Scene scene = new javafx.scene.Scene(page);
        var css = ChatContent.class.getResource("/view/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }

    public static Node video(Message message, Supplier<byte[]> loader) {
        Label badge = new Label("VIDEO");
        badge.getStyleClass().add("chat-file-badge");
        Label name = new Label(message.getFileName());
        name.getStyleClass().add("chat-file-name");
        name.setMaxWidth(190);
        Label size = new Label(ChatFiles.size(message.getFileSize()));
        size.getStyleClass().add("chat-file-size");
        VBox texts = new VBox(2, name, size);

        Button play = small("Play");
        play.setOnAction(e -> run("Play video", () -> ChatFiles.open(message.getFileName(), loader.get())));
        Button save = small("Save");
        save.setOnAction(e -> run("Save video", () -> ChatFiles.save(save.getScene().getWindow(), message.getFileName(), loader.get())));

        HBox row = new HBox(10, badge, texts, play, save);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button small(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button-secondary", "chat-small-button");
        return button;
    }

    private static void run(String title, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            AlertUtil.warning(title, e.getMessage());
        }
    }
}
