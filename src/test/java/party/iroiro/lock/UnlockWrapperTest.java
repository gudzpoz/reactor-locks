package party.iroiro.lock;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class UnlockWrapperTest {
    @RepeatedTest(value = 1)
    public void rwWrapperTest() {
        ArrayList<Function<Integer, Job>> shouldDos = new ArrayList<>();
        shouldDos.add(this::shouldDoSome);
        shouldDos.add(this::shouldEmpty);
        shouldDos.add(this::shouldFail);
        shouldDos.add(i -> Job.PASS);
        shouldDos.add(i -> Job.EMPTY);
        shouldDos.add(i -> Job.FAIL);
        for (boolean separateHandles : new boolean[]{true, false}) {
            for (Function<Integer, Job> shouldDo : shouldDos) {
                testCase(shouldDo, separateHandles, 100);
                testCase(shouldDo, separateHandles, 1000);
            }
        }
    }

    private void testCase(Function<Integer, Job> shouldDo, boolean separateHandles, int count) {
        RWLock lock = new ReactiveRWLock();
        ArrayList<Mono<Integer>> monos = new ArrayList<>();
        Set<Integer> records = new ConcurrentSkipListSet<>();
        for (int i = 0; i < count; i++) {
            monos.add(integerMono(
                    i, lock, i % 3 < 2, separateHandles,
                    shouldDo, records
            ));
        }
        assertEquals(count, Flux.merge(monos).count().block());
        assertEquals(count, records.size());
        assertFalse(lock.isRLocked());
        assertFalse(lock.isLocked());
    }

    private Mono<Integer> integerMono(int integer, RWLock lock,
                                      boolean reader, boolean separateHandles,
                                      Function<Integer, Job> shouldDo,
                                      Set<Integer> records) {
        return Mono.just(integer)
                .transform(reader ? lock::rLockOnNext : lock::lockOnNext)
                .delayElement(Duration.ofMillis(1))
                .flatMap(i -> {
                    assertTrue(records.add(i));
                    switch (shouldDo.apply(i)) {
                        case PASS:
                            return Mono.just(i);
                        case FAIL:
                            return Mono.error(new SomeException(i));
                        case EMPTY:
                            return Mono.empty();
                        default:
                            return Mono.never();
                    }
                }).transform(objectMono -> {
                    if (separateHandles) {
                        return objectMono
                                .transform(reader ? lock::rUnlockOnNext : lock::unlockOnNext)
                                .transform(reader ? lock::rUnlockOnError : lock::unlockOnError)
                                .transform(reader ? lock::rUnlockOnEmpty : lock::unlockOnEmpty);
                    } else {
                        return objectMono
                                .transform(reader ? lock::rUnlockOnTerminate : lock::unlockOnTerminate);
                    }
                })
                .onErrorResume(SomeException.class, e -> Mono.just(e.getI()))
                .switchIfEmpty(Mono.just(-1));
    }

    enum Job {
        PASS, FAIL, EMPTY,
    }

    private Job shouldDoSome(int i) {
        if (i % 7 == 3) {
            return Job.FAIL;
        } else if (i % 5 == 4) {
            return Job.EMPTY;
        } else {
            return Job.PASS;
        }
    }

    private Job shouldFail(int i) {
        if (i % 4 == 3) {
            return Job.FAIL;
        } else {
            return Job.PASS;
        }
    }

    private Job shouldEmpty(int i) {
        if (i % 4 == 1) {
            return Job.EMPTY;
        } else {
            return Job.PASS;
        }
    }
}
