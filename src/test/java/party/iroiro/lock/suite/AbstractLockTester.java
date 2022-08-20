package party.iroiro.lock.suite;

import party.iroiro.lock.Lock;
import party.iroiro.lock.util.LockCancellationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractLockTester<T> {
    protected final long[] init;
    protected final long[] requests;
    protected final int concurrency;

    public AbstractLockTester(int requests, int concurrency,
                              Function<Integer, Long> init,
                              Function<Integer, Long> delay,
                              float timeoutRatio) {
        this(randomTimes(requests, delay, timeoutRatio), initTimes(requests, init), concurrency);
    }

    private static long[] initTimes(int requests, Function<Integer, Long> init) {
        long[] durations = new long[requests];
        for (int i = 0; i < requests; i++) {
            durations[i] = init.apply(i);
        }
        return durations;
    }

    private static long[] randomTimes(int requests, Function<Integer, Long> delay, float ratio) {
        long[] durations = new long[requests];
        Random random = new Random();
        for (int i = 0; i < requests; i++) {
            durations[i] = delay.apply(i) * ((random.nextFloat() < ratio) ? -1 : 1);
        }
        return durations;
    }

    /**
     * @param requests    requests, delay duration (positive) or timeout duration (negative) (nanos)
     * @param init        the duration to wait for before requesting the lock
     * @param concurrency the number of threads
     */
    public AbstractLockTester(long[] requests, long[] init, int concurrency) {
        this.init = init;
        this.requests = requests;
        this.concurrency = concurrency;
    }

    protected abstract void onLock(int i, T t);

    protected abstract void beforeUnlock(int i, T t);

    protected abstract void verify(Flux<Integer> result, ConcurrentLinkedQueue<Integer> requesters, T t);

    public void test(Lock lock, T t) {
        ConcurrentLinkedQueue<Integer> requesters = new ConcurrentLinkedQueue<>();
        assertFalse(lock.isLocked());
        Scheduler scheduler = Schedulers.newParallel(lock.getClass().getSimpleName(), concurrency);
        List<Mono<Integer>> monoList = IntStream.range(0, requests.length).mapToObj(i -> Mono.just(i)
                .delayElement(Duration.ofNanos(init[i]))
                .then(Mono.defer(() -> {
                    requesters.add(i);
                    Mono<Integer> next = lock.withLock(() -> Mono.using(
                            AtomicInteger::new,
                            atomicI -> {
                                assertEquals(1, atomicI.incrementAndGet());
                                onLock(i, t);
                                return Mono.just(i)
                                        .publishOn(scheduler)
                                        .delayElement(requests[i] > 0
                                                ? Duration.ofNanos(requests[i])
                                                : Duration.ofSeconds(100));
                            },
                            atomicI -> callBeforeUnlock(t, i, atomicI)
                    )).next().onErrorContinue(LockCancellationException.class, (throwable, o) -> {
                    });
                    if (requests[i] <= 0) {
                        return next
                                .timeout(Duration.ofNanos(-requests[i]))
                                .onErrorReturn(TimeoutException.class, -1);
                    } else {
                        return next;
                    }
                }))
        ).collect(Collectors.toList());
        verify(Flux.merge(monoList), requesters, t);
        assertFalse(lock.isLocked());
    }

    private void callBeforeUnlock(T t, int i, AtomicInteger b) {
        assertEquals(2, b.incrementAndGet());
        beforeUnlock(i, t);
    }
}
