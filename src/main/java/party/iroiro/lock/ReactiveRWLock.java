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
            return SinkUtils.queue(writers);
        }
    }

    public synchronized void unlock() {
        Sinks.Empty<Void> next = writers.poll();
        if (next == null) {
            if (readerCount == 0) {
                state = State.NONE;
            } else {
                state = State.READING;
                Sinks.Empty<Void> reader = readers.poll();
                while (reader != null) {
                    reader.tryEmitEmpty();
                    reader = readers.poll();
                }
            }
        } else {
            next.tryEmitEmpty();
        }
    }

    @Override
    public synchronized Mono<Void> rLock() {
        readerCount++;
        switch (state) {
            case NONE:
                state = State.READING;
                /* Fall through */
            case READING:
                return Mono.empty();
            case WRITING:
                return SinkUtils.queue(readers);
            default:
                return Mono.never();
        }
    }

    public synchronized void rUnlock() {
        readerCount--;
        if (readerCount == 0) {
            Sinks.Empty<Void> writer = writers.poll();
            if (writer == null) {
                state = State.NONE;
            } else {
                state = State.WRITING;
                writer.tryEmitEmpty();
            }
        }
    }

    private enum State {
        READING, WRITING, NONE,
    }
}
