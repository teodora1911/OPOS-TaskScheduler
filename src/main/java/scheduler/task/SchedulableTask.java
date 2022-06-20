package scheduler.task;

import application.Utility;
import javafx.scene.control.ProgressBar;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class SchedulableTask extends Thread implements Serializable {

    public static final int MaxPriority = 1;
    public int id;
    protected TaskType type;
    protected ReentrantLock readWriteLock = new ReentrantLock();  // za upisivanje / čitanje resursa od strane više niti
    protected Integer givenPriority;  // originalni prioritet koji je korisnik specifikovao
    protected Integer priority;       // priotitet koji ima kada zauzme resurs(e) - PCP
    protected LocalDateTime deadline;
    protected Long givenExecutionTime; // u milisekundama
    public Integer parallelismDegree;
    protected TaskState state;
    protected Boolean terminated = false;
    public boolean started = false;

    // vrijednost mape reprezentuje da li se uspjesno obradio resurs
    protected HashMap<Resource, Boolean> resources = new HashMap<>();
    public String outputFolderPath;

    protected CancellationToken schedulerToken = new CancellationToken();
    protected CancellationToken userToken = new CancellationToken();

    public transient Instant lastAccessed;
    protected long executingTime = 0; // u milisekundama
    protected Double progress = 0.0;
    public transient ProgressBar progressBar;
    protected transient Consumer<SchedulableTask> updateProgress;

    public SchedulableTask(){
        id = Utility.TaskCounter++;
        setTaskState(TaskState.CREATED);
        this.terminated = false;
    }

    public SchedulableTask(int priority, TaskType type, long executionTime, LocalDateTime deadline, int parallelismDegree, String outputFolderPath, ArrayList<Resource> resources, ProgressBar progressBar, Consumer<SchedulableTask> updateProgress){
        this();
        this.givenPriority = priority;
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
        this.progressBar = progressBar;
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

    public void setTaskPriority(int priority){
        synchronized (this.priority){
            this.priority = priority;
        }
    }

    public int getTaskPriority(){
        synchronized (priority){
            return this.priority;
        }
    }

    public int getGivenPriority(){
        return givenPriority;
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
        synchronized (terminated){
            if(!terminated)
                terminated = true;
            else
                System.out.println(this.toString() + " is already terminated!");
            userToken.resume();
            schedulerToken.resume();
            Utility.Scheduler.unlockResources(this);
        }
    }

    public void pause(boolean user){
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

    public void restore(ProgressBar progressBar, Consumer<SchedulableTask> updateProgress){
        this.progressBar = progressBar;
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

    public static SchedulableTask deserialize(String filename){
        SchedulableTask task = null;
        try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))){
            task = (SchedulableTask) in.readObject();
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }

        return task;
    }
}
