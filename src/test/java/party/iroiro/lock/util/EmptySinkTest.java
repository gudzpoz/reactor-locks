package party.iroiro.lock.util;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class EmptySinkTest {
    @Test
    public void multipleSubscriptionTest() {
        EmptySink sink = new EmptySink();
        sink.subscribe(Operators.drainSubscriber());
        assertTrue(sink.emit());
        for (int i = 0; i < 10; i++) {
            Mono.from(sink).as(StepVerifier::create).verifyError(IllegalAccessException.class);
        }
        sink.mono().as(StepVerifier::create).verifyError(IllegalAccessException.class);
    }

    @Test
    public void multipleRequestTest() {
        EmptySink sink = new EmptySink();
        AtomicReference<Subscription> sub = new AtomicReference<>();
        AtomicBoolean emitted = new AtomicBoolean(false);
        sink.subscribe(new Subscriber<Void>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
                sub.set(s);
            }

            @Override
            public void onNext(Void unused) {
                fail();
            }

            @Override
            public void onError(Throwable t) {
                assertTrue(emitted.compareAndSet(false, true));
            }

            @Override
            public void onComplete() {
                assertTrue(emitted.compareAndSet(false, true));
            }
        });
        assertTrue(sink.emit());
        assertTrue(emitted.get());
        assertNotNull(sub.get());
        sub.get().request(1);
    }

    @Test
    public void requestBeforeCancelTest() {
        EmptySink sink = new EmptySink();
        sink.subscribe(Operators.drainSubscriber());
        assertTrue(sink.cancel());
    }
}
