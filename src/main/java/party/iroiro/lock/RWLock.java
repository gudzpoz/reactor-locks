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

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * An reactive interface for <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">
 * reader–writer locks</a>.
 */
public abstract class RWLock extends Lock {
    /**
     * Offers more flexibility than {@link #tryRLock(Duration)}
     *
     * <p>
     * See {@link LockHandle}
     * </p>
     *
     * @return a lock handle
     */
    public abstract LockHandle tryRLock();

    /**
     * Tries to acquire the reader-lock, or stop and propagate a {@link TimeoutException}
     * downstream after certain duration.
     *
     * @param duration the time to wait for lock
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    public Mono<Void> tryRLock(Duration duration) {
        LockHandle lockHandle = tryRLock();
        return lockHandle.mono().timeout(duration)
                .onErrorResume(TimeoutException.class, e -> {
                    if (lockHandle.cancel()) {
                        return Mono.error(e);
                    } else {
                        return Mono.empty();
                    }
                });
    }

    /**
     * See {@link Lock#lock} for details.
     *
     * <p>
     * The difference is that the underlying implementation might choose to implement a
     * <a href="https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock">Readers–writer lock</a>.
     * </p>
     * <p>
     * <b>Do not</b> use {@link Mono#timeout(Duration)} or {@link Mono#timeout(Publisher)},
     * which are not handled at all. Use {@link #tryRLock(Duration)} or {@link #tryRLock()} instead
     * if you want timeouts.
     * </p>
     *
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    public Mono<Void> rLock() {
        return tryRLock().mono();
    }

    /**
     * Checks whether this lock is reader-locked (or has reached the max lock holders)
     *
     * <p>
     * You should not rely on the result of this due to possible concurrent operations.
     * </p>
     *
     * @return whether this lock is reader-locked
     */
    public abstract boolean isRLocked();

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
