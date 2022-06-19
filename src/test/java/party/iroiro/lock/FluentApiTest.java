package party.iroiro.lock;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FluentApiTest {
    @Test
    public void fluentTest() {
        RWLock lock = new ReactiveRWLock();
        assertDoesNotThrow(() ->
                Flux.range(0, 200)
                        .map(integer -> (integer % 2 == 0 ?
                                Mono.just(integer)
                                        .as(Lock::begin)
                                        .map(i -> i + 1)
                                        .flatMap(i -> Mono.just(i).delayElement(Duration.ofMillis(i)))
                                        .with(lock) :
                                Mono.just(integer)
                                        .as(Lock::begin)
                                        .map(i -> i + 1)
                                        .flatMap(i -> Mono.just(i).delayElement(Duration.ofMillis(i)))
                                        .withR(lock))
                                .timeout(Duration.ofMillis(2L * integer))
                                .onErrorReturn(-1))
                        .collectList()
                        .flatMapMany(Flux::merge)
                        .blockLast());
    }
}
