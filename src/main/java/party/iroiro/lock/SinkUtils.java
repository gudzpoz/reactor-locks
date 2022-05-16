package party.iroiro.lock;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;

class SinkUtils {
    /**
     * Creates a {@link Sinks.Empty}, offer it to the queue, and make a {@link Mono}
     * which emits the {@param item} after the sink is filled.
     *
     * @param queue the queue
     * @param item  the item to be emitted
     * @param <T>   the {@link Mono} generic type
     * @return the new {@link Mono}
     */
    static <T> Mono<T> queue(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue,
                             T item) {
        Sinks.Empty<Void> empty = Sinks.empty();
        queue.add(empty);
        return empty.asMono().thenReturn(item);
    }
}
