package party.iroiro.lock;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

public class Example {
    private static Flux<String> getWork(String client, Duration delay, Lock lock) {
        return Mono.delay(delay)
                .transform(lock::lockOnNext)
                .thenMany(Flux.interval(Duration.ofMillis(300))
                        .take(3)
                        .map(i -> client)
                        .log(client)
                ).doOnTerminate(lock::unlock);
    }

    @Test
    public void processWithLock() {
        Lock lock = new ReactiveLock();
        String client1 = "client1";
        String client2 = "client2";
        Flux<String> requests = Flux.merge(
                getWork(client1, Duration.ofMillis(0), lock),
                getWork(client2, Duration.ofMillis(400), lock),
                getWork(client1, Duration.ofMillis(300), lock)
        );
        StepVerifier.create(requests)
                .expectSubscription()
                .expectNext(client1)
                .expectNext(client1)
                .expectNext(client1)
                .expectNext(client1)
                .expectNext(client1)
                .expectNext(client1)
                .expectNext(client2)
                .expectNext(client2)
                .expectNext(client2)
                .expectComplete()
                .verify(Duration.ofMillis(5000));
    }
}
