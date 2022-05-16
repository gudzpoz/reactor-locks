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

class SinkUtils {
    /**
     * Creates a {@link Sinks.Empty}, offer it to the queue, and make a {@link Mono}
     * which emits the {@param item} after the sink is filled.
     *
     * @param queue the queue
     * @param item  the item to be emitted
     * @param <T>   the {@link Mono} generic type
     * @return the new {@link Mono}
     */
    static <T> Mono<T> queue(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue,
                             T item) {
        Sinks.Empty<Void> empty = Sinks.empty();
        queue.add(empty);
        return empty.asMono().thenReturn(item);
    }
}
