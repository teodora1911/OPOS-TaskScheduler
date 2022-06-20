package scheduler.task;

public class IllegalTaskStateException extends RuntimeException {

    public IllegalTaskStateException() { super(); }

    public IllegalTaskStateException(String message) { super(message); }
}
