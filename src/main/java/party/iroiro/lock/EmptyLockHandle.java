package party.iroiro.lock;

import reactor.core.publisher.Mono;

/**
 * An empty lock handle that emits immediately and never gets cancelled
 */
class EmptyLockHandle implements LockHandle {
    private final static LockHandle instance = new EmptyLockHandle();
    private final Mono<Void> empty = Mono.empty();

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public Mono<Void> mono() {
        return empty;
    }

    /**
     * @return a cached instance of the empty handle
     */
    static LockHandle instance() {
        return instance;
    }
}
