package party.iroiro.lock.suite;

import org.junit.jupiter.api.RepeatedTest;
import party.iroiro.lock.ReactiveLock;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleLockTest {
    final SimpleLockTester tester = new SimpleLockTester(10000, 1);

    @RepeatedTest(100)
    public void casUnfairLockTest() {
        tester.test(new ReactiveLock(false), new LockInfo());
    }

    @RepeatedTest(100)
    public void casFairLockTest() {
        tester.test(new ReactiveLock(true), new LockInfo());
    }

    public static class LockInfo {
        public final AtomicBoolean locked = new AtomicBoolean(false);
        public final ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();
    }

    public static class SimpleLockTester extends AbstractLockTester<LockInfo> {
        public SimpleLockTester(int requests, int concurrency) {
            super(requests, concurrency,
                    integer -> 0L,
                    integer -> 10L,
                    -1);
        }

        @Override
        protected void onLock(int i, LockInfo lockInfo) {
            lockInfo.log.add("L" + i);
            assertFalse(lockInfo.locked.getAndSet(true));
        }

        @Override
        protected void beforeUnlock(int i, LockInfo lockInfo) {
            lockInfo.log.add("U" + i);
            assertTrue(lockInfo.locked.getAndSet(false));
        }

        @Override
        protected void verify(Flux<Integer> result, ConcurrentLinkedQueue<Integer> requesters, LockInfo lockInfo) {
            Set<Integer> set = result.collect(Collectors.toSet()).block();
            assertNotNull(set);
            assertEquals(Arrays.stream(requests).filter(l -> l > 0).count()
                    + (set.contains(-1) ? 1 : 0), set.size());
            for (int i = 0; i < requests.length; i++) {
                assertTrue(requests[i] <= 0 || set.contains(i));
            }
        }
    }
}
