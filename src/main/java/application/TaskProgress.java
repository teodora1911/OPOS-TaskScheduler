package application;

import javafx.scene.control.ProgressBar;
import scheduler.task.SchedulableTask;

public class TaskProgress {

    public SchedulableTask task;
    public ProgressBar progressBar;

    public TaskProgress() { }
    public TaskProgress(SchedulableTask task, ProgressBar progressBar) {
        this.task = task;
        this.progressBar = progressBar;
        progressBar.setPrefWidth(400);
        progressBar.setStyle("-fx-accent: #275D5D");
    }

    @Override
    public boolean equals(Object object){
        if(this == object)
            return true;
        if(object == null)
            return false;
        if(getClass() != object.getClass())
            return false;

        TaskProgress other = (TaskProgress) object;
        return this.task.equals(other.task);
    }

    @Override
    public int hashCode(){
        return task != null ? task.hashCode() : 1;
    }
}
