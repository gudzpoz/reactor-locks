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

import party.iroiro.lock.util.SinkUtils;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * An implementation of {@link Lock} that allows multiple lock holders with an upper limit
 * (that is, it is the reactive version of {@link Semaphore}).
 */
public final class ReactiveSemaphore extends AbstractLock {
    /**
     * The maximum lock holder count
     */
    private final int limit;

    /**
     * The number of lock users
     *
     * <p>
     * Actually, it is the number of current lock holders plus the number of requests under evaluation.
     * </p>
     */
    private volatile int count = 0;
    private final static AtomicIntegerFieldUpdater<ReactiveSemaphore> COUNT =
            AtomicIntegerFieldUpdater.newUpdater(ReactiveSemaphore.class, "count");

    /**
     * The ongoing jobs to decrement {@link #count}
     */
    private volatile int wip = 0;
    private final static AtomicIntegerFieldUpdater<ReactiveSemaphore> WIP =
            AtomicIntegerFieldUpdater.newUpdater(ReactiveSemaphore.class, "wip");

    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> queue =
            new ConcurrentLinkedQueue<>();

    /**
     * Creates a reactive semaphore with the given upper limit
     * @param limit a positive limit
     * @throws IllegalArgumentException when the limit is not positive
     */
    public ReactiveSemaphore(int limit) throws IllegalArgumentException {
        if (limit < 1) {
            throw new IllegalArgumentException("Expecting a positive limit");
        }
        this.limit = limit;
    }

    @Override
    public LockHandle tryLock() {
        if (COUNT.incrementAndGet(this) <= limit) {
            /* Atomically acquires the lock */
            return LockHandle.empty();
        } else {
            LockHandle handle = SinkUtils.queue(queue, SinkUtils::emitError);
            /* Ensures that trailing requests are processed */
            decrement();
            return handle;
        }
    }

    private void decrement() {
        /*
         * We introduced an intermediate WIP to decrement COUNT,
         * which ensures that all decrement requests get processed correctly.
         */
        if (WIP.incrementAndGet(this) == 1) {
            /*
             * Only one thread will be doing the decrementing job to avoid concurrent decrements.
             *
             * If there are pending requests, concurrent decrements might eventually result in
             * a COUNT below "limit", despite the waiting pending requests.
             */
            do {
                /*
                 * "count <= limit" works fine without need to ensure atomicity:
                 *
                 * - The actual lock holders should be less than or equals to "count - wip"
                 *   (considering that we increase "count" before incrementing "wip" in "tryLock").
                 *
                 * - "Wip" is greater than zero, that is, "actual <= count - wip < count <= limit",
                 *   i.e., we have at least "wip" slots already reserved, and nobody can take them
                 *   away with "tryLock" before we release them by decrementing "count".
                 */
                if (count <= limit) {
                    if (SinkUtils.emitAny(queue)) {
                        /* No decrements this cycle, since we have emitted to a new lock holder. */
                        continue;
                    }
                }
                COUNT.decrementAndGet(this);
            } while (WIP.decrementAndGet(this) != 0);
        }
    }

    @Override
    public boolean isLocked() {
        return count >= limit;
    }

    @Override
    public void unlock() {
        decrement();
    }
}
