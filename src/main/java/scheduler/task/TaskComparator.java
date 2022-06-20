package scheduler.task;

import java.io.Serializable;
import java.util.Comparator;

public class TaskComparator implements Comparator<SchedulableTask>, Serializable {

    private boolean inverted = false;

    public TaskComparator(boolean inverted){
        this.inverted = inverted;
    }
    @Override
    public int compare(SchedulableTask lhs, SchedulableTask rhs) {
        if(inverted){
            return rhs.getTaskPriority() - lhs.getTaskPriority();
        } else {
            return lhs.getTaskPriority() - rhs.getTaskPriority();
        }
    }
}
