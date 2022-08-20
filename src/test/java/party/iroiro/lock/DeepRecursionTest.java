package party.iroiro.lock;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class DeepRecursionTest {
    public static final int COUNT = 100000;

    @Test
    public void testDeepRecursion() {
        Lock lock = new ReactiveLock();
        lock.withLock(() -> {
            List<Mono<Integer>> lockRequests = new ArrayList<>(COUNT);
            for (int i = 0; i < COUNT; i++) {
                LockHandle lockHandle = lock.tryLock();
                lockRequests.add(lockHandle.mono().thenReturn(i).doOnTerminate(lock::unlock));
            }
            return Mono.just(lockRequests);
        }).flatMap(Flux::concat).blockLast();
    }
}
