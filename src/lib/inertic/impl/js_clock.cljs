;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.impl.js-clock
  (:require
   [inertic.impl.protocols :as p]))

(defrecord JSClock [timers]
  p/Clock
  (now [this] (.now js/Date))
  (schedule [this t fn0]
    (let [dela (- t (p/now this))]
      (let [h (volatile! nil)
            h (vreset! h (.setTimeout js/window
                                      (fn [_]
                                        (swap! timers disj @h)
                                        (fn0))
                                      (max 0 dela)))]
        (swap! timers conj h)
        h)))
  (cancel [this sched]
    (when (@timers sched)
      (swap! timers disj sched)
      (.clearTimeout js/window sched)
      true))
  (stop [this]
    (run! #(p/cancel this %) @timers)
    (reset! timers #{})))

(defn timeout-clock []
  (->JSClock (atom #{})))
