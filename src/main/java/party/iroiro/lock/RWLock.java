package party.iroiro.lock;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * A reactive interface for <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">
 * reader–writer locks</a>.
 */
public interface RWLock extends Lock {
    /**
     * The reader lock equivalent to {@link #tryLock()}
     *
     * <p>
     * See {@link LockHandle} and {@link #tryLock()}
     * </p>
     *
     * @return a lock handle
     */
    LockHandle tryRLock();

    /**
     * Tries to acquire the reader-lock, or stop and propagate a {@link TimeoutException}
     * downstream after certain duration.
     *
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * @param duration the time to wait for lock
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    @Deprecated
    Mono<Void> tryRLock(Duration duration);

    /**
     * The reader lock version of {@link #withLock(Supplier)}.
     *
     * @param scoped the function to get executed with the lock held
     * @return a {@link Flux} containing values produces by the {@link Publisher} returned by the function
     * @param <T> the flowing data type
     */
    <T> Flux<T> withRLock(Supplier<Publisher<T>> scoped);

    /**
     * See {@link AbstractLock#lock} for details.
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer lock</a>.
     * </p>
     * <p>
     * <b>Do not</b> use {@link Mono#timeout(Duration)} or {@link Mono#timeout(Publisher)},
     * which are not handled at all. Use {@link #tryRLock(Duration)} or {@link #tryRLock()} instead
     * if you want timeouts.
     * </p>
     *
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    @Deprecated
    Mono<Void> rLock();

    /**
     * Checks whether this lock is reader-locked (or has reached the max lock holders)
     *
     * <p>
     * You should not rely on the result of this due to possible concurrent operations.
     * </p>
     *
     * @return whether this lock is reader-locked
     */
    boolean isRLocked();

    /**
     * Tries to acquire the reader lock on the next element before propagating
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer lock</a>.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    @Deprecated
    <T> Mono<T> rLockOnNext(Mono<T> mono);

    /**
     * See {@link AbstractLock#unlock} for details.
     */
    void rUnlock();

    /**
     * See {@link AbstractLock#unlockOnEmpty(Mono)} for details.
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * @param mono the {@link Mono}, of which the next signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    @Deprecated
    <T> Mono<T> rUnlockOnEmpty(Mono<T> mono);

    /**
     * See {@link AbstractLock#unlockOnNext(Mono)} for details.
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    @Deprecated
    <T> Mono<T> rUnlockOnNext(Mono<T> mono);

    /**
     * See {@link AbstractLock#unlockOnTerminate(Mono)} for details.
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    @Deprecated
    <T> Mono<T> rUnlockOnTerminate(Mono<T> mono);

    /**
     * See {@link AbstractLock#unlockOnError(Mono)} for details.
     *
     * @deprecated Use {@link #withRLock(Supplier)} to handle cancelling signals
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    @Deprecated
    <T> Mono<T> rUnlockOnError(Mono<T> mono);
}
