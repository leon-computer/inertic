;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.impl.test-clock
  (:require
   [inertic.impl.protocols :as p]))

(defn- fire-tasks! [state]
  (let [exec (volatile! nil)]
    (swap!
     state
     (fn [{:keys [t-now scheduled tasks] :as state}]
       (let [kvs (->> scheduled
                      (take-while #(<= (key %) t-now)))
             task-ids (mapcat val kvs)
             _ (vreset! exec (map (comp first tasks) task-ids))]
         (-> state
             (update :tasks
                     (fn [tasks] (apply dissoc tasks task-ids)))
             (update :scheduled
                     (fn [scheduled] (apply dissoc scheduled (map key kvs))))))))
    (when-let [exec (seq @exec)]
      (run! #(%) exec)
      true)))

(defn- update-cleaning [m k f]
  (if-let [v (not-empty (f (get m k)))]
    (assoc m k v)
    (dissoc m k)))

(defrecord TestClock [state]
  p/Clock
  (now [this] (:t-now @state))
  (schedule [this t fn0 id]
    (let [[before _]
          (-> state
              (swap-vals!
               (fn [{:keys [t-now scheduled]
                     :as state}]
                 (let [t (max t t-now)]
                   (-> state
                       (update-in [:scheduled t] (fnil conj []) id)
                       (assoc-in [:tasks id] [fn0 t]))))))]
      (fire-tasks! state)
      id))
  (cancel [this sched-id]
    (let [[before after]
          (-> state
              (swap-vals!
               (fn [state]
                 (if-let [[task t] (-> state :tasks (get sched-id))]
                   (-> state
                       (update :tasks dissoc sched-id)
                       (update :scheduled update-cleaning t #(disj % sched-id)))
                   state))))]
      (not= before after)))
  (stop [this]
    (swap! state assoc :stopped? true)
    (run! #(p/cancel this %) (keys (:tasks @state)))))

(defn test-clock
  "Create a test clock."
  []
  (map->TestClock {:state (atom {:t-now 0
                                 :scheduled (sorted-map)})}))

(defn skip-to-next
  "Skip forward in time until reaching the next point in time where tasks
  are scheduled, or max-t.

  Execute scheduled tasks."
  [test-clock max-t]
  (swap! (:state test-clock)
         (fn [{:keys [t-now scheduled stopped?] :as state}]
           (if stopped?
             state ;; don't advance t-now anymore
             (-> state
                 (assoc :t-now (max t-now
                                    (if-let [t-next (ffirst scheduled)]
                                      (cond-> t-next
                                        max-t (min max-t))
                                      (or max-t t-now))))))))
  (while (fire-tasks! (:state test-clock))))

(defn skip
  [test-clock ms]
  (assert (nat-int? ms))
  (let [until (+ (p/now test-clock) ms)]
    (while (and (< (p/now test-clock) until)
                (not (:stopped? @(:state test-clock))))
      (skip-to-next test-clock until))))

(comment
  (let [c (test-clock)
        start (p/now c)
        fired (atom [])
        ptask #(fn [] (prn (swap! fired conj %)))
        atm #(do (skip c %) (prn "now: " (p/now c)))
        _ (p/schedule c (+ start 10) (ptask 10))
        _ (p/schedule c (+ start 10) (ptask 10))
        _ (p/schedule c (+ start 5) (ptask 5))
        _ (atm 5)
        _ (p/schedule c (+ start 5) (ptask 5))
        _ (atm 1)
        _ (p/schedule c (+ start 20) (ptask 20))
        id1 (p/schedule c (+ start 20) (ptask 20))
        id2 (p/schedule c (+ start 20) (ptask 20))
        _ (atm 4)
        _ (prn "cancel id1" (p/cancel c id1))
        _ (atm 11)
        _ (prn "cancel id2" (p/cancel c id2))]))
