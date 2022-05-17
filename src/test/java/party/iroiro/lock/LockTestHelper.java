package party.iroiro.lock;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Collectors;

abstract class LockTestHelper<T extends Lock> {
    protected final T lock;
    protected final int concurrency;
    private final Supplier<Duration> delay;
    LockTestHelper(T lock, int concurrency, Supplier<Duration> delay) {
        this.lock = lock;
        this.concurrency = concurrency;
        this.delay = delay;
    }

    public Mono<Void> verify() {
        return eagerSequence().then().doFinally(ignored -> this.verifyFinally());
    }

    protected abstract void verifyFinally();

    private Flux<Integer> eagerSequence() {
        return Flux.range(0, concurrency).collectList().flatMapMany(
                list -> Flux.merge(list.stream().map(this::toMono).collect(Collectors.toList()))
        );
    }

    private Mono<Integer> toMono(Integer integer) {
        return Mono.just(integer)
                .transform(this::schedule)
                .transform(this::lock)
                .doOnNext(this::verifyLock)
                .delayElement(delay.get())
                .flatMap(this::verifyBeforeUnlock)
                .transform(this::unlock)
                .transform(this::verifyUnlock);
    }

    abstract Mono<Integer> schedule(Mono<Integer> integerMono);

    abstract Mono<Integer> lock(Mono<Integer> integerMono);

    abstract Mono<Integer> unlock(Mono<Integer> integerMono);

    abstract void verifyLock(Integer i);

    abstract Mono<Integer> verifyBeforeUnlock(Integer i);

    Mono<Integer> verifyUnlock(Mono<Integer> integerMono) {
        return integerMono;
    }
}
