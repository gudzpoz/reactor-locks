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
 * Since it is broadcasting, it gets slower as the concurrency increases. But its
 * performance is comparable to {@link ReactiveLock} within ~100 concurrency, which
 * is still not that likely to get surmounted easily.
 * </p>
 */
public class BroadcastingLock extends AbstractLock {
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
    public LockHandle tryLock() {
        final AtomicBoolean lockedByMe = new AtomicBoolean(false);
        Mono<Void> request = queue.filter(ignored -> {
            if (unlocked.compareAndSet(true, false)) {
                if (lockedByMe.compareAndSet(false, true)) {
                    return true;
                } else {
                    /* Race condition: Cancelled */
                    unlock();
                    return false;
                }
            } else {
                return false;
            }
        }).next().then();
        return LockHandle.from(request, () -> lockedByMe.compareAndSet(false, true));
    }

    @Override
    public boolean isLocked() {
        return !unlocked.get();
    }

    @Override
    public void unlock() {
        unlocked.set(true);
        //noinspection StatementWithEmptyBody
        while (broadcast.tryEmitNext(true) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            /*
             * Race condition: Multiple unlocks:
             * The lock is acquired and unlocked after we set `unlocked` but before we emit
             * the signal, resulting in concurrent `tryEmitNext` and thus FAIL_NON_SERIALIZED.
             */
        }
    }
}
