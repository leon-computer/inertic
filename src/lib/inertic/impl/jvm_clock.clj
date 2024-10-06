;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.impl.jvm-clock
  (:require
   [inertic.impl.protocols :as p])
  (:import
   [java.util Date TimerTask Timer]
   [java.util.concurrent
    ScheduledExecutorService
    ScheduledFuture
    Executors
    TimeUnit]))

(set! *warn-on-reflection* true)

(defn- ^TimerTask task [f]
  (proxy [TimerTask] []
    (run [] (f))))

(defn make-id-gen [] (let [v (atom 0)] (fn [] (swap! v inc))))

;; Uses currentTimeMillis, more suitable for clock adjusted execution
(defrecord JVMTimer [ttasks ^Timer timer id-gen]
  p/Clock
  (now [this] (System/currentTimeMillis))
  (schedule [this t fn0]
    (let [id (id-gen)
          task (task (fn []
                       (swap! ttasks dissoc id)
                       (fn0)))
          _ (swap! ttasks assoc id task)
          _ (.schedule timer task (Date. ^long t))]
      id))
  (cancel [this sched-id]
    (let [[before] (swap! ttasks dissoc sched-id)]
      (when-let [task (get before sched-id)]
        (.cancel ^TimerTask task))))
  (stop [this]
    (reset! ttasks {})
    (.cancel timer)))

;; Uses nanoTime, more suitable for relative
(defrecord JVMScheduler [ttasks ^ScheduledExecutorService scheduler id-gen]
  p/Clock
  (now [this] (quot (System/nanoTime) 1000000))
  (schedule [this t fn0]
    (let [dela (- t (p/now this))]
      (let [id (id-gen)
            scheduled (promise)
            task
            (.schedule scheduler
                       ^Runnable
                       (fn []
                         @scheduled ;; ensure to cleanup after task is assoced
                         (swap! ttasks dissoc id)
                         (fn0))
                       ^long (max dela 0)
                       TimeUnit/MILLISECONDS)
            _ (swap! ttasks assoc id task)
            _ (deliver scheduled nil)]
        id)))
  (cancel [this sched-id]
    (let [[before] (swap-vals! ttasks dissoc sched-id)]
      (when-let [task (get before sched-id)]
        (.cancel ^ScheduledFuture task false))))
  (stop [this]
    (reset! ttasks {})
    (.shutdown scheduler)))

(defn jvm-timer []
  (map->JVMTimer {:timer (Timer.) :ttasks (atom {}) :id-gen (make-id-gen)}))

(defn jvm-scheduler []
  (map->JVMScheduler {:scheduler (Executors/newSingleThreadScheduledExecutor)
                      :ttasks (atom {})
                      :id-gen (make-id-gen)}))

(comment
  (def myc
    (let [c (jvm-timer)
          now (p/now c)
          _ (p/schedule c (+ now (* 1000 3)) (fn [] (println "Hey")))
          _ (p/schedule c (+ now (* 1000 10)) (fn [] (println "Hey")))]
      c))

  (p/stop myc)

  (def mys
    (let [c (jvm-scheduler)
          now (p/now c)
          _ (p/schedule c (+ now (* 1000 3)) (fn [] (println "Hey")))
          _ (p/schedule c (+ now (* 1000 10)) (fn [] (println "Hey")))]
      c))

  (p/stop myc))


(comment
  (.schedule (Executors/newSingleThreadScheduledExecutor)
             (fn []
               (println "ehhlo"))
             0
             TimeUnit/MILLISECONDS)

  )
