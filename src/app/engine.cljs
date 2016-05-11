(ns app.engine
  (:require [app.state :refer [state]]))

(defn- tick [time-ms]
  (let [last-time (:engine/time @state)
        new-time (/ time-ms 1000)
        dt (- new-time last-time)]
    ((:engine/callback @state) dt)
    (swap! state assoc :engine/time new-time)
    (if (:engine/running @state)
      (.requestAnimationFrame js/window tick))))

(defn- first-tick [time-ms]
  (swap! state assoc :engine/time (/ time-ms 1000))
  (.requestAnimationFrame js/window tick))

;; PUBLIC

(defn start [cb]
  (swap! state assoc :engine/callback cb)
  (when-not (:engine/running @state)
    (swap! state assoc :engine/running true)
    (.requestAnimationFrame js/window first-tick)))

(defn stop []
  (when (:engine/running @state)
    (swap! state assoc :engine/running false)))
