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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class RWLockTest {
    @RepeatedTest(value = 1000)
    public void rwLockTest() {
        rwLockTest(2, 0, false);
        rwLockTest(2, 1, false);
        rwLockTest(10, 0, true);
        rwLockTest(10, 1, true);
    }

    private void rwLockTest(int count, int delay, boolean scheduler) {
        ReactiveRWLock lock = new ReactiveRWLock();
        ArrayList<Mono<Integer>> monoCollection = new ArrayList<>(count);
        long totalDelay = 0;
        Scheduler elastic = Schedulers.boundedElastic();
        Scheduler parallel = Schedulers.parallel();
        AtomicBoolean writing = new AtomicBoolean(false);
        AtomicInteger readers = new AtomicInteger(0);
        for (int i = 0; i < count; i++) {
            totalDelay += delay;
            int finalI = i;
            monoCollection.add(
                    (scheduler ?
                            Mono.just(i)
                                    .publishOn((i & 1) == 0 ? elastic : parallel)
                            :
                            Mono.just(i))

                            .transform(mono -> {
                                if (finalI % 2 == 0) {
                                    /* Writer */
                                    return mono
                                            .transform(lock::lock)
                                            .flatMap(ii -> {
                                                if (writing.getAndSet(true)) {
                                                    return Mono.error(new IllegalStateException("Writing already"));
                                                } else {
                                                    if (readers.get() == 0) {
                                                        return Mono.just(ii);
                                                    } else {
                                                        return Mono.error(new IllegalStateException("Reading already"));
                                                    }
                                                }
                                            })
                                            .delayElement(Duration.ofMillis(delay))
                                            .flatMap(ii -> {
                                                if (writing.getAndSet(false)) {
                                                    if (readers.get() == 0) {
                                                        if (ii % 10 == 8) {
                                                            return Mono.error(new SomeException(ii));
                                                        } else {
                                                            return Mono.just(ii);
                                                        }
                                                    } else {
                                                        return Mono.error(new IllegalStateException("Reading before unlocking"));
                                                    }
                                                } else {
                                                    return Mono.error(new IllegalStateException("Not writing"));
                                                }
                                            })
                                            .transform(lock::unlockOnTerminate)
                                            .onErrorResume(SomeException.class, e -> Mono.just(e.getI()));
                                } else {
                                    /* Reader */
                                    return mono
                                            .transform(lock::rLock)
                                            .flatMap(ii -> {
                                                readers.incrementAndGet();
                                                if (writing.get()) {
                                                    return Mono.error(new IllegalStateException("Oops writing"));
                                                } else {
                                                    return Mono.just(ii);
                                                }
                                            })
                                            .delayElement(Duration.ofMillis(delay))
                                            .flatMap(ii -> {
                                                readers.decrementAndGet();
                                                if (writing.get()) {
                                                    return Mono.error(new IllegalStateException("Oops"));
                                                } else {
                                                    return Mono.just(ii);
                                                }
                                            })
                                            .transform(lock::rUnlockOnTerminate);
                                }
                            })
            );
        }
        Set<Integer> set = new ConcurrentSkipListSet<>();
        long start = System.nanoTime();
        assertDoesNotThrow(() -> Flux.merge(monoCollection).doOnNext(
                set::add
        ).blockLast());
        long end = System.nanoTime();
        assertEquals(count, set.size());
        assertTrue(scheduler || totalDelay * 1000_000 <= end - start);
        assertFalse(writing.get());
        assertEquals(0, readers.get());
    }

}
