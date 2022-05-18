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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testable
public class RWLockTest {
    @RepeatedTest(value = 20000)
    public void rwLockTest() {
        rwLockTest(2, 0, null);
        rwLockTest(2, 1, null);
        rwLockTest(10, 0, Schedulers.parallel());
        rwLockTest(5, 1, Schedulers.parallel());
    }

    @RepeatedTest(value = 3000)
    public void largeAudienceRWLockTest() {
        rwLockTest(500, 0, Schedulers.parallel());
        rwLockTest(500, 1, Schedulers.parallel());
    }

    @Test
    public void hungerTest() {
        ReactiveRWLock rw = new ReactiveRWLock();
        Mono<Void> firstReader = rw.rLock();
        Mono<Void> thenWriter = rw.lock();
        Mono<Void> blockedReader = rw.rLock();
        Mono<Void> blockedReader2 = rw.rLock();
        Flux.merge(
                firstReader.thenReturn(0).delayElement(Duration.ofSeconds(5))
                        .doOnTerminate(rw::rUnlock),
                Mono.just(1).delayElement(Duration.ofSeconds(1)).then(thenWriter)
                        .doOnTerminate(rw::unlock),
                Mono.just(2).delayElement(Duration.ofSeconds(2)).then(blockedReader)
                        .doOnTerminate(rw::rUnlock),
                Mono.just(3).delayElement(Duration.ofSeconds(3)).then(blockedReader2)
                        .doOnTerminate(rw::rUnlock)
        ).blockLast();
    }

    private void rwLockTest(int count, int delay, @Nullable Scheduler scheduler) {
        ReactiveRWLock rw = new ReactiveRWLock();
        Helper helper = new Helper(rw, count,
                () -> Duration.of(delay, ChronoUnit.MICROS), scheduler);
        helper.verify().block();
    }

    static class Helper extends LockTestHelper<RWLock> {
        final Set<Integer> set;
        final AtomicBoolean writing;
        final AtomicInteger readers;
        private final Scheduler scheduler;

        Helper(RWLock lock, int concurrency, Supplier<Duration> delay,
               @Nullable Scheduler scheduler) {
            super(lock, concurrency, delay);
            this.scheduler = scheduler;
            set = new ConcurrentSkipListSet<>();
            writing = new AtomicBoolean(false);
            readers = new AtomicInteger(0);
        }

        boolean isReader(int i) {
            return i % 3 != 0;
        }

        boolean shouldFails(int i) {
            return i % 7 == 2;
        }

        @Override
        protected void verifyFinally() {
            assertEquals(concurrency, set.size());
            assertEquals(0, readers.get());
            assertFalse(writing.get());
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
            return integerMono.flatMap(t -> {
                Duration duration = delay.get().multipliedBy(concurrency / 2);
                return (isReader(t) ? lock.rLock() : lock.lock()).thenReturn(t)
                        .timeout(duration)
                        .doOnError(TimeoutException.class, e -> assertTrue(set.add(t)));
            });
        }

        @Override
        void verifyLock(Integer i) {
            assertTrue(set.add(i));
            if (isReader(i)) {
                readers.incrementAndGet();
                assertFalse(writing.get());
            } else {
                assertEquals(0, readers.get());
                assertFalse(writing.getAndSet(true));
            }
        }

        @Override
        Mono<Integer> verifyBeforeUnlock(Integer i) {
            assertTrue(set.contains(i));
            if (isReader(i)) {
                readers.decrementAndGet();
                assertFalse(writing.get());
            } else {
                assertEquals(0, readers.get());
                assertTrue(writing.getAndSet(false));
            }
            return shouldFails(i) ? Mono.error(new SomeException(i)) : Mono.just(i);
        }

        @Override
        Mono<Integer> unlock(Mono<Integer> integerMono) {
            return integerMono

                    .doOnNext(i -> assertFalse(shouldFails(i)))
                    .doOnError(SomeException.class, e -> assertTrue(shouldFails(e.getI())))
                    .onErrorResume(SomeException.class, e -> Mono.just(e.getI()))

                    .flatMap(i -> isReader(i) ? Mono.just(i) : Mono.error(new SomeException(i)))
                    .transform(lock::rUnlockOnNext)
                    .onErrorResume(TimeoutException.class, e -> Mono.just(-1))
                    .transform(lock::unlockOnError)
                    .onErrorResume(SomeException.class, e -> Mono.just(e.getI()));
        }

        @Override
        Mono<Integer> verifyUnlock(Mono<Integer> integerMono) {
            return integerMono.onErrorResume(SomeException.class, e -> Mono.just(e.getI()));
        }
    }

}
