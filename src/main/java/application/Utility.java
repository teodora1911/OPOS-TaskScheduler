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

    // instance of scheduler
    public static TaskScheduler Scheduler;

    // resources data
    public static String InputFolderPath;
    public static String IntermediateFolderPath;
    public static String OutputFolderPath;
    public static String SerializedTasksFolderPath;

    // default values for scheduler
    public static Integer NumberOfConcurrentlyExecutingTasks;
    public static Integer NumberOfAvailableCores;

    // default values for task
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

        NumberOfConcurrentlyExecutingTasks = Integer.parseInt(properties.getProperty("default-parallel-tasks"));
        NumberOfAvailableCores = Integer.parseInt(properties.getProperty("default-cores"));

        PixelationTask.Extension = ResourceExtension = properties.getProperty("extension");
        PixelationTask.Step = PixelationStepSize = Integer.parseInt(properties.getProperty("pixelation-step"));
        TaskCounter = Integer.parseInt(properties.getProperty("counter-start"));
        DefaultTaskPriority = Integer.parseInt(properties.getProperty("default-priority"));
        MinTaskPriority = Integer.parseInt(properties.getProperty("min-priority"));
        DefaultTaskParallelismDegree = Integer.parseInt(properties.getProperty("default-parallelism-degree"));
        DefaultTaskExecutionTimeSec = Integer.parseInt(properties.getProperty("default-execution-time"));
    }

    public static void setScheduler(int availableCores, int maxConcurrentTasks, boolean preemptive){
        Scheduler = new TaskScheduler(availableCores, maxConcurrentTasks, preemptive);
        File inputFolder = new File(InputFolderPath);
        for(File file : inputFolder.listFiles()){
            //System.out.println(file.getAbsolutePath());
            Scheduler.resources.put(new Resource(file), null);
        }
    }
}