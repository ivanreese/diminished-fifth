(ns app.time
  (:require [app.state :refer [state]]))

(defn- tick [time-ms]
  (let [last-time (:time/time @state)
        new-time (/ time-ms 1000)
        dt (- new-time last-time)]
    ((:time/callback @state) dt)
    (swap! state assoc :time/time new-time)
    (if (:time/running @state)
      (.requestAnimationFrame js/window tick))))

(defn- first-tick [time-ms]
  (swap! state assoc :time/time (/ time-ms 1000))
  (.requestAnimationFrame js/window tick))

;; PUBLIC

(defn start [cb]
  (when-not (:time/running @state)
    (swap! state assoc :time/running true)
    (swap! state assoc :time/callback cb)
    (.requestAnimationFrame js/window first-tick)))

(defn stop []
  (when (:time/running @state)
    (swap! state assoc :time/running false)))
