# Reactor Locks - Locks, RWLocks, Semaphores

[![Maven Central](https://img.shields.io/maven-central/v/party.iroiro/reactor-locks?color=blue&label=Maven%20Central)](https://mvnrepository.com/artifact/party.iroiro/reactor-locks)
[![Javadoc](https://javadoc.io/badge2/party.iroiro/reactor-locks/Javadoc.svg?color=orange)](https://javadoc.io/doc/party.iroiro/reactor-locks)
[![License](https://img.shields.io/github/license/gudzpoz/reactor-locks?label=License)](./LICENSE)

[![Build and Publish](https://github.com/gudzpoz/reactor-locks/actions/workflows/build.yml/badge.svg)](https://github.com/gudzpoz/reactor-locks/actions/workflows/build.yml)
[![Build and Publish](https://github.com/gudzpoz/reactor-locks/actions/workflows/test.yml/badge.svg)](https://github.com/gudzpoz/reactor-locks/actions/workflows/test.yml)
[![Codecov](https://img.shields.io/codecov/c/github/gudzpoz/reactor-locks.svg?label=Coverage)](https://app.codecov.io/gh/gudzpoz/reactor-locks)

Reactor Locks is a library providing reactive access to locks with [Project Reactor](https://projectreactor.io/).

```java
import party.iroiro.lock.Lock;
import party.iroiro.lock.ReactiveLock;

class Example {
    Lock lock = new ReactiveLock();
    
    <T> Mono<T> reactive(Mono<T> mono) {
        return mono
                .as(Lock::begin)
                .flatMap(t -> someJob(t))
                .map(t -> someOtherJob(t))
                .with(lock);
    }
}
```

## Getting It

[![Maven Central](https://img.shields.io/maven-central/v/party.iroiro/reactor-locks?color=blue&label=Maven%20Central)](https://mvnrepository.com/artifact/party.iroiro/reactor-locks)

<details>
<summary>Maven</summary>

```xml
<dependency>
  <groupId>party.iroiro</groupId>
  <artifactId>reactor-locks</artifactId>
  <version>1.1.0</version>
</dependency>
```

</details>

<details>
<summary>Gradle</summary>

```groovy
implementation 'party.iroiro:reactor-locks:1.1.0'
```

</details>

## Usage

### Fluent API

```java
import party.iroiro.lock.Lock;

// ...

Lock lock = getLock();
// (1)    Start a locked scope builder
   mono.as(Lock::begin)

//    (2) Register operations in the locked scope
//    (2) Operations inside will be performed with the lock locked
      .map(t -> t + 1)
      .flatMap(this::fm)

// (3)   Build the locked scope with a lock
    .with(lock);
```

For `RWLock`s, you may choose between `.with` and `.withR` to distinguish reader-locking and writer-locking.

### Scoped Function

A little lengthy.

```java
mono.flatMap(t -> lock.withLock(() -> {
    return Mono.just(t)
               .map(t -> t + 1)
               .flatMap(this::fm);
});
```

For `RWLocks`s, `.withLock` or `.withRLock`.

### Advanced

The APIs below utilize `LockHandle` ([Javadoc](https://javadoc.io/doc/party.iroiro/reactor-locks/latest/index.html)):

```java
public interface LockHandle {
    Mono<Void> mono();
    boolean cancel();
}
```

Subscribe to `mono()` to get informed when the lock is ready. Or you may cancel the request. In case of a failed cancellation (`false` returned), you need to `unlock` the lock yourself. 

<table>
<tr><th>Function</th><th>Returns</th></tr>
<tr><td>

`Lock::tryLock()`

`RWLock::tryRLock()`

Immediately requests the lock. Use it with `Mono.defer` if you want laziness.</td><td>

A `LockHandle` which you may use to cancel the lock request any time you want to. Just make sure to check the return value of `cancel` in case that the lock is already granted (which means you need to `unlock` it yourself.</td></tr>
<tr><td>

`Lock::unlock()`

`RWLock::rUnlock()`</td><td>

Returns `void` since you do not need to wait to unlock.</td></tr>
<tr><td>

`Lock::isLocked()`

`RWLock::isRLocked()`</td><td>

Whether the lock has been (either reader- or writer-) locked. For semaphores, it means whether the lock has reached the max lock holders. 

**Never** rely on the result of this.</td></tr>
</table>

### Wrapped Operators

In previous versions of this library, we have `lockOnNext` `unlockOnNext` to make locking easier. However, all these operators does not handle Mono cancellations (from downstream `timeout` for example) and we are deprecating them.

Use the fluent API or `withLock` / `withRLock` instead.

## Locks

All the lock implementations use CAS operations and are non-blocking.

### Lock Implementations

- `ReactiveLock`

  A basic lock. Internally using a queue.

- `BroadcastingLock`

  Functionally equivalent to `ReactiveLock`. Internally using a broadcast, which might degrade performance in *really extreme* cases.

- `ReactiveSemaphore`

  A lock allowing multiple lock holders.

### RWLock Implementations

- `ReactiveRWLock`

  A basic [readers-writer lock](https://en.wikipedia.org/wiki/Readers%E2%80%93writer_lock).
  Handling writer hunger by blocking (reactively, of course) further readers
  if there is a writer waiting.

# License

This project is licensed under the [Apache License Version 2.0](./LICENSE).

```text
Copyright 2022 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

The [javadoc.gradle](./javadoc.gradle) file is actually modified from that of [reactor-core](https://github.com/reactor/reactor-core/blob/main/gradle/javadoc.gradle), which is licensed under the [Apache License Version 2.0](./LICENSE) as well.
