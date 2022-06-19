package party.iroiro.lock;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A class providing {@link Mono}-like API to build instructions in locked scopes
 *
 * <p>
 * See {@link LockedMonoBuilder} for more info.
 * </p>
 *
 * @param <R> the beginning mono data type
 */
public class LockedMono<R> {
    private final Mono<R> before;
    private final Sinks.One<R> start;

    LockedMono(Mono<R> mono) {
        this.before = mono;
        start = Sinks.one();
    }

    LockedMonoBuilder<R> begin() {
        return new LockedMonoBuilder<>(start.asMono());
    }

    /**
     * A simple builder for fluent locking API
     *
     * <p>
     * Example:
     * </p>
     * <pre><code>
     * Lock lock = new {@link ReactiveLock}();
     *
     * mono
     *     .map(t -&gt; operationsRegardlessOfTheLock(t))
     *     /* Begin the lock scope *&#47;
     *     .{@link Mono#as(Function) as}({@link Lock#begin(Mono) Lock::begin})
     *     .{@link #map(Function) map}(t -&gt; operationsWithLockAcquired(t))
     *     .{@link #flatMap(Function) flatMap}(s -&gt; operationsWithLockAcquired(s))
     *     /* Confirm to apply the above two transformations under the lock *&#47;
     *     .{@link #with(Lock) with(lock)};
     * </code></pre>
     *
     * <p>
     * The above code is actually equivalent to the following code using {@link Lock#withLock(Supplier)}.
     * </p>
     *
     * <pre><code>
     * Lock lock = new {@link ReactiveLock}();
     *
     * mono
     *     .map(t -&gt; operationsRegardlessOfTheLock(t))
     *     /* Begin the lock scope *&#47;
     *     .flatMap(t -&gt; lock.withLock(() -&gt;
     *         Mono.just(t)
     *             .map(t -&gt; operationsWithLockAcquired(t))
     *             .flatMap(s -&gt; operationsWithLockAcquired(s))
     *     ).next());
     * </code></pre>
     *
     * <p>
     * If you want more complex operators, please just wrap them in {@link #flatMap(Function)}.
     * </p>
     *
     * @param <T> the {@link Mono} data type
     */
    public class LockedMonoBuilder<T> {
        private final Mono<T> mono;

        private LockedMonoBuilder(Mono<T> mono) {
            this.mono = mono;
        }

        /**
         * Calling {@link Mono#map(Function)} of the internal mono, with the lock held
         *
         * @param mapper the synchronous transforming {@link Function}
         * @param <S>    the transformed value type
         * @return a new {@link LockedMonoBuilder}
         */
        public <S> LockedMonoBuilder<S> map(Function<T, S> mapper) {
            return new LockedMonoBuilder<>(mono.map(mapper));
        }

        /**
         * Calling {@link Mono#flatMap(Function)} of the internal mono, with the lock held
         *
         * @param flatMapper the synchronous transforming {@link Function}
         * @param <S>        the transformed value type
         * @return a new {@link LockedMonoBuilder}
         */
        public <S> LockedMonoBuilder<S> flatMap(Function<T, Mono<S>> flatMapper) {
            return new LockedMonoBuilder<>(mono.flatMap(flatMapper));
        }

        /**
         * Transforms the builder into a {@link Mono}, with previous transformations
         * gathered into the lock scope with {@link Lock#withLock(Supplier)}
         *
         * @param lock the lock
         * @return a normal {@link Mono} with previous locking, transformations and unlocking applied
         */
        public Mono<T> with(Lock lock) {
            return before.flatMap(r -> lock.withLock(() -> {
                        start.tryEmitValue(r);
                        return mono;
                    }).next()
            );
        }

        /**
         * Transforms the builder into a {@link Mono}, with previous transformations
         * gathered into the lock scope with {@link RWLock#withRLock(Supplier)}
         *
         * @param lock the reader-writer lock
         * @return a normal {@link Mono} with previous reader-locking, transformations and unlocking applied
         */
        public Mono<T> withR(RWLock lock) {
            return before.flatMap(r -> lock.withRLock(() -> {
                        start.tryEmitValue(r);
                        return mono;
                    }).next()
            );
        }
    }
}
