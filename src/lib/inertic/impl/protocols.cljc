;;   Copyright (c) Leon Grapenthin.  All rights reserved.
;;   You must not remove this notice, or any other, from this software.

(ns inertic.impl.protocols)

(defprotocol Clock
  (now [this]
    "Return the current clock time in ms")
  (schedule [this t fn0]
    "Schedule f fn0 to be executed at t.  fn0 may not block.  If t is in
the past or present, fn0 execute fn0 immediately.  Return a sched-id.")
  (cancel [this sched-id]
    "Cancel a scheduled task.  Return true if the task was cancelled the
first time.")
  (stop [this]
    "Stops the clock, guarantees that no more tasks run."))
