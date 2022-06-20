package scheduler.task;

import application.Utility;
import javafx.application.Platform;
import javafx.scene.control.ProgressBar;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
public class PixelationTask extends SchedulableTask implements Serializable {

    public static int Step;
    public static String Extension;
    public String intermediateFolderPath;
    protected ArrayList<PixelationThread> threads;
    protected int processedRows = 0;
    protected transient Object processingLock = new Object();
    protected int totalRows = 0;

//    public PixelationTask() { super(); }

    public PixelationTask(int priority, long executionTime, LocalDateTime deadline, int parallelismDegree, String outputFolderPath, ArrayList<Resource> resources, ProgressBar progressBar, Consumer<SchedulableTask> updateProgress) {
        super(priority, TaskType.PIXELATION, executionTime, deadline, parallelismDegree, outputFolderPath, resources, progressBar, updateProgress);
        intermediateFolderPath = Utility.IntermediateFolderPath + File.separator + "task-" + this.id;
        File intermediateFolder = new File(intermediateFolderPath);
        if(!intermediateFolder.exists())
            intermediateFolder.mkdir();
        if(resources.size() > 1){
            this.parallelismDegree = Math.min(resources.size(), parallelismDegree);
        }
        threads = new ArrayList<>(this.parallelismDegree);
        for(int i = 0; i < this.parallelismDegree; ++i)
            threads.add(new PixelationThread());
        initializeThreads();
    }

    @Override
    public void run() {
        threads.forEach(t -> new Thread(t).start());
        started = true;
        System.out.println(this.toString() + " is running.");
        try{
            while(!isTerminated()){
                // check for pause
                System.out.println("Checking for user pause.");
                if(userToken.isPaused()){
                    threads.forEach(t -> t.token.pause());
                    synchronized (userToken.lock){
                        userToken.lock.wait();
                    }
                }
                System.out.println("Checking for scheduler pause.");
                if(schedulerToken.isPaused()){
                    threads.forEach(t -> t.token.pause());
                    synchronized (schedulerToken.lock){
                        schedulerToken.lock.wait();
                    }
                }
                synchronized (terminated) {
                    System.out.println("Initializing lastAccessed.");
                    lastAccessed = Instant.now();
                }
                // try to lock resources
                System.out.println("Acquiring resources!");
                boolean acquired;
                List<Resource> requiredResources;
                synchronized (resources){
                    requiredResources = resources.entrySet().stream().filter(r -> !r.getValue()).map(r -> r.getKey()).collect(Collectors.toList());
                }
                while(!(acquired = Utility.Scheduler.lockResources(requiredResources, this))){
                    if(isTerminated()){
                        // scheduler.unlockResources(this); - ako zadatak nije zaključao sve resurse, nije nijedan
                        threads.forEach(t -> t.token.resume());
                        Utility.Scheduler.cancelTask(this);
                        break;
                    }
                   // scheduler.unlockResources(this);
                }

                if(acquired){
                    System.out.println("Resources acquired.");
                    setTaskPriority(MaxPriority);
                    // release all threads
                    threads.forEach(t -> t.token.resume());
                    while(!isPaused()){
                        if(threads.stream().allMatch(t -> t.completed)){
                            Utility.Scheduler.unlockResources(this);
                            terminate();
                            Utility.Scheduler.completedTask(this);
                            if(resources.size() == 1 && threads.size() > 1){
                                System.out.println(this.toString() + " is copying data ...");
                                // ako se obrađivao jedan resurs od strane više niti, treba da se kopira iz intermediate u output
                                resources.forEach((r, v) -> resources.replace(r, true));
                                String resourceName = resources.keySet().iterator().next().name;
                                Path source = Paths.get(intermediateFolderPath + File.separator + "Pixelation-" + resourceName);
                                Path destination = Paths.get(outputFolderPath + File.separator + "Pixelated-T" + id + "-" + resourceName);
                                try{
                                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                                } catch (IOException e){
                                    e.printStackTrace();
                                }
                            }
                            // done processing
                            return;
                        }
                    }
                    threads.forEach(t -> t.token.pause());
                    // set priority back to original priority
                    setTaskPriority(getGivenPriority());
                    Utility.Scheduler.unlockResources(this);
                    synchronized (terminated) {
                        lastAccessed = null;
                    }
                } else {
                    // it is terminated and all resources are unlocked
                    // do nothing
                    System.out.println("Resources not acquired.");
                }
            }
            Utility.Scheduler.cancelTask(this);
        } catch (InterruptedException exc){
            exc.printStackTrace();
        }
    }

    private void initializeThreads() {
        for(Resource resource : resources.keySet()){
            try{
                totalRows += resource.getImage().getHeight();
            } catch (Exception exc){
                System.err.println("Problem with reading the image: " + resource.name);
            }
        }
        System.out.println("Total rows = " + totalRows);
        if(resources.size() == 1 && threads.size() > 1){ // if processing just one file with multiple threads
            int stepSize = (int)Math.ceil(Double.valueOf(totalRows) / threads.size());
            if(stepSize % Step != 0)
                stepSize += (Step - stepSize % Step);
            int currentRow = 0;
            for(PixelationThread thread : threads){
                thread.setParameters(intermediateFolderPath, Math.min(currentRow, totalRows), Math.min((currentRow += stepSize), totalRows), true, resources.keySet().iterator().next());
               // thread.setParent(this);
            }
        } else { // if processing multiple files with one or multiple threads or processing one file with one thread
            if(resources.size() <= threads.size()){
                int i = 0;
                for(Resource resource : resources.keySet()){
                    threads.get(i++).setParameters(intermediateFolderPath, 0, 0, false, resource);
                   // threads.get(i++).setParent(this);
                }
            } else {
                List<Integer> indexes = IntStream.range(0, resources.keySet().size()).boxed().collect(Collectors.toList());
                ArrayList<Resource> res = new ArrayList<>(resources.keySet());
                for(int i = 0; i < threads.size(); ++i){
                    final int finalI = i;
                    List<Integer> currentIndexes = indexes.stream().filter(num -> num % threads.size() == finalI).collect(Collectors.toList());
                    ArrayList<Resource> pr = new ArrayList<>();
                    for(Integer index : currentIndexes)
                        pr.add(res.get(index));
                    threads.get(i).setParameters(intermediateFolderPath, pr);
                    //threads.get(i).setParent(this);
                }
            }
        }
    }

    public static PixelationTask deserialize(String filename){
        PixelationTask task = null;
        try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))){
            task = (PixelationTask) in.readObject();
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }

        return task;
    }

    @Override
    public double getProgress() {
        synchronized (processingLock){
            if(totalRows == 0)
                return 0.0d;
            progress = Double.valueOf(processedRows).doubleValue() / totalRows;
           // System.out.println("progress = " + progress);
            return progress;
        }
    }

    @Override
    public void terminate(){
        super.terminate();
        // ako neke niti čekaju za nastavak izvršavanja
        threads.forEach(t -> t.token.resume());
    }

    @Override
    public void restore(ProgressBar progressBar, Consumer<SchedulableTask> updateProgress){
        super.restore(progressBar, updateProgress);
        this.processingLock = new Object();
        PixelationTask.Step = Utility.PixelationStepSize;
        PixelationTask.Extension = Utility.ResourceExtension;
        for(PixelationThread thread : threads)
            thread.token.lock = new Object();
    }
    private class PixelationThread extends Thread implements Serializable {
        public String intermediatePath;
        public int processingRow, endRow;
        public boolean singleResourceProcessing;
        public CancellationToken token = new CancellationToken();
        public boolean completed;
        // storage of original files (in Resource) and intermediate files (String)
        public HashMap<Resource, String> resources = new HashMap<>();
        // storage of original files and starting indexes of processing them
        public HashMap<Resource, Integer> indexes = new HashMap<>();

        public PixelationThread() {
            token.paused = true;
            this.completed = true;
        }

//        public PixelationThread(String intermediatePath, int beginRow, int endRow, boolean singleResourceProcessing, Resource... resources){
//            setParameters(intermediatePath, beginRow, endRow, singleResourceProcessing, resources);
//        }

        public void setParameters(String intermediatePath, int beginRow, int endRow, boolean singleResourceProcessing, Resource... resources){
            this.endRow = endRow;
            this.singleResourceProcessing = singleResourceProcessing;
            this.intermediatePath = intermediatePath;
            this.completed = false;
            if(singleResourceProcessing && resources.length > 1) { throw new IllegalArgumentException(); }
            for(Resource resource : resources){
                this.resources.put(resource, intermediatePath + File.separator + "Pixelation-" + resource.name);
                this.indexes.put(resource, singleResourceProcessing ? beginRow : 0);
            }
        }

        public void setParameters(String intermediatePath, ArrayList<Resource> resources){
            singleResourceProcessing = false;
            this.completed = false;
            this.intermediatePath = intermediatePath;
            for(Resource resource : resources){
                this.resources.put(resource, intermediatePath + File.separator + "Pixelation-" + resource.name);
                this.indexes.put(resource, 0);
            }
        }

        private class PixelLocation implements Serializable {
            public int i;
            public int j;
            public int value;

            public PixelLocation(int i, int j, int value){
                this.i = i;
                this.j = j;
                this.value = value;
            }

            // public PixelLocation() { }
        }

        @Override
        public void run() {
            System.out.println("Thread started!");
            if(completed)
                return;
            ArrayList<PixelLocation> processedPixels = new ArrayList<>();
            BufferedImage originalImage = null;
            for(Map.Entry<Resource, String> resource : resources.entrySet()){
                synchronized (PixelationTask.this.resources){
                    // ako je resurs već obrađen, nastavljamo sa sljedećim resursom
                    if(PixelationTask.this.resources.get(resource.getKey())){
                        Utility.Scheduler.unlockResource(resource.getKey(), PixelationTask.this);
                        continue;
                    }
                }
                try{
                    originalImage = ImageIO.read(new File(resource.getKey().path));
                    processingRow = indexes.get(resource.getKey());
                    if(!singleResourceProcessing)
                        endRow = resource.getKey().getImage().getHeight();
                    for(int y = processingRow; y < endRow && y < endRow; y += PixelationTask.Step, processingRow += PixelationTask.Step){
                        // provjera za pauziranje / terminaciju
                        if(token.isPaused()){
                            indexes.replace(resource.getKey(), processingRow);
                            synchronized (token.lock){
                                token.lock.wait();
                            }
                        }
                        if(isTerminated()){
                            save(processedPixels, resource.getKey(), resource.getValue(), null);
                            Utility.Scheduler.cancelTask(PixelationTask.this);
                            System.out.println("Thread [" + getId() + ", T" +PixelationTask.this.id + "] finished.");
                            return;
                        }
                        //System.out.println("started " + y);
                        for(int x = 0; x < originalImage.getWidth() && x < originalImage.getWidth(); x += PixelationTask.Step){
                            List<Color> colors = new ArrayList<>();
                            for(int i = y; i < y + PixelationTask.Step && i < endRow; ++i) {
                                for(int j = x; j < x + PixelationTask.Step && j < originalImage.getWidth(); ++j)
                                    colors.add(new Color(originalImage.getRGB(j, i)));
                            }

                            double averageRed = colors.stream().mapToInt(e -> e.getRed()).average().getAsDouble();
                            double averageGreen = colors.stream().mapToInt(e -> e.getGreen()).average().getAsDouble();
                            double averageBlue = colors.stream().mapToInt(e -> e.getBlue()).average().getAsDouble();

                            Color averageColor = new Color((int)averageRed, (int)averageGreen, (int)averageBlue);

                            for (int i = y; i < y + PixelationTask.Step && i < endRow; ++i)
                                for (int j = x; j < x + PixelationTask.Step && j < originalImage.getWidth(); ++j)
                                    processedPixels.add(new PixelLocation(i, j, averageColor.getRGB()));
                        }
                        // update progress
                        synchronized (PixelationTask.this.processingLock){
                            PixelationTask.this.processedRows += PixelationTask.Step;
                        }
                        Platform.runLater(() -> PixelationTask.this.updateProgress.accept(PixelationTask.this));
                        // TODO: Update this
                        Thread.sleep(50);
                    }

                    // kada je jedan resurs obrađen, osloboditi ga
                    if(!singleResourceProcessing) {
                        synchronized (PixelationTask.this.resources) {
                            PixelationTask.this.resources.replace(resource.getKey(), true);
                            Utility.Scheduler.unlockResource(resource.getKey(), PixelationTask.this);
                        }
                        save(processedPixels, resource.getKey(), resource.getValue(), PixelationTask.this.outputFolderPath + File.separator + "Pixelated-" + "T" + PixelationTask.this.id + "-" + resource.getKey().name);
                    } else {
                        save(processedPixels, resource.getKey(), resource.getValue(), null);
                    }
                } catch (IOException | InterruptedException exc){
                    exc.printStackTrace();
                }
            }
            completed = true;
            System.out.println("Thread [" + getId() + ", T" +PixelationTask.this.id + "] finished.");
        }

        private void save(ArrayList<PixelLocation> processedPixels, Resource resource, String intermediate, String output) {
            if(processedPixels.size() < 1 || resource == null)
                return;
            BufferedImage processingImage;
            if(singleResourceProcessing)
                readWriteLock.lock();
            try{
                if(intermediate == null || !(new File(intermediate).exists()))
                    processingImage = ImageIO.read(new File(resource.path));
                else
                    processingImage = ImageIO.read(new File(intermediate));
                for(PixelLocation pixel : processedPixels)
                    processingImage.setRGB(pixel.j, pixel.i, pixel.value);
                File outputFile = new File(output == null ? intermediate : output);
                ImageIO.write(processingImage, PixelationTask.Extension, outputFile);
                processedPixels.clear();
            } catch (Exception exc){
                exc.printStackTrace();
            } finally {
                if(singleResourceProcessing)
                    readWriteLock.unlock();
            }
        }
    }
}