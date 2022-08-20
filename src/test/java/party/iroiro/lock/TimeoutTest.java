package party.iroiro.lock;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeoutTest {
    @Test
    public void timeoutTest() {
        ReactiveLock lock = new ReactiveLock();
        for (int i = 0; i < 1000; i++) {
            lock.tryLock().mono().as(StepVerifier::create).verifyComplete();
            LockHandle handle = lock.tryLock();
            handle.mono()
                    .timeout(Duration.ofMillis(1))
                    .as(StepVerifier::create)
                    .verifyError(TimeoutException.class);
            assertTrue(handle.cancel());
            lock.unlock();
        }
        assertFalse(lock.isLocked());
    }

    @Test
    public void resumeErrorTest() {
        ReactiveLock lock = new ReactiveLock();
        for (int i = 0; i < 1000; i++) {
            lock.tryLock().mono().as(StepVerifier::create).verifyComplete();
            LockHandle handle = lock.tryLock();
            handle.mono()
                    .timeout(Duration.ofMillis(1))
                    .onErrorResume(TimeoutException.class, e -> Mono.empty())
                    .as(StepVerifier::create)
                    .verifyComplete();
            assertTrue(handle.cancel());
            lock.unlock();
        }
        assertFalse(lock.isLocked());
    }

    @Test
    public void fluxResumeErrorTest() {
        List<Mono<Integer>> list = new ArrayList<>();
        ReactiveLock lock = new ReactiveLock();
        for (int i = 0; i < 1000; i++) {
            int finalI = i;
            list.add(Mono.defer(() -> lock.withLock(() ->
                    Mono.just(finalI).delayElement(Duration.ofSeconds(1)))
                    .next()
                    .timeout(Duration.ofMillis(1))));
        }
        Flux.merge(list)
                .onErrorContinue(TimeoutException.class, (e, o) -> {})
                .as(StepVerifier::create).verifyComplete();
    }
}
