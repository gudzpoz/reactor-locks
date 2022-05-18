package party.iroiro.lock;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

abstract class LockTestHelper<T extends AbstractLock> {
    protected final T lock;
    protected final int concurrency;
    protected final Supplier<Duration> delay;
    LockTestHelper(T lock, int concurrency, Supplier<Duration> delay) {
        this.lock = lock;
        this.concurrency = concurrency;
        this.delay = delay;
    }

    public Mono<Void> verify() {
        return eagerSequence().count().doFinally(ignored -> this.verifyFinally()).then();
    }

    protected abstract void verifyFinally();

    private Flux<Integer> eagerSequence() {
        return Flux.range(0, concurrency).map(this::toMono).collectList().flatMapMany(Flux::merge);
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
