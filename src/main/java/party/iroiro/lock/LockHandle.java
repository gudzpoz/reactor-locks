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

import java.util.function.Supplier;

/**
 * A lock handle (including a {@link Mono} to listen to and a canceller function).
 */
public interface LockHandle {
    static LockHandle empty() {
        return EmptyLockHandle.instance();
    }

    static LockHandle from(Mono<Void> mono, Supplier<Boolean> canceller) {
        return new CancellableHandle(mono, canceller);
    }

    /**
     * Attempts to cancels a lock request
     *
     * @return <code>true</code> if successfully cancelled<br>
     *         <code>false</code> if you have acquired the lock, probably just raced with the cancel request
     */
    boolean cancel();

    /**
     * Returns a {@link Mono} that emits success only after acquiring the lock
     *
     * @return a {@link Mono} that emits success only after acquiring the lock
     */
    Mono<Void> mono();

    class CancellableHandle implements LockHandle {
        private final Mono<Void> mono;
        private final Supplier<Boolean> canceller;

        CancellableHandle(Mono<Void> mono, Supplier<Boolean> canceller) {
            this.mono = mono;
            this.canceller = canceller;
        }

        public boolean cancel() {
            return canceller.get();
        }

        public Mono<Void> mono() {
            return mono;
        }
    }
}
