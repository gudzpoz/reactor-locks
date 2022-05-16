package party.iroiro.lock;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * An implementation of {@link Lock} that allows multiple lock holders with an upper limit
 * (that is, it is the reactive version of {@link java.util.concurrent.Semaphore}).
 */
public class ReactiveSemaphore extends Lock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> queue;
    private final int limit;
    private int count;

    public ReactiveSemaphore(int limit) {
        this.limit = limit;
        count = 0;
        queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public <T> Mono<T> lock(Mono<T> mono) {
        return mono.flatMap(t -> {
            synchronized (this) {
                if (count < limit) {
                    count++;
                    return Mono.just(t);
                } else {
                    return SinkUtils.queue(queue, t);
                }
            }
        });
    }

    public void unlock() {
        synchronized (this) {
            Sinks.Empty<Void> next = queue.poll();
            if (next == null) {
                count--;
            } else {
                next.tryEmitEmpty();
            }
        }
    }
}
