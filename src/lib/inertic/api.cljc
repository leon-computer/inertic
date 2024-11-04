;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.api
  (:require
   [inertic.impl.protocols :as p]
   [inertic.impl.test-clock :as tc]
   #?(:clj [inertic.impl.jvm-clock :as c]
      :cljs [inertic.impl.js-clock :as c])))

(defn seconds [n] (* 1000 n))

(defn minutes [n] (* 60 1000 n))

(defn hours [n] (* 60 60 1000 n))

(def ^:private id-ctr (atom 0))

(defn gen-id
  "Generate suitable id argument for one time use."
  [] (swap! id-ctr inc))

(defn run-in
  "Runs fn0 once, in ms, from now."
  ([clock ms fn0] (run-in clock ms fn0 (gen-id)))
  ([clock ms fn0 id]
   (p/schedule clock (+ (p/now clock) ms) fn0 id)
   id))

(defn run-at
  "See run-in."
  ([clock ms fn0] (run-at clock ms fn0 (gen-id)))
  ([clock start-t-ms fn0 id]
   (p/schedule clock start-t-ms fn0 id)
   id))

(defn run-every
  "Runs task every ms, at fixed rate.  I. e. a slow task / env parked by
  OS will `make up` for a missed execution."
  ([clock ms fn0] (run-every clock (p/now clock) ms fn0))
  ([clock start-t-ms ms fn0]
   (let [active? (volatile! true)
         go (fn go [start-t-ms]
              (p/schedule clock
                          start-t-ms
                          (fn []
                            (when @active?
                              (fn0)
                              (go (+ start-t-ms ms))))))]
     (go (+ start-t-ms ms))
     active?)))

(defn stop
  "Stops the clock, guarantees that no more tasks run."
  [c] (p/stop c))

(defn cancel
  "Cancel the task scheduled with id."
  [c id]
  (if (volatile? id)
    (vreset! id false)
    (p/cancel c id)))

(defn clock
  "Create a clock instance.  Opts:

  :absolute? - Only on JVM: timestamps are absolute.  I. e. NTP
               adjustments of the system clock will impact the
               scheduling (System/currentTimeMillis is used instead of
               nanoTime)."
  [& {:keys [absolute?] :as opts}]
  #?(:clj (if absolute?
            (c/jvm-timer)
            (c/jvm-scheduler))
     :cljs (c/timeout-clock)))

(defn now
  "Returns the clocks current time in milliseconds."
  [clock]
  (p/now clock))

(defn test-clock
  "Produces a test clock that starts at 0 and only advances via
  `skip`."
  []
  (tc/test-clock))

(defn skip
  "Adds ms to test clock tc and fires tasks that are expected to fire.

  If invoked with no argument, skips to the next point in time where
  tasks are scheduled."
  ([tc]
   (let [tc (:clock tc tc)]
     (tc/skip-to-next tc nil)))
  ([tc ms]
   (tc/skip (:clock tc tc) ms)))

(comment
  (let [c (clock)
        s1 (run-in c (seconds 3) #(println "Hello s1"))
        s2 (run-in c (seconds 6) #(println "Hello s2"))
        rec (run-every c (now c) (seconds 2) #(println "every 2 secs"))
        _ (Thread/sleep (seconds 5))
        _ (println "5 secs over")
        _ (cancel c s2)
        _ (Thread/sleep (seconds 5))
        _ (cancel c rec)
        _ (println "done")])

  (let [c (test-clock)
        s1 (run-in c (seconds 3) #(println "Hello s1"))
        s2 (run-in c (seconds 6) #(println "Hello s2"))
        rec (run-every c (now c) (seconds 2) #(println "every 2 secs"))
        _ (skip c (seconds 5))
        _ (println "5 secs over")
        _ (cancel c s2)
        _ (skip c (seconds 5))
        _ (cancel c rec)
        _ (println "done")
        ])

  ;; Bag example
  (let [apple-collector
        (fn [c]
          (let [bag (atom 0)
                add-apple #(println "In bag:" (swap! bag inc))
                _ (run-in c (seconds 10)
                          #(stop c))
                _ (run-every c (seconds 1) add-apple)]
            bag))]

    (println "Running apple collector:")
    (apple-collector (clock))
    (Thread/sleep (seconds 11)) ;; wait for apple collector to finish

    (let [tc (test-clock)
          _ (apple-collector tc)]
      ;; The following all prints immediately
      (println "Running simulated apple collector:")

      (println "Advancing to first collection")
      (skip tc)

      (println "Advancing 4 seconds after first collection")
      (skip tc (seconds 4))

      (println "Advancing 20 more seconds")
      (skip tc (seconds 20)))))

(comment
  ;; Testing precision on the JVM: The built in .scheduleAtFixedRate
  ;; doesn't seem to do a better job then our technique

  (defn gaps [coll]
    (->> coll (partition 2 1) (map (fn [[v1 v2]] (- v2 v1))) (into #{})))

  (let [c (clock)
        the-ms (volatile! [])
        sched
        (-> c
            (run-every 1 (fn []
                           (vswap! the-ms conj (now c)))))
        _ (-> c
              (run-in 1000 (fn [] (cancel c sched)
                             (let [res @the-ms]
                               (println (count res) (gaps res))))))]

    )

  (let [c (clock)
        the-ms (volatile! [])
        exec (java.util.concurrent.Executors/newSingleThreadScheduledExecutor)
        sched
        (-> exec
            (.scheduleAtFixedRate
             (fn []
               (vswap! the-ms conj
                       (quot (System/nanoTime) 1000000)))
             0
             1
             java.util.concurrent.TimeUnit/MILLISECONDS))
        _ (-> exec
              (.schedule
               ^java.lang.Runnable
               (fn []
                 (.cancel sched true)
                 (let [res @the-ms]
                   (println (count res) (gaps res))))
               1000
               java.util.concurrent.TimeUnit/MILLISECONDS))]


    )

  ;; test clock should execute self scheduling
  (let [c (test-clock)
        ctr (atom 10)
        go (fn go [] (when (pos? (swap! ctr dec))
                       (run-in c 1 go)))
        _ (go)
        _ (skip c 1)]
    @ctr)

  )
