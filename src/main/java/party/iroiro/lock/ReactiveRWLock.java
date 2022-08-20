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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * An implementation {@link RWLock}. See the Javadoc of {@link Lock} and {@link RWLock} for details.
 *
 * <p>
 * This implementation handles writer hunger. A waiting writer should block (in reactive
 * ways, of course) further readers from acquiring the lock.
 * </p>
 * <p>
 * Implemented with CAS operations (i.e., non-blocking).
 * </p>
 */
public final class ReactiveRWLock extends AbstractRWLock {
    /**
     * A state variable
     *
     * <ul>
     *     <li>{@code r == 0}: Unlocked.</li>
     *     <li>{@code r >= 1}: Locked by readers with no pending writers.</li>
     *     <li>{@code r == Integer.MIN_VALUE}: Never. Semantically, to-be-writer-unlocked or a negative zero.</li>
     *     <li>{@code 0 > r > Integer.MIN_VALUE}:
     *     <ul>
     *         <li>Either locked by readers, but there are pending writers.</li>
     *         <li>Or locked by a writer.</li>
     *     </ul>
     *     </li>
     * </ul>
     */
    private volatile int r = 0;
    private final static AtomicIntegerFieldUpdater<ReactiveRWLock> R =
            AtomicIntegerFieldUpdater.newUpdater(ReactiveRWLock.class, "r");

    private volatile int wip = 0;
    private final static AtomicIntegerFieldUpdater<ReactiveRWLock> WIP =
            AtomicIntegerFieldUpdater.newUpdater(ReactiveRWLock.class, "wip");

    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> readers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> writers = new ConcurrentLinkedQueue<>();

    @Override
    public LockHandle tryLock() {
        if (R.compareAndSet(this, 0, Integer.MIN_VALUE + 1)) {
            /* Atomically acquires the lock */
            return LockHandle.empty();
        } else {
            LockHandle handle = SinkUtils.queue(writers, SinkUtils::emitEmpty);
            /* Conceptually does a lock upgrade (reader to writer) */
            R.updateAndGet(this, i -> i >= 0 ? i + Integer.MIN_VALUE + 1 : i + 1);
            /* Ensures that trailing requests are processed */
            decrement();
            return handle;
        }
    }

    @Override
    public LockHandle tryRLock() {
        /* Atomically checks if it is already reading */
        if (R.incrementAndGet(this) > 0) {
            /* Acquires the lock */
            return LockHandle.empty();
        } else {
            /*
             * Writing or writer pending: Signals to the current writer
             */
            LockHandle handle = SinkUtils.queue(readers, SinkUtils::emitEmpty);
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
             * a corrupted R state.
             */
            do {
                if (r != Integer.MIN_VALUE + 1) {
                    /* Most commonly so */
                    R.decrementAndGet(this);
                } else {
                    /*
                     * Must be a writer / the last reader unlocking.
                     *
                     * Tries to emit to a writer first
                     */
                    if (SinkUtils.emitAny(writers)) {
                        //noinspection UnnecessaryContinue
                        continue;
                        /*
                         * Decrements WIP, without decrementing R
                         */
                    } else {
                        /*
                         * Tries to emit to readers
                         */
                        if (R.compareAndSet(this, Integer.MIN_VALUE + 1, 1)) {
                            /*
                             * The above updateAndGet sets R to 1 rather than 0:
                             * - We perform the following as a pseudo-reader.
                             * - Alternatively, we interpret this as a lock downgrade (writer -> reader).
                             */
                            do {
                                R.incrementAndGet(this);
                            } while (SinkUtils.emitAny(readers));

                            /*
                             * The above for-while loop induces an extra increment in R,
                             * we increment WIP instead of decrementing R
                             * to ensure that the lock is correctly unlocked or passed to a writer.
                             */
                            WIP.incrementAndGet(this);

                            /*
                             * The pseudo reader unlocks.
                             */
                            R.decrementAndGet(this);
                        } else {
                            /*
                             * Some reader / writer just incremented R:
                             * - Let's process their requests before switching to a reader lock.
                             * - It is because we need to ensure R < 0 when any writer is pending.
                             */
                            R.decrementAndGet(this);
                        }
                    }
                }
            } while (WIP.decrementAndGet(this) != 0);
        }
    }

    @Override
    public void unlock() {
        decrement();
    }

    @Override
    public void rUnlock() {
        decrement();
    }

    @Override
    public boolean isLocked() {
        return r != 0;
    }

    @Override
    public boolean isRLocked() {
        return r > 0;
    }
}
