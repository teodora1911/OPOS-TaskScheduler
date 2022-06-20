package scheduler;

import application.Utility;
import scheduler.task.SchedulableTask;

import java.io.File;

public class SerializeTaskEvent extends SchedulingEvent {

    protected SchedulableTask taskToSerialize;

    public SerializeTaskEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.taskToSerialize = task;
    }
    @Override
    public void execute(){
        if(taskToSerialize == null)
            return;
        String path = Utility.SerializedTasksFolderPath + File.separator + "Task" + taskToSerialize.id + "-" + System.currentTimeMillis() + ".ser";
        if(scheduler.executingTasks.contains(taskToSerialize)){
            taskToSerialize.pause(false);
            scheduler.executingTasks.remove(taskToSerialize);
            scheduler.numberOfFreeCores += taskToSerialize.parallelismDegree;
            taskToSerialize.serialize(path);
            taskToSerialize.terminate();
        } else if (scheduler.pendingTasks.contains(taskToSerialize)){
            // task je vec pauziran
            taskToSerialize.serialize(path);
            scheduler.pendingTasks.remove(taskToSerialize);
            taskToSerialize.terminate();
        } else if (scheduler.userPausedTasks.contains(taskToSerialize)){
            taskToSerialize.serialize(path);
            scheduler.userPausedTasks.remove(taskToSerialize);
            taskToSerialize.terminate();
        }
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
