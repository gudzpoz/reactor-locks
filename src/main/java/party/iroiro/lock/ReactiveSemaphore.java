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
import java.util.concurrent.Semaphore;

/**
 * An implementation of {@link Lock} that allows multiple lock holders with an upper limit
 * (that is, it is the reactive version of {@link java.util.concurrent.Semaphore}).
 */
public class ReactiveSemaphore extends Lock {
    private final ConcurrentLinkedQueue<Sinks.Empty<Void>> queue;
    private final int limit;
    private int count;

    public ReactiveSemaphore(int limit) {
        this.limit = limit;
        count = 0;
        queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public <T> Mono<T> lock(Mono<T> mono) {
        return mono.flatMap(t -> {
            synchronized (this) {
                if (count < limit) {
                    count++;
                    return Mono.just(t);
                } else {
                    return SinkUtils.queue(queue, t);
                }
            }
        });
    }

    public void unlock() {
        synchronized (this) {
            Sinks.Empty<Void> next = queue.poll();
            if (next == null) {
                count--;
            } else {
                next.tryEmitEmpty();
            }
        }
    }
}
