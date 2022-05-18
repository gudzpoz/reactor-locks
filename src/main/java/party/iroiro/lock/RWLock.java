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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An reactive interface for <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">
 * reader–writer locks</a>.
 */
public abstract class RWLock extends Lock {

    /**
     * See {@link Lock#lock} for details.
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer lock</a>.
     * </p>
     * <p>
     * The underlying implementation should handle the {@link SignalType#CANCEL} signal
     * correctly, that is, you may use {@link Mono#timeout(Duration)},
     * {@link Mono#timeout(Publisher)} safely. Timed-out locks need not unlocking,
     * as is with {@link ReentrantLock#tryLock(long, TimeUnit)}.
     * </p>
     *
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    public abstract Mono<Void> rLock();

    /**
     * See {@link Lock#lockOnNext(Mono)} for details.
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer lock</a>.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rLockOnNext(Mono<T> mono) {
        return mono.flatMap(t -> this.rLock().thenReturn(t));
    }

    /**
     * See {@link Lock#unlock} for details.
     */
    public abstract void rUnlock();

    /**
     * See {@link Lock#unlockOnEmpty(Mono)} for details.
     *
     * @param mono the {@link Mono}, of which the next signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnEmpty(Mono<T> mono) {
        return mono.switchIfEmpty(Mono.fromRunnable(this::rUnlock));
    }

    /**
     * See {@link Lock#unlockOnNext(Mono)} for details.
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnNext(Mono<T> mono) {
        return mono.doOnNext(ignored -> this.rUnlock());
    }

    /**
     * See {@link Lock#unlockOnTerminate(Mono)} for details.
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnTerminate(Mono<T> mono) {
        return mono.doOnTerminate(this::rUnlock);
    }

    /**
     * See {@link Lock#unlockOnError(Mono)} for details.
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> rUnlockOnError(Mono<T> mono) {
        return mono.doOnError(ignored -> this.rUnlock());
    }
}
