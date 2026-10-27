package app;

import javafx.application.Application;
import javafx.stage.Stage;
import util.AppNavigator;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        AppNavigator.showLogin(primaryStage);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
