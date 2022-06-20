package scheduler;

import scheduler.task.SchedulableTask;
import scheduler.task.TaskComparator;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScheduleTasksEvent extends SchedulingEvent {

    public ScheduleTasksEvent(TaskScheduler scheduler){
        super(scheduler);
    }

    @Override
    public void execute() {
        int currentlyExecutingTasksCount = scheduler.executingTasks.size();
        SchedulableTask minPriorityNonExecutingTask = scheduler.pendingTasks.peek();

        if(minPriorityNonExecutingTask == null){
           // System.out.println("minPriorityNonExecutingTask == null");
            return;
        }
        if(currentlyExecutingTasksCount < scheduler.maxParallelTasks && minPriorityNonExecutingTask.parallelismDegree <= scheduler.numberOfFreeCores){
            scheduler.numberOfFreeCores -= minPriorityNonExecutingTask.parallelismDegree;
            scheduler.executingTasks.add(minPriorityNonExecutingTask);
            if(minPriorityNonExecutingTask.started)
                minPriorityNonExecutingTask.resume(false);
            else 
                minPriorityNonExecutingTask.start();
            scheduler.pendingTasks.poll();
        } else if (scheduler.preemptive){
            List<SchedulableTask> lowerPriorityExecutingTasks = scheduler.executingTasks.stream().filter(t -> t.getTaskPriority() > minPriorityNonExecutingTask.getTaskPriority()).collect(Collectors.toList());
            if(!lowerPriorityExecutingTasks.isEmpty() && lowerPriorityExecutingTasks.stream().mapToInt(t -> t.parallelismDegree).sum() >= minPriorityNonExecutingTask.parallelismDegree){
                lowerPriorityExecutingTasks.sort(new TaskComparator(true));
                int currentNumberOfCores = 0;
                for(SchedulableTask lowerPriorityTask : lowerPriorityExecutingTasks){
                    currentNumberOfCores += lowerPriorityTask.parallelismDegree;
                    lowerPriorityTask.pause(false);
                    scheduler.executingTasks.remove(lowerPriorityTask);
                    scheduler.pendingTasks.offer(lowerPriorityTask);
                    if(currentNumberOfCores >= minPriorityNonExecutingTask.parallelismDegree){
                        scheduler.numberOfFreeCores += (currentNumberOfCores - minPriorityNonExecutingTask.parallelismDegree);
                        break;
                    }
                }
                scheduler.pendingTasks.poll();
                scheduler.executingTasks.add(minPriorityNonExecutingTask);
                if(minPriorityNonExecutingTask.started)
                    minPriorityNonExecutingTask.resume(false);
                else
                    minPriorityNonExecutingTask.start();
            }
        }

        Collection<SchedulableTask> allTasks = Stream.of(scheduler.executingTasks, scheduler.pendingTasks, scheduler.userPausedTasks).flatMap(Collection::stream).collect(Collectors.toList());
        scheduler.sendTasks.accept(allTasks);
    }
}
