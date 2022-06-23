package scheduler;

import scheduler.task.SchedulableTask;

/*
 * Koristimo kada:
 *     1. Zadatak se izvršava duže nego što je dozvoljeno
 *     2. Korisnik je eksplicitno zatražio terminiranje zadatka
 */
public class CancelTaskEvent extends SchedulingEvent {
    protected SchedulableTask taskToCancel;

    public CancelTaskEvent(TaskScheduler scheduler, SchedulableTask task){
        super(scheduler);
        this.taskToCancel = task;
    }

    @Override
    public void execute() {
        if(taskToCancel == null)
            return;
        taskToCancel.terminate();
        System.out.println("Terminating task!");
        if(scheduler.userPausedTasks.contains(taskToCancel))
            scheduler.userPausedTasks.remove(taskToCancel);
        if(scheduler.pendingTasks.contains(taskToCancel))
            scheduler.pendingTasks.remove(taskToCancel);
        if(scheduler.executingTasks.contains(taskToCancel)){
            scheduler.executingTasks.remove(taskToCancel);
            scheduler.numberOfFreeCores += taskToCancel.parallelismDegree;
        }
        scheduler.eventQueue.offer(new ScheduleTasksEvent(scheduler));
    }
}
