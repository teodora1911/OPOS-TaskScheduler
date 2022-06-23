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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FinalPageController implements Initializable {

    @FXML private TableView<Map.Entry<SchedulableTask, ProgressBar>> taskTable;
    @FXML private TableColumn<Map.Entry<SchedulableTask, ProgressBar>, SchedulableTask> taskColumn;
    @FXML private TableColumn<Map.Entry<SchedulableTask, ProgressBar>, ProgressBar> progressColumn;
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

    private ObservableList<Map.Entry<SchedulableTask, ProgressBar>> tasks = FXCollections.observableArrayList();

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
    public static boolean restore;
    @Override
    public void initialize(URL location, ResourceBundle resource) {
        resetTaskAttributes();
        datePicker.setValue(LocalDate.now());
        taskTypeComboBox.getItems().addAll(TaskType.values());

        taskColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Map.Entry<SchedulableTask, ProgressBar>, SchedulableTask>, ObservableValue<SchedulableTask>>() {
            @Override
            public ObservableValue<SchedulableTask> call(TableColumn.CellDataFeatures<Map.Entry<SchedulableTask, ProgressBar>, SchedulableTask> column) {
                return new SimpleObjectProperty<SchedulableTask>(column.getValue().getKey());
            }
        });
        progressColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Map.Entry<SchedulableTask, ProgressBar>, ProgressBar>, ObservableValue<ProgressBar>>() {
            @Override
            public ObservableValue<ProgressBar> call(TableColumn.CellDataFeatures<Map.Entry<SchedulableTask, ProgressBar>, ProgressBar> column) {
                return new SimpleObjectProperty<ProgressBar>(column.getValue().getValue());
            }
        });
        taskTable.setStyle("-fx-background-color: #f1f1f2; -fx-border-color: #bcbabe; " +
                "-fx-border-radius: 2; " +
                "-fx-font-family: 'Arial';" +
                " -fx-font-size: 15; " +
                "-fx-text-fill: #1e656d;" +
                "-fx-selection-bar: #bcbabe");
        taskTable.setItems(tasks);

        if(Utility.Scheduler.started){
            System.out.println("Not restoring scheduler");
            Utility.Scheduler.setUpdate(FinalPageController.this::update);
        } else {
            System.out.println("Restoring scheduler");
            Utility.Scheduler.restore(FinalPageController.this::update, FinalPageController.this::changeProgress, FinalPageController.this::register);
        }

//        if(restore){
//            System.out.println("Restoring scheduler");
//            Utility.Scheduler.restore(FinalPageController.this::update, FinalPageController.this::changeProgress);
//        } else {
//            System.out.println("Not restoring scheduler");
//            Utility.Scheduler.setUpdate(FinalPageController.this::update);
//        }

        pauseTaskButton.setOnAction(e -> {
            Map.Entry<SchedulableTask, ProgressBar> selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.pauseTask(selected.getKey());
            }
        });
        restartTaskButton.setOnAction(e -> {
            Map.Entry<SchedulableTask, ProgressBar> selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.resumeTask(selected.getKey());
            }
        });
        terminateTaskButton.setOnAction(e -> {
            Map.Entry<SchedulableTask, ProgressBar> selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.cancelTask(selected.getKey());
            }
        });
        serializeTaskButton.setOnAction(e -> {
            Map.Entry<SchedulableTask, ProgressBar> selected = taskTable.getSelectionModel().getSelectedItem();
            if(selected != null){
                Utility.Scheduler.serializeTask(selected.getKey());
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

                if(priority > 0 && parallelism > 0 && executionTime > 0 && FinalPageController.hours.contains(hours) && minSec.contains(minutes) && minSec.contains(seconds) && dateCmp >= 0 && pickedResources.size() > 0 && taskTypeComboBox.getSelectionModel().getSelectedItem() != null){
                    LocalDateTime deadline = LocalDateTime.of(datePicker.getValue(), LocalTime.of(hours, minutes, seconds));
                   // executionTime *= 1000;
                    if(priority > Utility.MinTaskPriority)
                        priority = Utility.MinTaskPriority;
                    if(parallelism > Utility.Scheduler.numberOfAvailableCores)
                        parallelism = Utility.Scheduler.numberOfAvailableCores;

                    PixelationTask task = new PixelationTask(priority, executionTime, deadline, parallelism, Utility.OutputFolderPath, pickedResources, FinalPageController.this::changeProgress);
                    register(task);
                    Utility.Scheduler.registerTask(task);
                    resetTaskAttributes();
                } else {
                    throw new IllegalArgumentException("Arguments are not valid!");
                }
            } catch (Exception exc){
                System.err.println(exc.getMessage());
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
                    register(taskToAdd);
                    taskToAdd.restore(FinalPageController.this::changeProgress);
                    new Thread(taskToAdd).start();
                    Utility.Scheduler.registerTask(taskToAdd);
                } else {
                    System.out.println("Deserialization failed.");
                }
            }
        });
    }

    private ProgressBar getNewProgressBar(){
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(540);
        progressBar.setStyle("-fx-accent: #d1babc");
        return progressBar;
    }

    public void register(SchedulableTask task){
        Platform.runLater(() -> {
            taskTable.getItems().add(Map.entry(task, getNewProgressBar()));
        });
    }

    public void update(List<SchedulableTask> allTasks){
        Platform.runLater(() -> {
            taskTable.getItems().removeIf(entry -> !allTasks.contains(entry.getKey()));
        });
    }

    public void changeProgress(SchedulableTask task){
        Platform.runLater(() -> {
            taskTable.getItems().forEach(entry -> {
                if(entry.getKey().equals(task)){
                    entry.getValue().setProgress(task.getProgress());
                    return;
                }
            });
        });
    }

    private void resetTaskAttributes(){
        priorityTextField.setText(Utility.DefaultTaskPriority.toString());
        parallelismDegreeField.setText(Utility.DefaultTaskParallelismDegree.toString());
        executionTimeField.setText(Utility.DefaultTaskExecutionTimeSec.toString());
        pickedResources.clear();
    }
}
