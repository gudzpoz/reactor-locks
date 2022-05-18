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

import java.time.Duration;
import java.util.concurrent.TimeoutException;

abstract class AbstractRWLock extends AbstractLock implements RWLock {

    @Override
    public Mono<Void> tryRLock(Duration duration) {
        LockHandle lockHandle = tryRLock();
        return lockHandle.mono().timeout(duration)
                .onErrorResume(TimeoutException.class, e -> {
                    if (lockHandle.cancel()) {
                        return Mono.error(e);
                    } else {
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<Void> rLock() {
        return tryRLock().mono();
    }

    @Override
    public <T> Mono<T> rLockOnNext(Mono<T> mono) {
        return mono.flatMap(t -> this.rLock().thenReturn(t));
    }

    @Override
    public <T> Mono<T> rUnlockOnEmpty(Mono<T> mono) {
        return mono.switchIfEmpty(Mono.fromRunnable(this::rUnlock));
    }

    @Override
    public <T> Mono<T> rUnlockOnNext(Mono<T> mono) {
        return mono.doOnNext(ignored -> this.rUnlock());
    }

    @Override
    public <T> Mono<T> rUnlockOnTerminate(Mono<T> mono) {
        return mono.doOnTerminate(this::rUnlock);
    }

    @Override
    public <T> Mono<T> rUnlockOnError(Mono<T> mono) {
        return mono.doOnError(ignored -> this.rUnlock());
    }
}
