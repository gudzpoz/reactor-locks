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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class SemaphoreTest {
    @RepeatedTest(value = 5000)
    public void semaphoreTest() {
        semaphoreTest(2, 1, 0, null);
        semaphoreTest(2, 1, 1, null);
        semaphoreTest(10, 5, 0, Schedulers.parallel());
        semaphoreTest(100, 5, 1, Schedulers.parallel());
    }

    private void semaphoreTest(int count, int limit, int delay, Scheduler scheduler) {
        Helper helper = new Helper(new ReactiveSemaphore(limit), count, () ->
                Duration.of(delay, ChronoUnit.MICROS), limit, scheduler);
        helper.verify().block();
    }

    static class Helper extends LockTestHelper<Lock> {
        final Set<Integer> set;
        final AtomicInteger readers;
        private final int limit;
        private final Scheduler scheduler;

        Helper(Lock lock, int concurrency, Supplier<Duration> delay, int limit, Scheduler scheduler) {
            super(lock, concurrency, delay);
            this.limit = limit;
            this.scheduler = scheduler;
            set = new ConcurrentSkipListSet<>();
            readers = new AtomicInteger(0);
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
            return integerMono.transform(lock::lockOnNext);
        }

        @Override
        Mono<Integer> unlock(Mono<Integer> integerMono) {
            return integerMono
                    .transform(lock::unlockOnNext)
                    .doOnNext(i -> assertNotEquals(9, i % 10))
                    .doOnError(SomeException.class, e -> assertEquals(9, e.getI() % 10))
                    .transform(lock::unlockOnError);
        }

        @Override
        void verifyLock(Integer i) {
            assertTrue(set.add(i));
            assertTrue(readers.incrementAndGet() <= limit);
        }

        @Override
        Mono<Integer> verifyBeforeUnlock(Integer i) {
            assertTrue(set.contains(i));
            assertTrue(readers.decrementAndGet() >= 0);
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
