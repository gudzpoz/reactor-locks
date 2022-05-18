package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.jupiter.api.Assertions.*;

public class RWLockHungerTest {
    @Test
    public void simpleTest() {
        RWLock lock = new ReactiveRWLock();
        assertDoesNotThrow(() -> Flux.merge(
                Mono.just(0) // First
                        .transform(lock::rLockOnNext)
                        .delayElement(Duration.ofSeconds(3))
                        .transform(lock::rUnlockOnNext),
                Mono.just(1) // Writer prioritized: Second
                        .delayElement(Duration.ofSeconds(1))
                        .flatMap(t -> lock.tryLock(Duration.ofSeconds(5)).thenReturn(t))
                        .delayElement(Duration.ofSeconds(3))
                        .transform(lock::unlockOnNext),
                Mono.just(2) // Third
                        .delayElement(Duration.ofSeconds(2))
                        .transform(lock::rLockOnNext)
                        .delayElement(Duration.ofSeconds(10))
                        .transform(lock::rUnlockOnNext)
        ).blockLast());
        assertFalse(lock.isLocked());
    }

    @Test
    public void timeoutTest() {
        RWLock lock = new ReactiveRWLock();
        lock.rLock().block();
        assertThrows(Exception.class, () -> lock.tryLock(Duration.ofMillis(100)).block());
    }

    @RepeatedTest(value = 50)
    public void rwLockHungerTest() {
        RWLock lock = new ReactiveRWLock();
        ArrayList<Mono<Integer>> monos = new ArrayList<>();
        Set<Integer> set = new ConcurrentSkipListSet<>();
        final int readerCount = 100;
        final int writerCount = 100;
        for (int i = 0; i < readerCount; i++) {
            monos.add(
                    Mono.just(-i - 1)
                            .publishOn(Schedulers.parallel())
                            .delayElement(Duration.ofMillis(i))
                            .transform(lock::rLockOnNext)
                            .delayElement(Duration.ofMillis(readerCount / 2))
                            .filter(set::add)
                            .transform(lock::rUnlockOnNext)
            );
        }
        for (int i = 0; i < writerCount; i++) {
            int finalI = i;
            monos.add(
                    Mono.just(i)
                            .publishOn(Schedulers.parallel())
                            .delayElement(Duration.ofMillis(i + readerCount / 2))
                            .flatMap(t -> lock.tryRLock(
                                    Duration.ofMillis(finalI + 3 * readerCount / 2)).thenReturn(t))
                            .delayElement(Duration.ofMillis(1))
                            .filter(set::add)
                            .transform(lock::unlockOnNext)
            );
        }
        assertEquals(readerCount + writerCount, Flux.merge(monos).count().block());
        assertEquals(readerCount + writerCount, set.size());
        assertFalse(lock.isLocked());
    }
}
