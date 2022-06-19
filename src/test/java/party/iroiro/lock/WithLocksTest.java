package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class WithLocksTest {
    @RepeatedTest(50)
    public void withReactiveLockTestLarge() {
        withLockTest(new ReactiveLock(), Duration.ofMillis(1), 1000, Duration.ofMillis(50));
    }

    @RepeatedTest(500)
    public void withReactiveLockTestMiddle() {
        withLockTest(new ReactiveLock(), Duration.ofMillis(1), 100, Duration.ofMillis(50));
    }

    @RepeatedTest(5000)
    public void withReactiveLockTestDense() {
        withLockTest(new ReactiveLock(), Duration.ofNanos(1), 100, Duration.ofNanos(500));
    }
    @RepeatedTest(50)
    public void withBroadcastingLockTestLarge() {
        withLockTest(new BroadcastingLock(), Duration.ofMillis(1), 1000, Duration.ofMillis(50));
    }

    @RepeatedTest(500)
    public void withBroadcastingLockTestMiddle() {
        withLockTest(new BroadcastingLock(), Duration.ofMillis(1), 100, Duration.ofMillis(50));
    }

    @RepeatedTest(5000)
    public void withBroadcastingLockTestDense() {
        withLockTest(new BroadcastingLock(), Duration.ofNanos(1), 100, Duration.ofNanos(500));
    }
    @RepeatedTest(50)
    public void withSemaphoreLockTestLarge() {
        withLockTest(new ReactiveSemaphore(1), Duration.ofMillis(1), 1000, Duration.ofMillis(50));
    }

    @RepeatedTest(500)
    public void withSemaphoreLockTestMiddle() {
        withLockTest(new ReactiveSemaphore(1), Duration.ofMillis(1), 100, Duration.ofMillis(50));
    }

    @RepeatedTest(5000)
    public void withSemaphoreLockTestDense() {
        withLockTest(new ReactiveSemaphore(1), Duration.ofNanos(1), 100, Duration.ofNanos(500));
    }
    @RepeatedTest(50)
    public void withRwReactiveLockTestLarge() {
        withLockTest(new ReactiveRWLock(), Duration.ofMillis(1), 1000, Duration.ofMillis(50));
    }

    @RepeatedTest(500)
    public void withRwReactiveLockTestMiddle() {
        withLockTest(new ReactiveRWLock(), Duration.ofMillis(1), 100, Duration.ofMillis(50));
    }

    @RepeatedTest(5000)
    public void withRwReactiveLockTestDense() {
        withLockTest(new ReactiveRWLock(), Duration.ofNanos(1), 100, Duration.ofNanos(500));
    }

    public void withLockTest(Lock lock, Duration delay, int concurrency, Duration timeout) {
        ArrayList<Mono<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            requests.add(
                    Mono.just(i)
                            .publishOn(Schedulers.parallel())
                            .flatMap(
                                    j -> lock.withLock(() -> Mono.just(j)
                                            .delayElement(delay)
                                            .flatMap(k -> {
                                                if (!lock.isLocked()) {
                                                    return Mono.error(new RuntimeException("Unlocked?"));
                                                } else {
                                                    return Mono.just(k);
                                                }
                                            })
                                    ).singleOrEmpty()
                            ).timeout(timeout)
                            .onErrorReturn(TimeoutException.class, -1)
            );
        }
        assertDoesNotThrow(() -> Flux.merge(requests).blockLast());
    }
}
