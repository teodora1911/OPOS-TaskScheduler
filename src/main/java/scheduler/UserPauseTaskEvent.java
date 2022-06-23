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
        taskToPause.pause(true);
        if(!scheduler.userPausedTasks.contains(taskToPause))
            scheduler.userPausedTasks.add(taskToPause);
        if(scheduler.executingTasks.contains(taskToPause)){
            scheduler.executingTasks.remove(taskToPause);
            scheduler.numberOfFreeCores += taskToPause.parallelismDegree;
        }
        if(scheduler.pendingTasks.contains(taskToPause))
            scheduler.pendingTasks.remove(taskToPause);
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
