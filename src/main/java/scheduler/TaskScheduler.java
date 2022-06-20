package scheduler;

import scheduler.task.SchedulableTask;
import scheduler.task.Resource;
import scheduler.task.TaskComparator;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;

public class TaskScheduler implements Serializable {

    public int numberOfAvailableCores;
    public int numberOfFreeCores;
    public int maxParallelTasks;
    public boolean preemptive;
    public boolean started = false;

    LinkedBlockingQueue<SchedulingEvent> eventQueue = new LinkedBlockingQueue<>();

    ArrayList<SchedulableTask> userPausedTasks = new ArrayList<>(10);
    PriorityBlockingQueue<SchedulableTask> pendingTasks = new PriorityBlockingQueue<>(10, new TaskComparator(false));
    ArrayList<SchedulableTask> executingTasks;
    public HashMap<Resource, SchedulableTask> resources = new HashMap<>();

    protected Consumer<Collection<SchedulableTask>> sendTasks;

    protected SchedulingUtility schedulingThread = new SchedulingUtility(this);
    protected CancellationTasksThread cancellationThread = new CancellationTasksThread();

    public TaskScheduler(int coresAvailable, int maxParallelTasks, boolean preemptive){
        this.numberOfAvailableCores = coresAvailable;
        this.numberOfFreeCores = coresAvailable;
        this.maxParallelTasks = maxParallelTasks;
        this.preemptive = preemptive;
        this.executingTasks = new ArrayList<>(maxParallelTasks);
        schedulingThread.setDaemon(true);
        cancellationThread.setDaemon(true);
        schedulingThread.start();
        cancellationThread.start();
        started = true;
    }

    public void setGUICommunication(Consumer<Collection<SchedulableTask>> sendTasks){
        this.sendTasks = sendTasks;
    }

    /**
     * Metode koje se pozivaju iz GUI ili
     * zadaci pozivaju ukoliko se desi neka promjena
     */
    public void startDemo() { // just for demonstration
        eventQueue.offer(new ScheduleTasksEvent(this));
    }
    public void registerTask(SchedulableTask task){
        eventQueue.offer(new AddTaskEvent(this, task));
    }

    public void pauseTask(SchedulableTask task){
        eventQueue.offer(new UserPauseTaskEvent(this, task));
    }

    public void resumeTask(SchedulableTask task){
        eventQueue.offer(new UserResumeTaskEvent(this, task));
    }

    public void cancelTask(SchedulableTask task){
        eventQueue.offer(new CancelTaskEvent(this, task));
    }

    public void completedTask(SchedulableTask task){
        eventQueue.offer(new TaskCompletedEvent(this, task));
    }

    public void serializeTask(SchedulableTask task) { eventQueue.offer(new SerializeTaskEvent(this, task)); }

    // try to lock all specified resources
    public boolean lockResources(List<Resource> resourcesToLock, SchedulableTask task){
        if(task == null || resourcesToLock == null) //  || !resources.keySet().containsAll(resourcesToLock)
            return false;
        boolean lockedAll = true;
        synchronized (resources){
            for(Resource resourceToLock : resourcesToLock)
                lockedAll = lockedAll && (resources.get(resourceToLock) == null);
            if(lockedAll){
                resourcesToLock.forEach(r -> resources.replace(r, task));
            }
        }
        return lockedAll;
    }

    // unlock all resources which specified task holds
    public void unlockResources(SchedulableTask task){
        if(task == null)
            return;
        synchronized (resources){
            resources.forEach((r, t) -> {
                if(t != null && t.equals(task))
                    resources.replace(r, null);
            });
        }
    }

    // unlock specified resource
    public void unlockResource(Resource resource, SchedulableTask task){
        if(task == null || resource == null)
            return;
        synchronized (resources){
            SchedulableTask taskHoldingResource = resources.get(resource);
            if(taskHoldingResource != null && taskHoldingResource.equals(task))
                resources.replace(resource, null);
        }
    }

    private class CancellationTasksThread extends Thread {
        @Override
        public void run(){
            System.out.println("Cancellation thread started.");
            while(true){
                for(SchedulableTask executingTask : executingTasks){
                    if(executingTask.isTerminated()){
                        eventQueue.offer(new CancelTaskEvent(TaskScheduler.this, executingTask));
                        unlockResources(executingTask);
                    }
                }
                eventQueue.offer(new ScheduleTasksEvent(TaskScheduler.this));
                try{
                    Thread.sleep(10);
                } catch(InterruptedException exc){
                    exc.printStackTrace();
                }
            }
        }
    }
}
