package application;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import scheduler.TaskScheduler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class StartPageController implements Initializable {

    @FXML private TextField numberOfTasksField;
    @FXML private TextField numberOfCoresField;
    @FXML private Label bannerLabel;
    @FXML private CheckBox preemptiveCheck;

    private boolean restored = false;

    @FXML
    void startScheduler(ActionEvent event) {
        try{
            bannerLabel.setVisible(false);
            int numberOfParallelExecutingTasks = Integer.parseInt(numberOfTasksField.getText());
            int numberOfAvailableCores = Integer.parseInt(numberOfCoresField.getText());
            boolean preemptive = preemptiveCheck.isSelected();

            if(numberOfParallelExecutingTasks > 0 && numberOfAvailableCores > 0){
                Utility.setScheduler(numberOfAvailableCores, numberOfParallelExecutingTasks, preemptive);
                System.out.println("Restored? " + restored);
                try{
                    FXMLLoader loader = new FXMLLoader(FinalPageController.class.getResource("FinalPage.fxml"));
                    Scene scene = new Scene(loader.load());
                    Stage stage = new Stage();
                    stage.setTitle("Raspoređivač zadataka");
                    FinalPageController.stage = stage;
                    FinalPageController.restore = restored;
                    stage.setScene(scene);
                    if(StartPageController.stage != null)
                        StartPageController.stage.close();
                    stage.show();
                } catch (IOException ioexc){
                    ioexc.printStackTrace();
                }
            } else { throw new IllegalArgumentException("Arguments are not valid!"); }
        } catch (Exception exc){
            bannerLabel.setVisible(true);
        }
    }

    @FXML
    void restoreScheduler(ActionEvent action){
        try{
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Izaberite fajl");
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Serialized Files", "*.ser"));
            fileChooser.setInitialDirectory(new File(Utility.AutosavedSchedulerPath));
            File picked = fileChooser.showOpenDialog(stage);
            if(picked != null){
                TaskScheduler scheduler = TaskScheduler.deserialize(picked.getAbsolutePath());
                Utility.Scheduler = scheduler;
                numberOfTasksField.setText(String.valueOf(scheduler.maxParallelTasks));
                numberOfCoresField.setText(String.valueOf(scheduler.numberOfAvailableCores));
                preemptiveCheck.setSelected(scheduler.preemptive);
//                numberOfTasksField.setEditable(false);
//                numberOfCoresField.setEditable(false);
                restored = true;
            } else {
                throw new IllegalArgumentException("Morate odabrati jedan fajl.");
            }
        } catch (Exception exc){
            exc.printStackTrace();
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
