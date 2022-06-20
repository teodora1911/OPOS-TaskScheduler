package scheduler;

public class SchedulingUtility extends Thread {

    private TaskScheduler scheduler;
    private boolean terminated = false;

    public SchedulingUtility(TaskScheduler scheduler){
        this.scheduler = scheduler;
    }

    public synchronized boolean isTerminated(){
        return terminated;
    }

    public synchronized void setTerminated(boolean terminated){
        this.terminated = terminated;
    }

    @Override
    public void run() {
        System.out.println("Scheduling thread started.");
        while(!isTerminated()){
            // maybe check if scheduler started
            SchedulingEvent executingEvent = scheduler.eventQueue.poll();
            if(executingEvent != null){
                // TODO: Delete this
//                if(executingEvent instanceof AddTaskEvent)
//                    System.out.println("Adding task!");
//                if (executingEvent instanceof CancelTaskEvent)
//                    System.out.println("Terminating task!");
//                if(executingEvent instanceof ScheduleTasksEvent)
//                    System.out.println("Schedule tasks!");
//                if(executingEvent instanceof UserPauseTaskEvent)
//                    System.out.println("User paused task!");
//                if(executingEvent instanceof TaskCompletedEvent)
//                    System.out.println("Task completed!");
                executingEvent.execute();
                try{
                    Thread.sleep(5);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }
}
