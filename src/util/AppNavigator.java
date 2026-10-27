package util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class AppNavigator {

    private AppNavigator() {
    }

    public static void showLogin(Stage stage) {
        show(stage, "/view/login.fxml", 1100, 700, 980, 640);
    }

    public static void showMain(Stage stage) {
        show(stage, "/view/main.fxml", 1240, 800, 1080, 700);
    }

    public static Parent load(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(AppNavigator.class.getResource(fxml));
        return loader.load();
    }

    private static void show(Stage stage, String fxml, double width, double height,
                             double minWidth, double minHeight) {
        try {
            Parent root = load(fxml);

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, width, height);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            stage.setTitle("VITALIS - Hospital Management System");
            stage.setWidth(width);
            stage.setHeight(height);
            stage.setMinWidth(minWidth);
            stage.setMinHeight(minHeight);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.error("Screen error", "Could not open " + fxml + ".\n\n" + e.getMessage());
        }
    }
}
