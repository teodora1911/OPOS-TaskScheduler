package application;

import scheduler.TaskScheduler;
import scheduler.task.PixelationTask;
import scheduler.task.Resource;
import scheduler.task.SchedulableTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Utility {

    public static TaskScheduler Scheduler;

    // putanje do foldera u kojima se nalaze/smještaju resursi
    public static String InputFolderPath;
    public static String IntermediateFolderPath;
    public static String OutputFolderPath;
    // putanja serijalizovanih zadataka
    public static String SerializedTasksFolderPath;
    public static String AutosavedSchedulerPath;

    // podrazumijevane vrijednosti raspoređivača
    public static Integer NumberOfConcurrentlyExecutingTasks;
    public static Integer NumberOfAvailableCores;

    // podrazumijevane vrijednosti za zadatake
    public static Integer DefaultTaskPriority;
    public static Integer MinTaskPriority;
    public static Integer DefaultTaskParallelismDegree;
    public static Integer DefaultTaskExecutionTimeSec;
    public static Integer PixelationStepSize;
    public static String ResourceExtension;
    public static Integer TaskCounter;

    private Utility() { }

    public static void loadPropertiesFile(String propertiesFile) throws IOException {
        InputStream input = new FileInputStream(propertiesFile);
        Properties properties = new Properties();
        properties.load(input);

        InputFolderPath = properties.getProperty("input");
        OutputFolderPath = properties.getProperty("output");
        IntermediateFolderPath = properties.getProperty("intermediate");
        SerializedTasksFolderPath = properties.getProperty("tasks");
        AutosavedSchedulerPath = properties.getProperty("scheduler-autosaved");

        NumberOfConcurrentlyExecutingTasks = Integer.parseInt(properties.getProperty("default-parallel-tasks"));
        NumberOfAvailableCores = Integer.parseInt(properties.getProperty("default-cores"));

        PixelationTask.Extension = ResourceExtension = properties.getProperty("extension");
        PixelationTask.Step = PixelationStepSize = Integer.parseInt(properties.getProperty("pixelation-step"));
        DefaultTaskPriority = Integer.parseInt(properties.getProperty("default-priority"));
        MinTaskPriority = Integer.parseInt(properties.getProperty("min-priority"));
        DefaultTaskParallelismDegree = Integer.parseInt(properties.getProperty("default-parallelism-degree"));
        DefaultTaskExecutionTimeSec = Integer.parseInt(properties.getProperty("default-execution-time"));
    }

    public static void setScheduler(int availableCores, int maxConcurrentTasks, boolean preemptive){
        if(Scheduler == null){
            System.out.println("Making new scheduler.");
            Scheduler = new TaskScheduler(availableCores, maxConcurrentTasks, preemptive);
            File inputFolder = new File(InputFolderPath);
            for(File file : inputFolder.listFiles())
                Scheduler.resources.put(new Resource(file), null);
        } else {
            System.out.println("Scheduler is already assigned.");
        }
    }
}