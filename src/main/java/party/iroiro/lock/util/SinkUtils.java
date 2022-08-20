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

package party.iroiro.lock.util;

import party.iroiro.lock.LockHandle;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public abstract class SinkUtils {
    private SinkUtils() {
    }

    /**
     * Creates a {@link Sinks.Empty}, offer it to the queue, and make a {@link Mono}
     * which emits success after the sink is filled.
     *
     * @param queue    the queue
     * @param onCancel the callback to register to {@link Mono#doOnCancel(Runnable)}, the created sink will be passed
     * @return the new {@link Mono}
     */
    public static LockHandle queue(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue,
                                   Function<Sinks.Empty<Void>, Boolean> onCancel) {
        Sinks.Empty<Void> empty = Sinks.empty();
        queue.add(empty);
        return LockHandle.from(empty.asMono(), () -> onCancel.apply(empty));
    }

    public static LockHandle queueSink(ConcurrentLinkedQueue<EmptySink> queue) {
        EmptySink empty = new EmptySink();
        queue.add(empty);
        return empty;
    }

    public static boolean emitEmpty(Sinks.Empty<Void> sink) {
        Sinks.EmitResult result;
        do {
            result = sink.tryEmitEmpty();
        } while (result == Sinks.EmitResult.FAIL_NON_SERIALIZED);
        return result.isSuccess();
    }

    public static boolean emitError(Sinks.Empty<Void> sink) {
        Sinks.EmitResult result;
        do {
            result = sink.tryEmitError(LockCancellationException.instance());
        } while (result == Sinks.EmitResult.FAIL_NON_SERIALIZED);
        return result.isSuccess();
    }

    public static boolean emitAny(ConcurrentLinkedQueue<Sinks.Empty<Void>> queue) {
        Sinks.Empty<Void> sink = queue.poll();
        while (sink != null) {
            if (emitEmpty(sink)) {
                return true;
            }
            sink = queue.poll();
        }
        return false;
    }

    public static boolean emitAnySink(ConcurrentLinkedQueue<EmptySink> queue) {
        EmptySink sink = queue.poll();
        while (sink != null) {
            if (sink.emit()) {
                return true;
            }
            sink = queue.poll();
        }
        return false;
    }
}
