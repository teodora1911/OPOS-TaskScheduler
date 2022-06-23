package scheduler;

import scheduler.task.SchedulableTask;

public class TaskCompletedEvent extends SchedulingEvent {

    protected SchedulableTask completedTask;

    public TaskCompletedEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.completedTask = task;
    }

    @Override
    public void execute(){
        if(completedTask == null)
            return;
        completedTask.terminate();
        if(scheduler.executingTasks.contains(completedTask)){
            scheduler.executingTasks.remove(completedTask);
            scheduler.numberOfFreeCores += completedTask.parallelismDegree;
        }
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
