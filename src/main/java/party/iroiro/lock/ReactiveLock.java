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
 * A reactive {@link Lock} implementation. See the Javadoc of {@link Lock}.
 */
public class ReactiveLock extends Lock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> queue;
    private boolean locked;

    public ReactiveLock() {
        queue = new ConcurrentLinkedQueue<>();
        locked = false;
    }

    public synchronized void unlock() {
        if (SinkUtils.emitAndCheckShouldUnlock(queue)) {
            locked = false;
        }
    }

    public synchronized Mono<Void> lock() {
        if (locked) {
            return SinkUtils.queue(queue, (empty) -> {
                synchronized (this) {
                    if (!empty.tryEmitEmpty().isSuccess()) {
                        /* Race condition: Emitted by previous unlock */
                        unlock();
                    }
                }
            });
        } else {
            locked = true;
            return Mono.empty();
        }
    }

    @Override
    public synchronized boolean isLocked() {
        return locked;
    }
}
