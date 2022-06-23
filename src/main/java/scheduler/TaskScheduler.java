package scheduler;

import application.Utility;
import scheduler.task.SchedulableTask;
import scheduler.task.Resource;
import scheduler.task.TaskComparator;

import java.io.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaskScheduler implements Serializable {

    public int numberOfAvailableCores;
    public int numberOfFreeCores;
    public int maxParallelTasks;
    public boolean preemptive;
    public transient boolean started;
    public int nextTaskID;

    LinkedBlockingQueue<SchedulingEvent> eventQueue = new LinkedBlockingQueue<>();

    ArrayList<SchedulableTask> userPausedTasks = new ArrayList<>(10);
    PriorityBlockingQueue<SchedulableTask> pendingTasks = new PriorityBlockingQueue<>(10, new TaskComparator(false));
    ArrayList<SchedulableTask> executingTasks;
    public HashMap<Resource, SchedulableTask> resources = new HashMap<>();

    protected transient SchedulingUtility schedulingThread;
    protected transient CancellationTasksThread cancellationThread;

    protected transient AutosaveThread autosaveThread;
    protected transient Consumer<List<SchedulableTask>> update;

    public TaskScheduler(int coresAvailable, int maxParallelTasks, boolean preemptive){
        this.numberOfAvailableCores = coresAvailable;
        this.numberOfFreeCores = coresAvailable;
        this.maxParallelTasks = maxParallelTasks;
        this.preemptive = preemptive;
        this.executingTasks = new ArrayList<>(maxParallelTasks);
        this.nextTaskID = 0;
        start();
    }

    public void start() {
        if(!started){
            System.out.println("In started method");
            schedulingThread = new SchedulingUtility(this);
            cancellationThread = new CancellationTasksThread();
            autosaveThread = new AutosaveThread();
            schedulingThread.setDaemon(true);
            cancellationThread.setDaemon(true);
            autosaveThread.setDaemon(true);
            schedulingThread.start();
            cancellationThread.start();
            autosaveThread.start();
            started = true;
        }
    }

    /*
     * Metode koje se pozivaju iz GUI ili
     * zadaci pozivaju ukoliko se desi neka promjena
     */
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

    public void setUpdate(Consumer<List<SchedulableTask>> update){ this.update = update; }

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

    // otključava sve resurse koje je zadatak zaključao
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

    // otključava specifikovani resurs
    public void unlockResource(Resource resource, SchedulableTask task){
        if(task == null || resource == null)
            return;
        synchronized (resources){
            SchedulableTask taskHoldingResource = resources.get(resource);
            if(taskHoldingResource != null && taskHoldingResource.equals(task))
                resources.replace(resource, null);
        }
    }

//    public void cancel(){
//        schedulingThread.terminated = true;
//        cancellationThread.terminated = true;
//    }

    // pokreće sve zadatke i kontrolne niti
    public void restore(Consumer<List<SchedulableTask>> update, Consumer<SchedulableTask> changeProgress, Consumer<SchedulableTask> register){
        eventQueue.forEach(event -> {
            event.scheduler = TaskScheduler.this;
            if(event instanceof AddTaskEvent){
                AddTaskEvent addEvent = (AddTaskEvent) event;
                restoreTask(addEvent.taskToAdd, changeProgress, register, true);
            } else if (event instanceof CancelTaskEvent){
                CancelTaskEvent cancelEvent = (CancelTaskEvent) event;
                restoreTask(cancelEvent.taskToCancel, changeProgress, register, false);
            } else if (event instanceof SerializeTaskEvent){
                SerializeTaskEvent serializeEvent = (SerializeTaskEvent) event;
                restoreTask(serializeEvent.taskToSerialize, changeProgress, register, false);
            } else if (event instanceof TaskCompletedEvent){
                TaskCompletedEvent completedEvent = (TaskCompletedEvent) event;
                restoreTask(completedEvent.completedTask, changeProgress, register, false);
            } else if (event instanceof UserPauseTaskEvent){
                UserPauseTaskEvent userPauseEvent = (UserPauseTaskEvent) event;
                restoreTask(userPauseEvent.taskToPause, changeProgress, register, false);
            } else if (event instanceof UserResumeTaskEvent){
                UserResumeTaskEvent userResumeEvent = (UserResumeTaskEvent) event;
                restoreTask(userResumeEvent.taskToResume, changeProgress, register, false);
            } else {
                System.out.println("Event not supported!");
            }
        });
        setUpdate(update);
        getAllTasks().forEach(t -> restoreTask(t, changeProgress, register, false));
        start();
    }

    private void restoreTask(SchedulableTask task, Consumer<SchedulableTask> changeProgress, Consumer<SchedulableTask> register, boolean schedulePause){
        if(task != null && !started){
            register.accept(task);
            task.restore(changeProgress);
            if(schedulePause)
                task.pause(false);
            new Thread(task).start();
        }
    }

    protected List<SchedulableTask> getAllTasks() {
        return Stream.of(executingTasks, pendingTasks, userPausedTasks).flatMap(Collection::stream).distinct().collect(Collectors.toList());
    }

    public void serialize(String path){
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))){
           // System.out.println("Writing task scheduler.");
            out.writeObject(this);
        } catch (IOException exc){
            exc.printStackTrace();
        }
    }

    public static TaskScheduler deserialize(String path){
        TaskScheduler scheduler = null;
        try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))){
            scheduler = (TaskScheduler) in.readObject();
            System.out.println("Deserialization successful.");
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }

        return scheduler;
    }

    private class CancellationTasksThread extends Thread {

        public boolean terminated = false;
        @Override
        public void run(){
            System.out.println("Cancellation thread started.");
            while(!terminated){
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
            System.out.println("Cancellation thread finished!");
        }
    }

    private class AutosaveThread extends Thread {

        public static final String FILENAME = "TaskScheduler";
        @Override
        public void run() {
            System.out.println("Autosave thread started.");
            while(true){
                try{
                    File serializedFolder = new File(Utility.AutosavedSchedulerPath);
                    File[] oldSerializedSchedulerFiles = serializedFolder.listFiles();
                    TaskScheduler.this.serialize(Utility.AutosavedSchedulerPath + File.separator + FILENAME + "_" + System.currentTimeMillis() + ".ser");
                    for(File f : oldSerializedSchedulerFiles)
                        f.delete();

                    Thread.sleep(200);
                } catch (Exception exc){
                    exc.printStackTrace();
                }
            }
        }
    }
}
