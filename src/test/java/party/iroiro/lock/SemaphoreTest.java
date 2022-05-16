package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class SemaphoreTest {
    @RepeatedTest(value = 1000)
    public void semaphoreTest() {
        semaphoreTest(2, 1, 0, false);
        semaphoreTest(2, 1, 1, false);
        semaphoreTest(10, 5, 0, true);
        semaphoreTest(100, 5, 1, true);
    }

    private void semaphoreTest(int count, int limit, int delay, boolean scheduler) {
        Lock lock = new ReactiveSemaphore(limit);
        ArrayList<Mono<Integer>> monoCollection = new ArrayList<>(count);
        long totalDelay = 0;
        Scheduler elastic = Schedulers.boundedElastic();
        Scheduler parallel = Schedulers.parallel();
        AtomicInteger readers = new AtomicInteger(0);
        AtomicInteger max = new AtomicInteger(0);
        for (int i = 0; i < count; i++) {
            totalDelay += delay;
            monoCollection.add(
                    (scheduler ?
                            Mono.just(i)
                                    .publishOn((i & 1) == 0 ? elastic : parallel)
                            :
                            Mono.just(i))

                            .transform(lock::lock)
                            .flatMap(ii -> {
                                int now = readers.incrementAndGet();
                                if (now > count) {
                                    return Mono.error(new IllegalStateException("Max already"));
                                } else {
                                    max.updateAndGet(m -> Math.max(now, m));
                                    return Mono.just(ii);
                                }
                            })
                            .delayElement(Duration.ofMillis(delay))
                            .flatMap(ii -> {
                                if (readers.decrementAndGet() >= 0) {
                                    if (ii % 10 == 8) {
                                        return Mono.error(new SomeException(ii));
                                    } else {
                                        return Mono.just(ii);
                                    }
                                } else {
                                    return Mono.error(new IllegalStateException("Really unexpected"));
                                }
                            })
                            .transform(lock::unlockOnTerminate)
                            .onErrorResume(SomeException.class, e -> Mono.just(e.getI())));
        }
        Set<Integer> set = new ConcurrentSkipListSet<>();
        long start = System.nanoTime();
        assertDoesNotThrow(() -> Flux.merge(monoCollection).doOnNext(
                set::add
        ).blockLast());
        long end = System.nanoTime();
        assertEquals(count, set.size());
        assertTrue(scheduler || totalDelay * 1000_000 <= end - start);
        assertEquals(0, readers.get());
    }

}
