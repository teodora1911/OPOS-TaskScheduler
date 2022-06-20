package scheduler;

import scheduler.task.SchedulableTask;

public class UserPauseTaskEvent extends SchedulingEvent {

    protected SchedulableTask taskToPause;

    public UserPauseTaskEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.taskToPause = task;
    }

    @Override
    public void execute() {
        if(taskToPause == null)
            return;
        if(scheduler.executingTasks.contains(taskToPause)){
            scheduler.executingTasks.remove(taskToPause);
            scheduler.numberOfFreeCores += taskToPause.parallelismDegree;
            taskToPause.pause(true);
            scheduler.userPausedTasks.add(taskToPause);
        }
        if(scheduler.pendingTasks.contains(taskToPause)){
            taskToPause.pause(true);
            scheduler.pendingTasks.remove(taskToPause);
            scheduler.userPausedTasks.add(taskToPause);
        }
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
