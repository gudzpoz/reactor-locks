package party.iroiro.lock;

import reactor.core.publisher.Mono;

/**
 * An reactive interface for <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">
 * reader–writer locks</a>.
 */
public abstract class RWLock extends Lock {

    /**
     * See {@link Lock#lock} for details.
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer party.iroiro.lock</a>.
     * </p>
     * <p>
     * The default implementation simply calls {@link Lock#lock}.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public abstract <T> Mono<T> rLock(Mono<T> mono);

    /**
     * See {@link Lock#unlock} for details.
     */
    public abstract void rUnlock();

    /**
     * See {@link Lock#unlockOnEmpty} for details.
     *
     * @param mono the {@link Mono}, of which the next signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnEmpty(Mono<T> mono) {
        return mono.switchIfEmpty(Mono.fromCallable(() -> {
            this.rUnlock();
            return null;
        }));
    }

    /**
     * See {@link Lock#unlockOnNext} for details.
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnNext(Mono<T> mono) {
        return mono.doOnNext(ignored -> this.rUnlock());
    }

    /**
     * See {@link Lock#unlockOnTerminate} for details.
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnTerminate(Mono<T> mono) {
        return mono.doOnTerminate(this::rUnlock);
    }

    /**
     * See {@link Lock#unlockOnError} for details.
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnError(Mono<T> mono) {
        return mono.doOnError(ignored -> this.rUnlock());
    }
}
