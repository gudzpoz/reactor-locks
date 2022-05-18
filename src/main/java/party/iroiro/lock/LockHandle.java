package party.iroiro.lock;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * A lock handle (including a {@link Mono<Void>} to listen to and a canceller function).
 */
public interface LockHandle {
    static LockHandle empty() {
        return new LockHandle() {
            final Mono<Void> empty = Mono.empty();

            @Override
            public boolean cancel() {
                return false;
            }

            @Override
            public Mono<Void> mono() {
                return empty;
            }
        };
    }

    static LockHandle from(Mono<Void> mono, Supplier<Boolean> canceller) {
        return new CancellableHandle(mono, canceller);
    }

    /**
     * Cancels a lock request
     *
     * <p>
     * If the lock is already acquired, it is equivalent to unlocking the lock (somehow).
     * So make sure that you never call it more than once.
     * </p>
     *
     * @return true if successfully cancelled (or else, you have acquired the lock,
     *         probably just raced with the cancel request)
     */
    boolean cancel();

    /**
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
