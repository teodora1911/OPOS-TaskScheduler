package scheduler.task;

import application.Utility;

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
    public ArrayList<PixelationThread> threads;
    public int processedRows = 0;
    public transient Object processingLock = new Object();
    public int totalRows = 0;

    public PixelationTask(int priority, long executionTime, LocalDateTime deadline, int parallelismDegree, String outputFolderPath, ArrayList<Resource> resources, Consumer<SchedulableTask> updateProgress) {
        super(priority, TaskType.PIXELATION, executionTime, deadline, parallelismDegree, outputFolderPath, resources, updateProgress);
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
                // provjerava da li je pauziran
               // System.out.println("Checking for user pause.");
                if(userToken.isPaused()){
                    threads.forEach(t -> t.token.pause());
                    synchronized (userToken.lock){
                        userToken.lock.wait();
                    }
                }
               // System.out.println("Checking for scheduler pause.");
                if(schedulerToken.isPaused()){
                    threads.forEach(t -> t.token.pause());
                    synchronized (schedulerToken.lock){
                        schedulerToken.lock.wait();
                    }
                }
                synchronized (terminated) {
                   // System.out.println("Initializing lastAccessed.");
                    lastAccessed = Instant.now();
                }
                // zaključavanje svih resursa koje zadatak još nije obradio
                boolean acquired;
                List<Resource> requiredResources;
                synchronized (resources){
                    requiredResources = resources.entrySet().stream().filter(r -> !r.getValue()).map(r -> r.getKey()).collect(Collectors.toList());
                }
                // ako je lista zahtjevanih resursa prazna, to znači da su obrađeni svi resursi
                if(requiredResources.isEmpty()){
                    finish();
                    return;
                }
                while(!(acquired = Utility.Scheduler.lockResources(requiredResources, this))){
                    if(isPaused())
                        break;
                    try{
                        Thread.sleep(1);
                    } catch (InterruptedException exc){
                        System.err.println(exc.getMessage());
                    }
                }

                if(acquired){
                   // System.out.println("Resources acquired.");
                    // obavijesti sve niti za nastavak obrade
                    threads.forEach(t -> t.token.resume());
                    while(!isPaused()){ // terminated == false && isPaused() == false
                        if(threads.stream().allMatch(t -> t.completed)){
                            finish();
                            return;
                        }
                    }
                }
            }
            Utility.Scheduler.cancelTask(this);
        } catch (InterruptedException exc){
            exc.printStackTrace();
        }
        System.out.println("Task is finished.");
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
        if(resources.size() == 1 && threads.size() > 1){ // paralelna obrada nad jednim fajlom
            int stepSize = (int)Math.ceil(Double.valueOf(totalRows) / threads.size());
            if(stepSize % Step != 0)
                stepSize += (Step - stepSize % Step);
            int currentRow = 0;
            for(PixelationThread thread : threads){
                thread.setParameters(intermediateFolderPath, Math.min(currentRow, totalRows), Math.min((currentRow += stepSize), totalRows), true, resources.keySet().iterator().next());
            }
        } else { // obrada više fajlova (jedna ili više niti)
            if(resources.size() <= threads.size()){
                int i = 0;
                for(Resource resource : resources.keySet()){
                    threads.get(i++).setParameters(intermediateFolderPath, 0, 0, false, resource);
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
                }
            }
        }
    }

    private void finish(){
        dateTimeFinished = LocalDateTime.now();
        System.out.println("In finish() method!");
        terminate();
        Utility.Scheduler.completedTask(this);
        if(resources.size() == 1 && threads.size() > 1){
            System.out.println(this.toString() + " is copying data ...");
            // ako se obrađivao jedan resurs od strane više niti, treba da se kopira iz intermediate u output folder
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
    public void pause(boolean user){
        super.pause(user);
        threads.forEach(t -> t.token.pause());
    }

    @Override
    public void restore(Consumer<SchedulableTask> updateProgress){
        super.restore(updateProgress);
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
        public PauseToken token = new PauseToken();
        public boolean completed;

        // ključ je resurs (u kom se nalazi originalni fajl)
        // vrijednost je putanja do fajla u kom će se smjestiti djelimično obrađen resurs
        public HashMap<Resource, String> resources = new HashMap<>();

        // vrijednost je redni broj reda kojeg data nit obrađuje
        public HashMap<Resource, Integer> indexes = new HashMap<>();

        public PixelationThread() {
            token.paused = true;
            this.completed = true;
        }

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
                            System.out.println("Terminated? " + PixelationTask.this.terminated);
                            return;
                        }
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
                        // ažuriraj stanje obrade
                        synchronized (PixelationTask.this.processingLock){
                            PixelationTask.this.processedRows += PixelationTask.Step;
                        }
                        if(PixelationTask.this.updateProgress != null)
                            PixelationTask.this.updateProgress.accept(PixelationTask.this);
                        Thread.sleep(100);
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