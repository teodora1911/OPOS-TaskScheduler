package scheduler;

import scheduler.task.SchedulableTask;
import scheduler.task.TaskState;

public class AddTaskEvent extends SchedulingEvent{

    protected SchedulableTask taskToAdd;

    public AddTaskEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.taskToAdd = task;
    }

    @Override
    public void execute(){
        if(taskToAdd == null)
            return;
        taskToAdd.setTaskState(TaskState.READY);
        scheduler.pendingTasks.offer(taskToAdd);
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
