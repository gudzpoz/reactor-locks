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
  /*  lock   */ .transform(lock::lockOnNext)
                .doOnNext(this::someJob)
                .map(t -> t.toString())
  /* unlock  */ .transform(lock::unlockOnNext);
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
  <version>0.3.0</version>
</dependency>
```

</details>

<details>
<summary>Gradle</summary>

```groovy
implementation 'party.iroiro:reactor-locks:0.3.0'
```

</details>

## Usage

### Basic

> You need to ensure every successful locking is followed by unlocking.

> **Do not** attempt to use `lock.lock().timeout(...)`. Use `Lock::tryLock` instead.


<table>
<tr><th>Function</th><th>Returns</th></tr>
<tr><td>

`Lock::lock()`

`RWLock::lock()`</td><td>

A `Mono<Void>` that emits success only after acquiring the lock.</td></tr>
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

<table>
<tr><th>Operator</th><th>Example</th></tr>
<tr><td>

`Lock::lockOnNext`

`RWLock::rLockOnNext`</td><td>

```java
return mono
      .transform(lock::lockOnNext)
      .doOnNext(t -> log.info("Lock Acquired"));
```
</td>
</tr>
<tr><td>

`Lock::unlockOnNext`

`RWLock::rUnlockOnNext`</td><td>

```java
return lockedMono
      .transform(lock::unlockOnNext)
      .doOnNext(t -> log.info("Lock Released"));
```
</td>
</tr>
<tr><td>

`Lock::unlockOnEmpty`

`RWLock::rUnlockOnEmpty`</td><td>

```java
return lockedMono
      .transform(lock::unlockOnEmpty)
      .switchIfEmpty(Mono.fromRunnable(() ->
              log.info("Lock released");
      );
```
</td>
</tr>
<tr><td>

`Lock::unlockOnError`

`RWLock::rUnlockOnError`</td><td>

```java
return lockedMono
      .flatMap(ignored -> Mono.error(new Exception()))
      .transform(lock::unlockOnError)
      .doOnError(t -> log.info("Lock Released"));
```
</td>
</tr>
<tr><td>

`Lock::unlockOnTerminate`

`RWLock::rUnlockOnTerminate`</td><td>

```java
return lockedMono
      .transform(lock::unlockOnTerminate)
      .doOnTerminate(t -> log.info("Lock Release"));
```
</td>
</tr>
</table>

## Locks

### Lock Implementations

- `ReactiveLock`
  
  A basic lock. Internally using a queue.
  
- `BroadcastingLock`

  Functionally equivalent to `ReactiveLock`. Internally using a broadcast, which might degrade performance in *really extreme* cases.

- `Semaphore`

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
