package application;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import scheduler.task.PixelationTask;
import scheduler.task.Resource;
import scheduler.task.SchedulableTask;
import scheduler.task.TaskType;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FinalPageController implements Initializable {

    @FXML private TableView<TaskProgress> taskTable;
    @FXML private TableColumn<TaskProgress, SchedulableTask> taskColumn;
    @FXML private TableColumn<TaskProgress, ProgressBar> progressColumn;
    @FXML private Button pauseTaskButton;
    @FXML private Button restartTaskButton;
    @FXML private Button terminateTaskButton;
    @FXML private Button serializeTaskButton;
    @FXML private TextField priorityTextField;
    @FXML private TextField parallelismDegreeField;
    @FXML private TextField executionTimeField;
    @FXML private DatePicker datePicker;
    @FXML private TextField hoursField;
    @FXML private TextField minutesField;
    @FXML private TextField secondsField;
    @FXML private ComboBox<TaskType> taskTypeComboBox;
    @FXML private Button addTaskButton;
    @FXML private Button deserializeTaskButton;

    private ArrayList<Resource> pickedResources = new ArrayList<>();
    private static final List<Integer> hours = IntStream.range(0, 24).boxed().collect(Collectors.toList());
    private static final List<Integer> minSec = IntStream.range(0, 60).boxed().collect(Collectors.toList());

    private ObservableList<TaskProgress> tasks = FXCollections.observableArrayList();

    @FXML
    void chooseResources(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Izaberite resurse");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*." + PixelationTask.Extension));
        fileChooser.setInitialDirectory(new File(Utility.InputFolderPath));
        List<File> picked = fileChooser.showOpenMultipleDialog(stage);
        if(picked != null && !picked.isEmpty()){
            pickedResources.clear();
            picked.forEach(f -> pickedResources.add(new Resource(f)));
            try{
                if(!Utility.Scheduler.resources.keySet().containsAll(pickedResources)){
                    System.out.println("Scheduler does not contain all specified resources!");
                    pickedResources.clear();
                }
            } catch (Exception exc){
                exc.printStackTrace();
                pickedResources.clear();
            }
        }
    }

    public static Stage stage;
    @Override
    public void initialize(URL location, ResourceBundle resource) {
        resetTaskAttributes();
        datePicker.setValue(LocalDate.now());
        taskTypeComboBox.getItems().addAll(TaskType.values());

        taskColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TaskProgress, SchedulableTask>, ObservableValue<SchedulableTask>>() {
            @Override
            public ObservableValue<SchedulableTask> call(TableColumn.CellDataFeatures<TaskProgress, SchedulableTask> column) {
                return new SimpleObjectProperty<SchedulableTask>(column.getValue().task);
            }
        });
        progressColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TaskProgress, ProgressBar>, ObservableValue<ProgressBar>>() {
            @Override
            public ObservableValue<ProgressBar> call(TableColumn.CellDataFeatures<TaskProgress, ProgressBar> column) {
                return new SimpleObjectProperty<ProgressBar>(column.getValue().progressBar);
            }
        });
        taskTable.setStyle("-fx-background-color: #f1f1f2; -fx-border-color: #bcbabe; " +
                "-fx-border-radius: 2; " +
                "-fx-font-family: 'Arial';" +
                " -fx-font-size: 15; " +
                "-fx-text-fill: #1e656d;" +
                "-fx-selection-bar: #bcbabe");
        Utility.Scheduler.setGUICommunication(FinalPageController.this::refreshView);

        pauseTaskButton.setOnAction(e -> {
            TaskProgress selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.pauseTask(selected.task);
            }
        });
        restartTaskButton.setOnAction(e -> {
            TaskProgress selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.resumeTask(selected.task);
            }
        });
        terminateTaskButton.setOnAction(e -> {
            TaskProgress selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.cancelTask(selected.task);
            }
        });
        serializeTaskButton.setOnAction(e -> {
            TaskProgress selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.serializeTask(selected.task);
            }
        });
        addTaskButton.setOnAction(e -> {
            try{
                int priority = Integer.parseInt(priorityTextField.getText());
                int parallelism = Integer.parseInt(parallelismDegreeField.getText());
                int executionTime = Integer.parseInt(executionTimeField.getText()); // u sekundama
                int hours = Integer.parseInt(hoursField.getText());
                int minutes = Integer.parseInt(minutesField.getText());
                int seconds = Integer.parseInt(secondsField.getText());
                int dateCmp = datePicker.getValue().compareTo(LocalDate.now());

//                System.out.println(priority);
//                System.out.println(parallelism);
//                System.out.println(executionTime);
//                System.out.println(hours);
//                System.out.println(minutes);
//                System.out.println(seconds);
//                System.out.println(dateCmp);
//                System.out.println(pickedResources.size());
//                System.out.println(taskTypeComboBox.getSelectionModel().getSelectedItem());

                if(priority > 0 && parallelism > 0 && executionTime > 0 && FinalPageController.hours.contains(hours) && minSec.contains(minutes) && minSec.contains(seconds) && dateCmp >= 0 && pickedResources.size() > 0 && taskTypeComboBox.getSelectionModel().getSelectedItem() != null){
                    LocalDateTime deadline = LocalDateTime.of(datePicker.getValue(), LocalTime.of(hours, minutes, seconds));
                    executionTime *= 1000;
                    if(priority <= SchedulableTask.MaxPriority)
                        priority = SchedulableTask.MaxPriority + 1;
                    if(priority > Utility.MinTaskPriority)
                        priority = Utility.MinTaskPriority;
                    if(parallelism > Utility.Scheduler.numberOfAvailableCores)
                        parallelism = Utility.Scheduler.numberOfAvailableCores;

                    PixelationTask task = new PixelationTask(priority, executionTime, deadline, parallelism, Utility.OutputFolderPath, pickedResources, new ProgressBar(), FinalPageController.this::changeProgress);
                    Utility.Scheduler.registerTask(task);
                    resetTaskAttributes();
                } else {
                    throw new IllegalArgumentException("Arguments are not valid!");
                }
            } catch (Exception exc){
                exc.printStackTrace();
               // System.err.println(exc.getMessage());
            }
        });
        deserializeTaskButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Izaberite zadatak");
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Serialized Files", "*.ser"));
            fileChooser.setInitialDirectory(new File(Utility.SerializedTasksFolderPath));
            File picked = fileChooser.showOpenDialog(stage);
            if(picked != null){
                PixelationTask taskToAdd = PixelationTask.deserialize(picked.getAbsolutePath());
                if(taskToAdd != null){
                    System.out.println("Deserialization successful.");
                    taskToAdd.restore(new ProgressBar(), FinalPageController.this::changeProgress);
                    new Thread(taskToAdd).start();
                    Utility.Scheduler.registerTask(taskToAdd);
                } else {
                    System.out.println("Deserialization failed.");
                }
            }
        });
    }

    public void refreshView(Collection<SchedulableTask> allTasks){
        Platform.runLater(() -> {
            List<TaskProgress> guiTasks = allTasks.stream().distinct().map(t -> new TaskProgress(t, t.progressBar)).collect(Collectors.toList());
            tasks.clear();
            tasks.addAll(guiTasks);
            taskTable.setItems(tasks);
        });
    }

    public void changeProgress(SchedulableTask task){
        if(task != null && task.progressBar != null){
            task.progressBar.setProgress(task.getProgress());
        }
    }

    private void resetTaskAttributes(){
        priorityTextField.setText(Utility.DefaultTaskPriority.toString());
        parallelismDegreeField.setText(Utility.DefaultTaskParallelismDegree.toString());
        executionTimeField.setText(Utility.DefaultTaskExecutionTimeSec.toString());
        pickedResources.clear();
    }
}
