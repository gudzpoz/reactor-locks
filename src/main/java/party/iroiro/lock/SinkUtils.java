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
import java.util.function.Consumer;

abstract class SinkUtils {
    private SinkUtils() {}

    /**
     * Creates a {@link Sinks.Empty}, offer it to the queue, and make a {@link Mono}
     * which emits success after the sink is filled.
     *
     * @param queue the queue
     * @param onCancel the callback to register to {@link Mono#doOnCancel(Runnable)}, the created sink will be passed
     * @return the new {@link Mono}
     */
    static Mono<Void> queue(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue,
                            Consumer<Sinks.Empty<Void>> onCancel) {
        Sinks.Empty<Void> empty = Sinks.empty();
        queue.add(empty);
        return empty.asMono().doOnCancel(() -> onCancel.accept(empty));
    }

    /**
     * Poll from the queue, emit to the polled item, until the emission is successful or the queue empty
     *
     * <p>
     * External synchronization is almost <b>required</b>. Use with caution.
     * </p>
     *
     * @param queue the queue to poll from
     * @return <code>false</code> if the emission is successful and the lock need not be removed<br>
     *         <code>true</code> if the emission failed and the lock can now be removed
     */
    static boolean emitAndCheckShouldUnlock(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue) {
        Sinks.Empty<Void> sink = queue.poll();
        while (sink != null) {
            if (sink.tryEmitEmpty().isSuccess()) {
                return false;
            }
            sink = queue.poll();
        }
        return true;
    }
}
