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

import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * A reactive interface for simple locks (and probably semaphores)
 */
public abstract class Lock {
    /**
     * Get a {@link Mono} that emits success only after acquiring the lock
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .flatMap(t -> lock.<b>lock()</b>.thenReturn(t))
     *         /* Some processing &#42;/
     *         .transform(lock::unlock)
     *         .block();
     * </code></pre>
     *
     * <p>
     * The underlying implementation should automatically queue the {@link Mono} up
     * if the lock is not available.
     * </p>
     *
     * @return a {@link Mono} that emits success when the lock is acquired
     */
    public abstract Mono<Void> lock();

    /**
     * Try to acquire the lock on the next element before propagating
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::<b>lockOnNext</b>)
     *         /* Some processing &#42;/
     *         .transform(lock::unlock)
     *         .block();
     * </code></pre>
     *
     * <p>
     * The underlying implementation should automatically queue the {@link Mono} up
     * if the lock is not available.
     * </p>
     * <p>
     * When the lock becomes available, the value will be automatically propagated downstream.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require locking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> lockOnNext(Mono<T> mono) {
        return mono.flatMap(t -> this.lock().thenReturn(t));
    }

    /**
     * Unlocks
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lock)
     *         .flatMap(item -&gt; {
     *             /* Some processing &#42;/
     *             lock.<b>unlock</b>();
     *             return Mono.just(item);
     *         })
     *         .block();
     * </code></pre>
     */
    public abstract void unlock();

    /**
     * Release the lock with {@link Mono#doOnTerminate(Runnable)} before propagating
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lock)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnTerminate</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnTerminate(Runnable)} to ensure the execution order and handling of
     * all cases, including an empty {@link Mono}, a successful {@link Mono} with a
     * emitted value or {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the termination signal will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnTerminate(Mono<T> mono) {
        return mono.doOnTerminate(this::unlock);
    }

    /**
     * Release the lock with {@link Mono#doOnNext(Consumer)} before propagating
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lock)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnNext</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnNext(Consumer)} to ensure the execution order and handling of
     * a successful {@link Mono} with a emitted value.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next value will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnNext(Mono<T> mono) {
        return mono.doOnNext(ignored -> this.unlock());
    }

    /**
     * Release the lock with {@link Mono#switchIfEmpty(Mono)}
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lock)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnEmpty</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#switchIfEmpty(Mono)} to ensure the execution order and handling of
     * an empty {@link Mono}.
     * </p>
     *
     * @param mono the {@link Mono}, of which the signal, when Mono is empty, will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnEmpty(Mono<T> mono) {
        return mono.switchIfEmpty(Mono.fromCallable(() -> {
            this.unlock();
            return null;
        }));
    }

    /**
     * Release the lock with {@link Mono#doOnError(Consumer)} before propagating
     *
     * <p>
     * Usage:
     * </p>
     * <pre><code>
     *     Lock lock = new ReactiveLock(); /* Or other locks &#42;/
     *     mono
     *         .transform(lock::lock)
     *         /* Some processing &#42;/
     *         .transform(lock::<b>unlockOnError</b>)
     *         .block();
     * </code></pre>
     *
     * <p>
     * Using {@link Mono#doOnError(Consumer)} to ensure the execution order and handling of
     * {@link Mono}s with an error.
     * </p>
     *
     * @param mono the {@link Mono}, of which the next error will require unlocking to propagate
     * @param <T>  the generic type of {@link Mono}
     * @return the transformed {@link Mono}
     */
    public <T> Mono<T> unlockOnError(Mono<T> mono) {
        return mono.doOnError(ignored -> this.unlock());
    }
}
