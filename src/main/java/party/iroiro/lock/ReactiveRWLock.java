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
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation {@link RWLock}. See the Javadoc of {@link Lock} and {@link RWLock} for details.
 *
 * <p>
 * This implementation handles writer hunger. A waiting writer should block (in reactive
 * ways, of course) further readers from acquiring the lock.
 * </p>
 */
public class ReactiveRWLock extends AbstractRWLock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> readers;
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> writers;
    private final ReentrantLock lock;
    private int readerCount;
    private boolean readingStarting;
    private State state;

    public ReactiveRWLock() {
        state = State.NONE;
        readerCount = 0;
        readingStarting = false;
        readers = new ConcurrentLinkedQueue<>();
        writers = new ConcurrentLinkedQueue<>();
        lock = new ReentrantLock();
    }

    @Override
    public LockHandle tryLock() {
        lock.lock();
        if (state == State.NONE) {
            state = State.WRITING;
            lock.unlock();
            return LockHandle.empty();
        } else {
            LockHandle entry = SinkUtils.queue(writers, empty -> empty.tryEmitEmpty().isSuccess());
            lock.unlock();
            return entry;
        }
    }

    @Override
    public boolean isLocked() {
        lock.lock();
        boolean state = this.state != State.NONE;
        lock.unlock();
        return state;
    }

    public void unlock() {
        Sinks.Empty<Void> sink;
        while (true) {
            lock.lock();

            sink = writers.poll();
            if (sink == null) {
                if (readers.isEmpty()) {
                    state = State.NONE;
                } else {
                    startReaders();
                }
                lock.unlock();
                return;
            }

            lock.unlock();

            if (sink.tryEmitEmpty().isSuccess()) {
                return;
            }
        }
    }

    /**
     * One should lock {@link #lock} before calling this method
     *
     * <p>
     * Does not unlock the {@link #lock}.
     * </p>
     */
    private void startReaders() {
        readingStarting = true;
        state = State.READING;

        Sinks.Empty<Void> reader;
        while (true) {
            reader = readers.poll();
            if (reader == null) {
                readingStarting = false;
                if (readerCount == 0) {
                    if (writers.isEmpty()) {
                        state = State.NONE;
                    } else {
                        /*
                         * May cause StackOverflowError in really unlucky conditions
                         * Hopefully the recursion is not that deep
                         */
                        state = State.WRITING;
                        lock.unlock();
                        unlock();
                        lock.lock();
                    }
                }
                return;
            }

            readerCount++;

            lock.unlock();
            boolean success = reader.tryEmitEmpty().isSuccess();
            lock.lock();
            if (!success) {
                readerCount--;
            }
        }
    }

    @Override
    public LockHandle tryRLock() {
        lock.lock();
        switch (state) {
            case NONE:
                state = State.READING;
                readerCount++;
                lock.unlock();
                return LockHandle.empty();
            case READING:
                if (writers.isEmpty() && !readingStarting) {
                    readerCount++;
                    lock.unlock();
                    return LockHandle.empty();
                }
                /* Fall through */
            case WRITING:
                /* default: For full coverage */
            default:
                LockHandle entry = SinkUtils.queue(readers, empty -> {
                    lock.lock();
                    readerCount++;
                    lock.unlock();
                    if (empty.tryEmitEmpty().isSuccess()) {
                        rUnlock();
                        return true;
                    } else {
                        rUnlock();
                        return false;
                    }
                });
                lock.unlock();
                return entry;
        }
    }

    @Override
    public boolean isRLocked() {
        lock.lock();
        boolean state = this.state == State.READING;
        lock.unlock();
        return state;
    }

    @Override
    public void rUnlock() {
        lock.lock();
        readerCount--;
        if (readerCount == 0 && state == State.READING) {
            if (!readingStarting) {
                state = State.WRITING;
                lock.unlock();
                unlock();
                return;
            }
        }
        lock.unlock();
    }

    private enum State {
        READING, WRITING, NONE,
    }
}
