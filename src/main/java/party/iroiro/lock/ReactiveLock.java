package party.iroiro.lock;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A reactive {@link Lock} implementation. See the Javadoc of {@link Lock}.
 */
public class ReactiveLock extends Lock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> queue;
    private boolean locked;

    public ReactiveLock() {
        queue = new ConcurrentLinkedQueue<>();
        locked = false;
    }

    public void unlock() {
        synchronized (this) {
            Sinks.Empty<Void> sink = queue.poll();
            if (sink == null) {
                locked = false;
            } else {
                sink.tryEmitEmpty();
            }
        }
    }

    public <T> Mono<T> lock(Mono<T> mono) {
        return mono.flatMap(t -> {
            synchronized (this) {
                if (locked) {
                    return SinkUtils.queue(queue, t);
                } else {
                    locked = true;
                    return Mono.just(t);
                }
            }
        });
    }
}
