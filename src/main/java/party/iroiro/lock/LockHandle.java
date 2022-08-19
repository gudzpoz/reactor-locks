package party.iroiro.lock;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * A lock handle (including a {@link Mono} to listen to and a canceller function).
 */
public interface LockHandle {
    static LockHandle empty() {
        return EmptyLockHandle.instance();
    }

    static LockHandle from(Mono<Void> mono, Supplier<Boolean> canceller) {
        return new CancellableHandle(mono, canceller);
    }

    /**
     * Attempts to cancels a lock request
     *
     * @return <code>true</code> if successfully cancelled<br>
     *         <code>false</code> if you have acquired the lock, probably just raced with the cancel request
     */
    boolean cancel();

    /**
     * Returns a {@link Mono} that emits success only after acquiring the lock
     *
     * @return a {@link Mono} that emits success only after acquiring the lock
     */
    Mono<Void> mono();

    class CancellableHandle implements LockHandle {
        private final Mono<Void> mono;
        private final Supplier<Boolean> canceller;

        CancellableHandle(Mono<Void> mono, Supplier<Boolean> canceller) {
            this.mono = mono;
            this.canceller = canceller;
        }

        public boolean cancel() {
            return canceller.get();
        }

        public Mono<Void> mono() {
            return mono;
        }
    }
}
