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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of {@link Lock} by internally broadcasting releasing events.
 *
 * <p>
 * Since it is broadcasting, it get slower as the concurrency increases. But its
 * performance is comparable to {@link ReactiveLock} within ~100 concurrency, which
 * is still not that likely to get surmounted easily.
 * </p>
 */
public class BroadcastingLock extends Lock {
    private final AtomicBoolean unlocked;
    private final Sinks.Many<Boolean> broadcast;
    private final Flux<Boolean> queue;

    public BroadcastingLock() {
        unlocked = new AtomicBoolean(true);
        broadcast = Sinks.many().multicast().onBackpressureBuffer(1, false);
        queue = broadcast.asFlux();
        broadcast.tryEmitNext(true);
    }

    @Override
    public Mono<Void> lock() {
        return queue.filter(ignored -> {
            synchronized (this) {
                return unlocked.getAndSet(false);
            }
        }).next().then();
    }

    @Override
    public synchronized void unlock() {
        unlocked.set(true);
        broadcast.tryEmitNext(true);
    }
}
