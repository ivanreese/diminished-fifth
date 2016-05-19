(ns app.engine
  (:require [app.state :refer [state callback]]))

(defn- tick [time-ms]
  (when (get-in @state [:engine :running])
    (.requestAnimationFrame js/window tick)
    (let [start-time (get-in @state [:engine :start-time])
          last-time (get-in @state [:engine :time])
          time (- (/ time-ms 1000) start-time)
          dt (- time last-time)]
      (swap! state assoc-in [:engine :time] time)
      (@callback dt))))

(defn- first-tick [time-ms]
  (swap! state assoc-in [:engine :start-time] (/ time-ms 1000))
  (.requestAnimationFrame js/window tick))

(defn- begin-raf! [state]
  (.requestAnimationFrame js/window first-tick)
  state)
  

;; PUBLIC

(defn init [state cb]
  (reset! callback cb)
  (assoc-in state [:engine :running] false))

(defn start [state]
  (if (get-in state [:engine :running])
    state
    (-> state
        (assoc-in [:engine :running] true)
        (assoc-in [:engine :time] 0)
        (begin-raf!))))

(defn stop [state]
  (assoc-in state [:engine :running] false))
