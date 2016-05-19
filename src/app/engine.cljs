(ns app.engine
  (:require [app.state :refer [state]]))

(defn- tick [time-ms]
  (when (:engine/running @state)
    (.requestAnimationFrame js/window tick)
    (let [time (/ time-ms 1000)
          last-time (:engine/time @state)
          dt (- time last-time)]
      (swap! state assoc :engine/time time)
      ((:engine/callback @state) dt))))

(defn- first-tick [time-ms]
  (swap! state assoc :engine/time (/ time-ms 1000))
  (.requestAnimationFrame js/window tick))

;; PUBLIC

(defn init [callback]
  (swap! state assoc :engine/running false)
  (swap! state assoc :engine/callback callback))

(defn start []
  (when-not (:engine/running @state)
    (swap! state assoc :engine/running true)
    (.requestAnimationFrame js/window first-tick)))

(defn stop []
  (swap! state assoc :engine/running false))
