package party.iroiro.lock;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A reactive interface for simple locks (and probably semaphores)
 */
public interface Lock {
    /**
     * Immediately requests to hold the lock.
     *
     * <p>
     * See {@link LockHandle}. Subscribe to {@link LockHandle#mono()} to get informed
     * when the lock becomes available.
     * </p>
     * <p>
     * To properly unlock, you are suggested to handle all cases including completing
     * without error, failing with errors or the whole {@link Mono} or {@link Flux} getting
     * cancelled. You might want to use {@link #withLock(Supplier)} to save yourself from
     * the boilerplate, which internally uses {@link Flux#using(Callable, Function, Consumer)}
     * to {@link LockHandle#cancel()} or {@link #unlock()} accordingly.
     * </p>
     *
     * @return a lock handle
     */
    LockHandle tryLock();

    /**
     * Tries to acquire the lock, or stop and propagate a {@link TimeoutException} downstream after
     * certain duration.
     *
     * @param duration the time to wait for lock
     * @return a {@link Mono} that emits success when the lock is acquired
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     */
    @Deprecated
    Mono<Void> tryLock(Duration duration);

    /**
     * Get a {@link Mono} that emits success only after acquiring the lock
     *
     * @return a {@link Mono} that emits success when the lock is acquired
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .flatMap(t -&gt; lock.<b>lock()</b>.thenReturn(t))
     *         /* Some processing &#42;/
     *         .transform(lock::unlockOnNext)
     *         .block();
     * </code></pre>
     *
     * <p>
     * The underlying implementation should automatically queue the {@link Mono} up
     * if the lock is not available.
     * </p>
     * <p>
     * <b>Do not</b> use {@link Mono#timeout(Duration)} or {@link Mono#timeout(Publisher)},
     * which are not handled at all. Use {@link #tryLock(Duration)} or {@link #tryLock()} instead
     * if you want timeouts.
     * </p>
     */
    @Deprecated
    Mono<Void> lock();

    /**
     * Automatically acquires the lock, executes the function and unlocks.
     *
     * <p>
     * It handles all cases including when the {@link Flux} completes without error (empty or not),
     * fails with errors, or gets cancelled middle way.
     * </p>
     *
     * @param scoped a {@link Publisher} supplier to be run with the lock held
     * @return a {@link Flux} containing values produces by the {@link Publisher} returned by the function
     * @param <T> the flowing data type
     */
    <T> Flux<T> withLock(Supplier<Publisher<T>> scoped);

    /**
     * Checks whether this lock is locked (or has reached the max lock holders)
     *
     * <p>
     * You should not rely on the result of this due to possible concurrent operations.
     * </p>
     *
     * @return whether this lock is locked
     */
    boolean isLocked();

    /**
     * Try to acquire the lock on the next element before propagating
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::<b>lockOnNext</b>)
     *         /* Some processing &#42;/
     *         .transform(lock::unlockOnNext)
     *         .block();
     * </code></pre>
     *
     * <p>
     * The underlying implementation should automatically queue the {@link Mono} up
     * if the lock is not available.
     * </p>
     * <p>
     * When the lock becomes available, the value will be automatically propagated downstream.
     * </p>
     */
    @Deprecated
    <T> Mono<T> lockOnNext(Mono<T> mono);

    /**
     * Unlocks
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lockOnNext)
     *         .flatMap(item -&gt; {
     *             /* Some processing &#42;/
     *             lock.<b>unlock</b>();
     *             return Mono.just(item);
     *         })
     *         .block();
     * </code></pre>
     */
    void unlock();

    /**
     * Release the lock with {@link Mono#doOnTerminate(Runnable)} before propagating
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lockOnNext)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnTerminate</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnTerminate(Runnable)} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     */
    @Deprecated
    <T> Mono<T> unlockOnTerminate(Mono<T> mono);

    /**
     * Release the lock with {@link Mono#doOnNext(Consumer)} before propagating
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lockOnNext)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnNext</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnNext(Consumer)} to ensure the execution order and handling of
     * a successful {@link Mono} with a emitted value.
     * </p>
     */
    @Deprecated
    <T> Mono<T> unlockOnNext(Mono<T> mono);

    /**
     * Release the lock with {@link Mono#switchIfEmpty(Mono)}
     *
     * @param mono the {@link Mono}, of which the signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lockOnNext)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnEmpty</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#switchIfEmpty(Mono)} to ensure the execution order and handling of
     * an empty {@link Mono}.
     * </p>
     */
    @Deprecated
    <T> Mono<T> unlockOnEmpty(Mono<T> mono);

    /**
     * Release the lock with {@link Mono#doOnError(Consumer)} before propagating
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     * @deprecated Use {@link #withLock(Supplier)} to handle cancelling signals
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lockOnNext)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnError</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnError(Consumer)} to ensure the execution order and handling of
     * {@link Mono}s with an error.
     * </p>
     */
    @Deprecated
    <T> Mono<T> unlockOnError(Mono<T> mono);
}
