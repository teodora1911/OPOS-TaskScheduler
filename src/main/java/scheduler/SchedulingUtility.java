package scheduler;

public class SchedulingUtility extends Thread {

    private TaskScheduler scheduler;
    public boolean terminated = false;

    public SchedulingUtility(TaskScheduler scheduler){
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        System.out.println("Scheduling thread started.");
        while(!terminated){
            SchedulingEvent executingEvent = scheduler.eventQueue.poll();
            if(executingEvent != null){
                executingEvent.execute();
                try{
                    Thread.sleep(1);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Scheduling thread finished!");
    }
}
