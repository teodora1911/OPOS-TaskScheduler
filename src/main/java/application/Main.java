package application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(StartPageController.class.getResource("StartPage.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        StartPageController.stage = stage;
        stage.setTitle("Raspoređivač zadataka");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) throws IOException {
        // loading properties
        Utility.loadPropertiesFile("load-info.properties");
        // show stage
        launch();
    }
}