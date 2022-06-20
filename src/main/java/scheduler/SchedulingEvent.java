package scheduler;

public abstract class SchedulingEvent /*implements Serializable*/{
    // before execution this reference should be updated
    protected TaskScheduler scheduler;

    public SchedulingEvent(TaskScheduler scheduler){
        this.scheduler = scheduler;
    }
    public abstract void execute();
}
