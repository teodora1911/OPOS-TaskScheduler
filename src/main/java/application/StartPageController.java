package application;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class StartPageController implements Initializable {

    @FXML private TextField numberOfTasksField;
    @FXML private TextField numberOfCoresField;
    @FXML private Label bannerLabel;
    @FXML private CheckBox preemptiveCheck;

    @FXML
    void startScheduler(ActionEvent event) {
        try{
            bannerLabel.setVisible(false);
            int numberOfParallelExecutingTasks = Integer.parseInt(numberOfTasksField.getText());
            int numberOfAvailableCores = Integer.parseInt(numberOfCoresField.getText());
            boolean preemptive = preemptiveCheck.isSelected();

            if(numberOfParallelExecutingTasks > 0 && numberOfAvailableCores > 0){
                Utility.setScheduler(numberOfAvailableCores, numberOfParallelExecutingTasks, preemptive);
                try{
                    FXMLLoader loader = new FXMLLoader(FinalPageController.class.getResource("FinalPage.fxml"));
                    Scene scene = new Scene(loader.load());
                    Stage stage = new Stage();
                    stage.setTitle("Raspoređivač zadataka");
                    FinalPageController.stage = stage;
                    stage.setScene(scene);
                    if(StartPageController.stage != null)
                        StartPageController.stage.close();
                    stage.show();
                } catch (IOException ioexc){
                    ioexc.printStackTrace();
                }
            } else { throw new IllegalArgumentException("Arguments are not valid!"); }
        } catch (Exception exc){
            System.err.println(exc.getMessage());; // TODO: Comment this
            bannerLabel.setVisible(true);
        }
    }

    public static Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resource){
        numberOfTasksField.setText(Utility.NumberOfConcurrentlyExecutingTasks.toString());
        numberOfCoresField.setText(Utility.NumberOfAvailableCores.toString());
        bannerLabel.setVisible(false);
        preemptiveCheck.setSelected(true);
    }
}
