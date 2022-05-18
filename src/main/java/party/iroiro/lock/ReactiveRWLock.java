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
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An implementation {@link RWLock}. See the Javadoc of {@link RWLock} for details.
 *
 * <p>
 * This implementation handles writer hunger. A waiting writer should block (in reactive
 * ways of course) further readers from acquiring the lock.
 * </p>
 */
public class ReactiveRWLock extends RWLock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> readers;
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> writers;
    private int readerCount;
    private State state;

    public ReactiveRWLock() {
        state = State.NONE;
        readerCount = 0;
        readers = new ConcurrentLinkedQueue<>();
        writers = new ConcurrentLinkedQueue<>();
    }

    @Override
    public synchronized Mono<Void> lock() {
        if (state == State.NONE) {
            state = State.WRITING;
            return Mono.empty();
        } else {
            return SinkUtils.queue(writers, empty -> {
                synchronized (this) {
                    if (!empty.tryEmitEmpty().isSuccess()) {
                        /* Race condition: Emitted by previous unlock / rUnlock */
                        unlock();
                    }
                }
            });
        }
    }

    @Override
    public synchronized boolean isLocked() {
        return state != State.NONE;
    }

    public synchronized void unlock() {
        if (SinkUtils.emitAndCheckShouldUnlock(writers)) {
            if (readers.isEmpty()) {
                state = State.NONE;
            } else {
                startReaders();
            }
        }
    }

    private void startReaders() {
        state = State.READING;
        Sinks.Empty<Void> reader = readers.poll();
        while (reader != null) {
            if (reader.tryEmitEmpty().isSuccess()) {
                readerCount++;
            }
            reader = readers.poll();
        }
        if (readerCount == 0) {
            state = State.NONE;
        }
    }

    @Override
    public synchronized Mono<Void> rLock() {
        switch (state) {
            case NONE:
                state = State.READING;
                readerCount++;
                return Mono.empty();
            case READING:
                if (writers.isEmpty()) {
                    readerCount++;
                    return Mono.empty();
                }
                /* Fall through */
            case WRITING:
                /* default: For full coverage */
            default:
                return SinkUtils.queue(readers, empty -> {
                    synchronized (this) {
                        if (empty.tryEmitEmpty().isSuccess()) {
                            readerCount++;
                        }
                        rUnlock();
                    }
                });
        }
    }

    @Override
    public synchronized boolean isRLocked() {
        return state == State.READING;
    }

    @Override
    public synchronized void rUnlock() {
        readerCount--;
        if (readerCount == 0 && state != State.WRITING) {
            state = State.WRITING;
            if (SinkUtils.emitAndCheckShouldUnlock(writers)) {
                startReaders();
            }
        }
    }

    private enum State {
        READING, WRITING, NONE,
    }
}
