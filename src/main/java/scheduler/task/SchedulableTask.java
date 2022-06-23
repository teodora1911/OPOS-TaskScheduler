package scheduler.task;

import application.Utility;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class SchedulableTask extends Thread implements Serializable {
    public int id;
    public TaskType type;
    public ReentrantLock readWriteLock = new ReentrantLock();
    public Integer priority;
    public LocalDateTime deadline;
    public Long givenExecutionTime; // u milisekundama
    public Integer parallelismDegree;
    public TaskState state;
    public Boolean terminated;
    public transient boolean started = false;

    // vrijednost reprezentuje da li se uspješno obrađen resurs
    public HashMap<Resource, Boolean> resources = new HashMap<>();
    public String outputFolderPath;

    public PauseToken schedulerToken = new PauseToken();
    public PauseToken userToken = new PauseToken();

    public transient Instant lastAccessed;
    public long executingTime = 0; // u milisekundama
    public LocalDateTime dateTimeFinished;
    public Double progress = 0.0;
    public transient Consumer<SchedulableTask> updateProgress;

    public SchedulableTask(){
        id = Utility.Scheduler.nextTaskID++;
        setTaskState(TaskState.CREATED);
        this.terminated = false;
    }

    public SchedulableTask(int priority, TaskType type, long executionTime, LocalDateTime deadline, int parallelismDegree, String outputFolderPath, ArrayList<Resource> resources, Consumer<SchedulableTask> updateProgress){
        this();
        this.priority = priority;
        this.type = type;
        this.givenExecutionTime = executionTime;
        this.deadline = deadline;
        this.parallelismDegree = parallelismDegree;
        Path outputPath = Path.of(outputFolderPath);
        if(!Files.isDirectory(outputPath)){
            try{
                Files.createDirectories(outputPath);
            } catch (IOException exc){
                terminated = true;
            }
        }
        this.outputFolderPath = outputFolderPath;
        for(Resource resource : resources)
            this.resources.put(resource, false);
        this.updateProgress = updateProgress;
    }

    private boolean metDeadline() {
        // ako je prekoračio rok
        if(deadline.compareTo(LocalDateTime.now()) <= 0 || givenExecutionTime < executingTime){
            synchronized (state) { state = TaskState.TERMINATED; }
            terminated = true;
            return true;
        }
        // ako nema zabilježenog početnog vremena (nije krenuo ili je pauziran)
        if(lastAccessed == null)
            return false;
        Instant current = Instant.now();
        long elapsed = Duration.between(lastAccessed, current).toMillis();
        executingTime += elapsed;
        lastAccessed = current;

        if(givenExecutionTime < executingTime) {
            synchronized (state){ state = TaskState.TERMINATED; }
            terminated = true;
            return true;
        } else { return false; }
    }

    public int getTaskPriority(){
        return priority;
    }

    public void setTaskState(TaskState state){
        synchronized (state){
            this.state = state;
        }
    }

    public double getProgress() {
        synchronized (progress){
            return progress;
        }
    }

    public boolean isTerminated() {
        synchronized (terminated){
            return terminated || metDeadline();
        }
    }

    public void terminate() {
        Utility.Scheduler.unlockResources(this);
        synchronized (terminated){
            terminated = true;
            userToken.resume();
            schedulerToken.resume();
        }
    }

    public void pause(boolean user){
        Utility.Scheduler.unlockResources(this);
        synchronized (terminated){
            lastAccessed = null;
        }
        if(user) {
            userToken.pause();
            setTaskState(TaskState.PAUSED);
        }
        else {
            schedulerToken.pause();
            setTaskState(TaskState.READY);
        }
    }

    public void resume(boolean user){
        if(user && userToken.isPaused()){
            schedulerToken.pause();
            setTaskState(TaskState.READY);
            userToken.resume();
        } else if (!user && schedulerToken.isPaused()){
            setTaskState(TaskState.RUNNING);
            schedulerToken.resume();
        }
    }

    public boolean isPaused(){
        return isTerminated() || userToken.isPaused() || schedulerToken.isPaused();
    }

    @Override
    public boolean equals(Object object){
        if(this == object)
            return true;
        if(object == null)
            return false;
        if(getClass() != object.getClass())
            return false;

        SchedulableTask other = (SchedulableTask)object;
        return id == other.id;
    }

    @Override
    public int hashCode(){
        return Integer.valueOf(id).hashCode();
    }

    @Override
    public String toString(){
        return "Task [id: " + id + ", priority: " + getTaskPriority() + "]";
    }

    public void restore(Consumer<SchedulableTask> updateProgress){
        this.updateProgress = updateProgress;
        this.schedulerToken.lock = new Object();
        this.userToken.lock = new Object();
    }
    public void serialize(String path){
        try(ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))){
            out.writeObject(this);
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
