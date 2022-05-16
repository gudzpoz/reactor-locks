package party.iroiro.lock;

import reactor.core.publisher.Mono;

/**
 * A reactive interface for simple locks (and probably semaphores)
 */
public abstract class Lock {
    /**
     * Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::<b>party.iroiro.lock</b>)
     *         /* Some processing &#42;/
     *         .transform(party.iroiro.lock::unlock)
     *         .block();
     * </code></pre>
     *
     * <p>
     * The underlying implementation should automatically queue the {@link Mono} up
     * if the party.iroiro.lock is not available.
     * </p>
     * <p>
     * When the party.iroiro.lock becomes available, the value will be automatically propagated downstream.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public abstract <T> Mono<T> lock(Mono<T> mono);

    /**
     * Unlocks. Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::party.iroiro.lock)
     *         .flatMap(item -> {
     *             /* Some processing &#42;/
     *             party.iroiro.lock.<b>unlock</b>();
     *             return Mono.just(item);
     *         })
     *         .block();
     * </code></pre>
     */
    public abstract void unlock();

    /**
     * Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::party.iroiro.lock)
     *         /* Some processing &#42;/
     *         .transform(party.iroiro.lock::<b>unlockOnTerminate</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnTerminate} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnTerminate(Mono<T> mono) {
        return mono.doOnTerminate(this::unlock);
    }

    /**
     * Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::party.iroiro.lock)
     *         /* Some processing &#42;/
     *         .transform(party.iroiro.lock::<b>unlockOnNext</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnNext} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnNext(Mono<T> mono) {
        return mono.doOnNext(ignored -> this.unlock());
    }

    /**
     * Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::party.iroiro.lock)
     *         /* Some processing &#42;/
     *         .transform(party.iroiro.lock::<b>unlockOnEmpty</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnNext} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnEmpty(Mono<T> mono) {
        return mono.switchIfEmpty(Mono.fromCallable(() -> {
            this.unlock();
            return null;
        }));
    }

    /**
     * Usage:
     * <pre><code>
     *     mono
     *         .transform(party.iroiro.lock::party.iroiro.lock)
     *         /* Some processing &#42;/
     *         .transform(party.iroiro.lock::<b>unlockOnError</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnNext} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnError(Mono<T> mono) {
        return mono.doOnError(ignored -> this.unlock());
    }
}
