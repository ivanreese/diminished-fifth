(ns app.engine
  (:require [app.state :refer [state]]))

(defn- tick [time-ms]
  (when (get-in @state [:engine :running])
    (.requestAnimationFrame js/window tick)
    (let [last-wall-time (get-in @state [:engine :wall-time])
          wall-time (/ time-ms 1000)
          dt (- wall-time last-wall-time)
          count (inc (get-in @state [:engine :count]))]
      (swap! state update-in [:engine :time] + dt)
      (swap! state assoc-in [:engine :wall-time] wall-time)
      (swap! state assoc-in [:engine :count] count)
      ((get-in @state [:engine :callback]) dt))))

(defn- first-tick [time-ms]
  (.requestAnimationFrame js/window tick)
  (swap! state assoc-in [:engine :wall-time] (/ time-ms 1000)))

  
;; PUBLIC

(defn restart [state callback]
  (-> state
    (assoc-in [:engine :time] 0)
    (assoc-in [:engine :count] 0)
    (assoc-in [:engine :callback] callback)))
  

(defn start [state]
  (when-not (get-in state [:engine :running])
    (.requestAnimationFrame js/window first-tick))
  (assoc-in state [:engine :running] true))

(defn stop [state]
  (assoc-in state [:engine :running] false))
