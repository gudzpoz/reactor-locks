package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class LockTest {
    @Test
    public void sinkTest() {
        Sinks.Empty<Void> emitsBefore = Sinks.empty();
        assertEquals(Sinks.EmitResult.OK, emitsBefore.tryEmitEmpty());
        assertEquals("Value", emitsBefore.asMono().thenReturn("Value")
                .block(Duration.ofSeconds(1)));

        Sinks.Empty<Void> emitsAfter = Sinks.empty();
        assertThrows(RuntimeException.class,
                () -> emitsAfter.asMono().thenReturn("Value").block(Duration.ofSeconds(1)));
        assertEquals(Sinks.EmitResult.OK, emitsAfter.tryEmitEmpty());
        assertEquals("Next", emitsAfter.asMono().thenReturn("Next").block());
        assertEquals("Next", emitsAfter.asMono().thenReturn("Next").block());
        assertEquals("Next", emitsAfter.asMono().thenReturn("Next").block());
    }

    @Test
    public void callableTest() {
        AtomicBoolean bool = new AtomicBoolean(false);
        Mono<Boolean> booleanMono = Mono.fromCallable(() -> bool.getAndSet(true));
        assertFalse(bool.get());
        assertEquals(false, booleanMono.block());
        assertTrue(bool.get());
    }

    @RepeatedTest(value = 1000)
    public void lockTestMultiple() {
        lockTest(100, 0, false);
        lockTest(100, 0, true);
    }

    @RepeatedTest(value = 10000)
    public void lockTwoTest() {
        lockTest(2, 0, false);
        lockTest(2, 1, true);
    }

    private void lockTest(int count, int delay, boolean scheduler) {
        ReactiveLock lock = new ReactiveLock();
        ArrayList<Mono<Integer>> mono = new ArrayList<>(count);
        long totalDelay = 0;
        Scheduler elastic = Schedulers.boundedElastic();
        Scheduler parallel = Schedulers.parallel();
        AtomicBoolean checker = new AtomicBoolean(false);
        for (int i = 0; i < count; i++) {
            totalDelay += delay;
            mono.add(
                    (scheduler ?
                            Mono.just(i)
                                    .publishOn((i & 1) == 0 ? elastic : parallel)
                            :
                            Mono.just(i))

                            .transform(lock::lock)
                            .flatMap(ii -> {
                                if (checker.getAndSet(true)) {
                                    return Mono.error(new IllegalStateException("Oops"));
                                } else {
                                    return Mono.just(ii);
                                }
                            })
                            .delayElement(Duration.ofMillis(delay))
                            .flatMap(ii -> {
                                if (checker.getAndSet(false)) {
                                    if (ii % 10 == 9) {
                                        return Mono.error(new SomeException(ii));
                                    } else {
                                        return Mono.just(ii);
                                    }
                                } else {
                                    return Mono.error(new IllegalStateException("Oops"));
                                }
                            })
                            .transform(lock::unlockOnTerminate)
                            .onErrorResume(SomeException.class, e -> Mono.just(e.getI())
                            )
            );
        }
        Set<Integer> set = new ConcurrentSkipListSet<>();
        long start = System.nanoTime();
        assertDoesNotThrow(() -> Flux.merge(mono).doOnNext(set::add).blockLast());
        long end = System.nanoTime();
        assertEquals(count, set.size());
        assertTrue(scheduler || totalDelay * 1000_000 <= end - start);
    }

}
