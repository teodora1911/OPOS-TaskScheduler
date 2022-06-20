package scheduler.task;

import java.io.Serializable;

public enum TaskState implements Serializable {
    CREATED,
    READY,
    RUNNING,
    PAUSED,
    TERMINATED;
}
