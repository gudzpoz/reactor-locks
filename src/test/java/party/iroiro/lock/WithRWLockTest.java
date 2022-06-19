package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class WithRWLockTest {
    @RepeatedTest(50)
    public void withRwReactiveLockTestLarge() {
        withRwLockTest(new ReactiveRWLock(), Duration.ofMillis(1), 1000, Duration.ofMillis(5));
    }

    @RepeatedTest(500)
    public void withRwReactiveLockTestMiddle() {
        withRwLockTest(new ReactiveRWLock(), Duration.ofMillis(1), 100, Duration.ofMillis(5));
    }

    @RepeatedTest(5000)
    public void withRwReactiveLockTestDense() {
        withRwLockTest(new ReactiveRWLock(), Duration.ofNanos(1), 100, Duration.ofNanos(10));
    }

    public void withRwLockTest(RWLock lock, Duration delay, int concurrency, Duration timeout) {
        ArrayList<Mono<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            requests.add(
                    Mono.just(i)
                            .publishOn(Schedulers.parallel())
                            .flatMap(
                                    j -> j % 2 == 0 ? getWLock(lock, delay, j) : getRLock(lock, delay, j)
                            ).timeout(timeout)
                            .onErrorReturn(TimeoutException.class, -1)
            );
        }
        assertDoesNotThrow(() -> Flux.merge(requests).blockLast());
    }

    private Mono<Integer> getRLock(RWLock lock, Duration delay, Integer j) {
        return lock.withRLock(() -> Mono.just(j)
                .delayElement(delay)
                .flatMap(k -> {
                    if (!lock.isLocked() || !lock.isRLocked()) {
                        return Mono.error(new RuntimeException("Unlocked?"));
                    } else {
                        return Mono.just(k);
                    }
                })
        ).singleOrEmpty();
    }

    private Mono<Integer> getWLock(RWLock lock, Duration delay, Integer j) {
        return lock.withLock(() -> Mono.just(j)
                .delayElement(delay)
                .flatMap(k -> {
                    if (!lock.isLocked()) {
                        return Mono.error(new RuntimeException("Unlocked?"));
                    } else {
                        return Mono.just(k);
                    }
                })
        ).singleOrEmpty();
    }
}
