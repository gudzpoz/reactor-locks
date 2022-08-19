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

import party.iroiro.lock.util.EmptySink;
import party.iroiro.lock.util.SinkUtils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * A lock implementation using mainly CAS operations to synchronize
 *
 * <p>
 * It is modified from {_link CasSemaphore}, optimizing away the intermediate WIP variable.
 * </p>
 */
public final class ReactiveLock extends AbstractLock {
    private volatile int count = 0;
    private static final AtomicIntegerFieldUpdater<ReactiveLock> COUNT =
            AtomicIntegerFieldUpdater.newUpdater(ReactiveLock.class, "count");

    private final ConcurrentLinkedQueue<EmptySink> queue = new ConcurrentLinkedQueue<>();

    private final boolean fairness;

    /**
     * Creates an unfair lock
     */
    public ReactiveLock() {
        this(false);
    }

    /**
     * Creates an instance of {@code CasLock} with the given fairness policy
     * @param fairness {@code true} if this lock should use a fair ordering policy
     */
    public ReactiveLock(boolean fairness) {
        this.fairness = fairness;
    }

    @Override
    public LockHandle tryLock() {
        if (COUNT.compareAndSet(this, 0, 1)) {
            return LockHandle.empty();
        } else {
            LockHandle handle = SinkUtils.queueSink(queue);
            fairDecrement(false);
            return handle;
        }
    }

    private void fairDecrement(boolean unlocking) {
        /*
         * COUNT states:
         * - COUNT == 0: The lock is unlocked, with no ongoing decrement operations.
         * - COUNT >= 1: Either the lock is being held, or there is an ongoing decrement operation.
         *               Note that the two are mutual exclusive, since they both require COUNT++ == 0.
         *
         * If "unlocking", then we are responsible for decrements.
         *
         * Otherwise,
         * 1. If COUNT++ >= 1, either someone is holding the lock, or there is an ongoing
         *    decrement operation. Either way, some thread will eventually emit to pending requests.
         *    We increment COUNT to signal to the emitter that the queue could have potentially been
         *    appended to after its last emission.
         * 2. If COUNT++ == 0, then we are responsible for decrementing.
         */
        if (unlocking || COUNT.incrementAndGet(this) == 1) {
            do {
                if (SinkUtils.emitAnySink(queue)) {
                    /*
                     * Leaves the decrementing job to the next lock holder, who will unlock somehow.
                     */
                    return;
                }
                /*
                 * It is now safe to decrement COUNT, since there is no concurrent decrements.
                 */
            } while (COUNT.decrementAndGet(this) != 0);
        }
    }

    @Override
    public boolean isLocked() {
        return count != 0;
    }

    @Override
    public void unlock() {
        if (fairness) {
            fairDecrement(true);
        } else {
            COUNT.set(this, 0);
            fairDecrement(false);
        }
    }
}
