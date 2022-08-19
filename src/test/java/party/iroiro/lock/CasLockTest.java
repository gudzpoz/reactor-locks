package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CasLockTest {
    public static final int COUNT = 100000;

    @RepeatedTest(5)
    public void casLockTest() {
        ReactiveLock lock = new ReactiveLock();
        AtomicInteger locked = new AtomicInteger(-1);
        List<Mono<Integer>> monoList = Flux.range(0, COUNT)
                .map(i -> lock.withLock(() -> {
                    assertTrue(locked.compareAndSet(-1, i));
                    Mono<Integer> mono = Mono.just(i)
                            .publishOn(Schedulers.parallel())
                            .delayElement(Duration.ofNanos(100))
                            .doFinally(ignored -> assertTrue(locked.compareAndSet(i, -1)));
                    if (Math.random() > 0.95) {
                        mono = mono
                                .timeout(Duration.ofNanos(50))
                                .onErrorReturn(TimeoutException.class, i);
                    }
                    return mono;
                }).next())
                .collectList().block();
        assert monoList != null;
        HashSet<Integer> set = new HashSet<>(Objects.requireNonNull(Flux.merge(monoList).collectList().block()));
        assertEquals(COUNT, set.size());
        for (int i = 0; i < COUNT; i++) {
            assertTrue(set.contains(i));
        }
    }
}
