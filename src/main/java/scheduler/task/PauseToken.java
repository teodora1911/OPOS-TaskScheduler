package scheduler.task;

import java.io.Serializable;

public class PauseToken implements Serializable {

    protected boolean paused = false;
    public transient Object lock = new Object();

    public boolean isPaused() {
        synchronized (lock){
            return paused;
        }
    }

    public void pause() {
        synchronized (lock) {
            paused = true;
        }
    }

    public void resume(){
        synchronized (lock){
            paused = false;
            lock.notifyAll();
        }
    }
}
