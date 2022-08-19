package party.iroiro.lock.util;

/**
 * An empty exception signifying the cancellation of a lock request
 */
public class LockCancellationException extends Exception {
    private final static LockCancellationException INSTANCE = new LockCancellationException();

    /**
     * @return a static instance of the exception
     */
    public static LockCancellationException instance() {
        return INSTANCE;
    }
}
