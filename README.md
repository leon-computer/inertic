# Inertic

## Rationale

Programs with timers can be difficult to test and debug. Developers often resort to setting smaller timeouts, injecting artificial delays, or writing mocks to make timing based routines testable. Such workarounds can lead to unreliable tests, increased flakiness, and added complexity.

Inertic's CLJC API solves this problem by providing a lazy/virtual test clock, which can be programmatically advanced in unit tests and be used interchangeably with a real clock wrapping the hosts scheduling facilities. This allows developers to control and simulate time based behaviors.

## Motivating example

Here is an example of a time based program, written against inertic.  It takes a clock as its argument.

```clojure
(require '[inertic.api :as i])

(defn apple-collector [c]
  (let [bag (atom 0)
        add-apple #(println "In bag:" (swap! bag inc))
        worker (i/run-every c (i/seconds 1) add-apple)]
    (i/run-in c (i/seconds 3) #(i/cancel c worker))
    bag))
```

The program prints a count of collected apples every second. After 3 seconds, it stops. By taking a clock as its argument `c`, this program can be run with both real time and with virtual time.

Let's start this program by passing the host clock abstraction.

```clojure
(apple-collector (i/clock))

In bag: 1
In bag: 2
In bag: 3

```
This made us wait three seconds.

But we can also run `apple-collector` with a test clock, allowing us to advance time virtually and see a result immediately.

```clojure
(def tc (i/test-clock))

(apple-collector tc)

;; Skip two seconds
(i/skip tc (i/seconds 2))

In bag: 1
In bag: 2

;; We could assert that only two apples have been collected here

(i/skip tc) ;; Instead of passing an amount of time to skip, we can
            ;; also just skip to the next point in time where something
            ;; is scheduled

In bag: 3

```

## Usage

To use inertic, avoid using host timers/schedulers directly.  Also, don't use `(System/currentTimeMillis)` directly, use `(i/now my-clock)`.  This is required to support correct lazy execution via `i/test-clock` and `i/skip`.

When using inertics timers, make sure that you don't pass blocking lambdas.  Inertic is not designed to manage execution of long running tasks on timer threads.  On the JVM, it will be best if you dispatch work to another thread immediately so that other timer effects are not blocked.

You may not be able to control all timing sensitive aspects of your application with inertic yet, for instance due to other libraries not using inertic.  There is still value in using inertic.  It not only provides a sane abstraction over CLJ/CLJS timer logic in a single API, you may likely end up with routines that depend only on inertic and can be tested in isolation from the rest of your application.

## Design

Inertic is intended to be a low level API and its users are expected to wrap it according to their needs.  For instance, some applications may find value in having a single global clock and binding it dynamically.  For others, passing down different clocks abstracting different host timers may be the fine grained level of control they need.  Users who rely on core.async would use a core.async timeout channel based on inertic.

While JVM schedulers support threadpools to execute long running timer tasks, inertic doesn't utilize this and instead delegates the execution problem back to the user, where it should be solved more correctly.  In modern applications, the same long running tasks can be triggered by a variety of events and there doesn't seem a particular reason why certain long running tasks should run on the same threadpool, just because they were triggered by a timer.

## Release status and implementation notes

I don't intend to break the API namespace / its behavior.

.impl... stuff might change, like for instance the Clock protocol.

inertic is used in medium critical production applications.

## Future

There will likely be a core.async timeout channel replacement that can be controlled with inertics clocks.  The same could be done for promesa.


## License & Copyright
Copyright (c) Leon Grapenthin

Distributed under the MIT License