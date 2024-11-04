;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.impl.js-clock
  (:require
   [inertic.impl.protocols :as p]))

(defrecord JSClock [timers]
  p/Clock
  (now [this] (.now js/Date))
  (schedule [this t fn0 id]
    (let [dela (- t (p/now this))]
      (let [h (js/setTimeout (fn [_]
                               (swap! timers dissoc id)
                               (fn0))
                             (max 0 dela))]
        (swap! timers assoc id h)
        id)))
  (cancel [this sched]
    (when-let [timeout-id (@timers sched)]
      (swap! timers dissoc sched)
      (js/clearTimeout timeout-id)
      true))
  (stop [this]
    (run! #(p/cancel this %) @timers)
    (reset! timers #{})))

(defn timeout-clock []
  (->JSClock (atom {})))
