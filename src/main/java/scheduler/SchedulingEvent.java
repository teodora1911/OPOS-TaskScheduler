package scheduler;

import java.io.Serializable;

public abstract class SchedulingEvent implements Serializable {

    protected transient TaskScheduler scheduler;

    public SchedulingEvent(TaskScheduler scheduler){
        this.scheduler = scheduler;
    }

    public abstract void execute();
}
