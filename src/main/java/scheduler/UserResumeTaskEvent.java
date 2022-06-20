package scheduler;

import scheduler.task.SchedulableTask;

public class UserResumeTaskEvent extends SchedulingEvent {

    protected SchedulableTask taskToResume;

    public UserResumeTaskEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.taskToResume = task;
    }

    @Override
    public void execute(){
        if(taskToResume == null)
            return;
        if(scheduler.userPausedTasks.contains(taskToResume)){
            taskToResume.resume(true);
            scheduler.pendingTasks.offer(taskToResume);
            scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
        }
    }
}
