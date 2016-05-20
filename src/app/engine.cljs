(ns app.engine
  (:require [app.state :refer [state callback]]))

(defn- tick [time-ms]
  (when (get-in @state [:engine :running])
    (.requestAnimationFrame js/window tick)
    (let [last-wall-time (get-in @state [:engine :wall-time])
          wall-time (/ time-ms 1000)
          dt (- wall-time last-wall-time)]
      (swap! state update-in [:engine :time] + dt)
      (swap! state assoc-in [:engine :wall-time] wall-time)
      (@callback dt))))

(defn- first-tick [time-ms]
  (.requestAnimationFrame js/window tick)
  (swap! state assoc-in [:engine :wall-time] (/ time-ms 1000)))

  
;; PUBLIC

(defn restart [state]
  (assoc-in state [:engine :time] 0))
  

(defn start [state]
  (if-not (get-in state [:engine :running])
    (.requestAnimationFrame js/window first-tick))
  (-> state
    (restart)
    (assoc-in [:engine :running] true)))

(defn stop [state]
  (assoc-in state [:engine :running] false))
