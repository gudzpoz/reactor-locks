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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import party.iroiro.lock.LockHandle;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.IntUnaryOperator;

/**
 * A simple {@link Sinks.Empty}-like implementation
 *
 * <p>
 * Very often, we busy-loop to deal with {@link Sinks.EmitResult#FAIL_NON_SERIALIZED} when
 * using {@link Sinks}. It may not be ideal and might lead to erroneous behaviours.
 * </p>
 * <p>
 * Instead, this implementation aims to rid these failures (by internally using an atomic field,
 * which still busy-loops to {@link AtomicIntegerFieldUpdater#getAndUpdate(Object, IntUnaryOperator)}
 * but is much simpler), while still providing necessary functionalities needed by lock implementations.
 * </p>
 *
 * <p>It expects:</p>
 * <ul>
 *     <li>A single subscriber</li>
 *     <li>Subscriber cancellation is not followed by another request</li>
 * </ul>
 *
 * <p>
 * One can only successfully {@link #emit()} or {@link #cancel()} once.
 * </p>
 */
public class EmptySink implements Publisher<Void>, LockHandle {
    // @formatter:off
    private final static int ERROR      = 0b00000001;
    private final static int EMPTY      = 0b00000010;
    private final static int SUBSCRIBED = 0b00000100;
    private final static int REQUESTED  = 0b00001000;
    // @formatter:on

    private volatile Subscriber<? super Void> subscriber = null;
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<EmptySink, Subscriber> SUBSCRIBER =
            AtomicReferenceFieldUpdater.newUpdater(EmptySink.class, Subscriber.class, "subscriber");

    private volatile int state = 0;
    private static final AtomicIntegerFieldUpdater<EmptySink> STATE =
            AtomicIntegerFieldUpdater.newUpdater(EmptySink.class, "state");

    private final Mono<Void> mono = Mono.from(this);

    @Override
    public void subscribe(Subscriber<? super Void> s) {
        if (SUBSCRIBER.compareAndSet(this, null, s)) {
            STATE.getAndUpdate(this, state -> state | SUBSCRIBED);
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    int s = STATE.getAndUpdate(EmptySink.this, state -> state | REQUESTED);
                    if ((s & REQUESTED) == 0) {
                        if ((s & ERROR) != 0) {
                            subscriber.onError(LockCancellationException.instance());
                        } else if ((s & EMPTY) != 0) {
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                    STATE.getAndUpdate(EmptySink.this, state -> state & ~REQUESTED);
                    /*
                     * We do not attempt to keep STATE and SUBSCRIBER in sync,
                     * since we expect a single subscriber only.
                     * So the following code may produce NullPointerException
                     * with concurrent #request and #cancel.
                     *
                     * SUBSCRIBER.set(EmptySink.this, null);
                     */
                }
            });
        } else {
            Operators.error(s, new IllegalAccessException("Multiple subscription disallowed"));
        }
    }

    /**
     * Tries to emit {@link SignalType#ON_COMPLETE} signal to a subscriber
     *
     * <p>
     * It remembers the emission request if there is no subscriber, and emits
     * the signal to the next subscriber instead.
     * </p>
     *
     * @return {@code true} if successfully scheduled or emitted to the subscriber
     */
    public boolean emit() {
        int s = STATE.getAndUpdate(this, state -> state | EMPTY);
        if ((s & (EMPTY | ERROR)) == 0) {
            if ((s & REQUESTED) != 0) {
                subscriber.onComplete();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Tries to emit a {@link LockCancellationException} to a subscriber
     *
     * <p>
     * It remembers the cancellation request if there is no subscriber, and emits
     * the exception to the next subscriber instead.
     * </p>
     *
     * @return {@code true} if successfully scheduled or emitted to the subscriber
     */
    @Override
    public boolean cancel() {
        int s = STATE.getAndUpdate(this, state -> state | ERROR);
        if ((s & (EMPTY | ERROR)) == 0) {
            if ((s & REQUESTED) != 0) {
                subscriber.onError(LockCancellationException.instance());
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Mono<Void> mono() {
        return mono;
    }
}
