/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testable
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
    public void lockTestHundredConcurrency() {
        lockTest(new ReactiveLock(), 100, 0, null);
        lockTest(new ReactiveLock(), 100, 0, Schedulers.parallel());
    }

    @RepeatedTest(value = 1000)
    public void broadcastingLockTestHundredConcurrency() {
        lockTest(new BroadcastingLock(), 100, 0, null);
        lockTest(new BroadcastingLock(), 100, 0, Schedulers.parallel());
    }

    @RepeatedTest(value = 10000)
    public void lockTestPairConcurrency() {
        lockTest(new ReactiveLock(), 2, 0, null);
        lockTest(new ReactiveLock(), 2, 1, Schedulers.parallel());
    }

    @RepeatedTest(value = 10000)
    public void broadcastingLockTestPairConcurrency() {
        lockTest(new BroadcastingLock(), 2, 0, null);
        lockTest(new BroadcastingLock(), 2, 1, Schedulers.parallel());
    }

    @RepeatedTest(value = 3000)
    public void largeAudienceTest() {
        lockTest(new ReactiveLock(), 500, 0, Schedulers.parallel());
    }

    @RepeatedTest(value = 3000)
    public void largeAudienceBroadcastTest() {
        lockTest(new BroadcastingLock(), 500, 0, Schedulers.parallel());
    }

    @RepeatedTest(value = 100)
    public void timeoutTest() {
        lockTest(new ReactiveLock(), 50, 10, Schedulers.parallel());
    }

    @RepeatedTest(value = 100)
    public void timeoutBroadcastingTest() {
        lockTest(new BroadcastingLock(), 50, 10, Schedulers.parallel());
    }

    private void lockTest(Lock lock, int concurrency, int delay, Scheduler scheduler) {
        Helper lockTester = new Helper(lock, concurrency,
                () -> Duration.of(delay, ChronoUnit.MILLIS), scheduler);
        lockTester.verify().block();
    }

    static class Helper extends LockTestHelper<Lock> {
        final Set<Integer> set;
        final AtomicBoolean locked;
        private final Scheduler scheduler;

        Helper(Lock lock, int concurrency, Supplier<Duration> delay, Scheduler scheduler) {
            super(lock, concurrency, delay);
            this.scheduler = scheduler;
            set = new ConcurrentSkipListSet<>();
            locked = new AtomicBoolean(false);
        }

        @Override
        protected void verifyFinally() {
            assertEquals(concurrency, set.size());
        }

        @Override
        Mono<Integer> schedule(Mono<Integer> integerMono) {
            if (scheduler == null) {
                return integerMono;
            } else {
                return integerMono.publishOn(scheduler);
            }
        }

        @Override
        Mono<Integer> lock(Mono<Integer> integerMono) {
            Duration duration = delay.get().multipliedBy(concurrency / 2);
            return duration.isZero()
                    ? integerMono.transform(lock::lockOnNext)
                    : integerMono.flatMap(i -> lock.lockOnNext(Mono.just(i)).timeout(duration)
                            .doOnError(e -> assertTrue(set.add(i))));
        }

        @Override
        Mono<Integer> unlock(Mono<Integer> integerMono) {
            return integerMono
                    .transform(lock::unlockOnNext)
                    .doOnNext(i -> assertNotEquals(9, i % 10))
                    .doOnError(SomeException.class, e -> assertEquals(9, e.getI() % 10))
                    .onErrorResume(TimeoutException.class, e -> Mono.just(-1))
                    .transform(lock::unlockOnError);
        }

        @Override
        void verifyLock(Integer i) {
            assertTrue(set.add(i));
            assertFalse(locked.getAndSet(true));
        }

        @Override
        Mono<Integer> verifyBeforeUnlock(Integer i) {
            assertTrue(set.contains(i));
            assertTrue(locked.getAndSet(false));
            if (i % 10 == 9) {
                return Mono.error(new SomeException(i));
            } else {
                return Mono.just(i);
            }
        }

        @Override
        Mono<Integer> verifyUnlock(Mono<Integer> integerMono) {
            return integerMono.onErrorResume(SomeException.class, e -> Mono.just(e.getI()));
        }
    }

}
