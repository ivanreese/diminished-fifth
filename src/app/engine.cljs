(ns app.engine
  (:require [app.state :refer [state]]
            [app.util :refer [log]]))

; The sim-dt gives an upper bound on how long we can spend calculating the next state.
; Because we want a stable/deterministic simulation, we update at a fixed/known frequency.
; The render loop runs at a different rate than this, updating as fast as possible (60fps),
; and will use accumulation and interpolation to make up for the frequency/phase difference.
(defonce sim-dt (/ 1 60))

;; If our last tick took longer than this, we'll cap the wall-dt at this limit.
;; This avoids a death spiral where longer frames trigger more (integrate) calls, which causes even longer frames..
(defonce wall-dt-limit 0.1)

; State Variables
(defn- initialize []
  (swap! state merge {:engine/wall-time 0
                      :engine/sim-time 0
                      :engine/accumulator sim-dt
                      :engine/this-sim-state {:x  0 :v 0}
                      :engine/prev-sim-state {:x  0 :v 0}
                      :engine/integrate nil
                      :engine/render nil}))

(defonce initialized (do (initialize) true))

(defn- finish-tick [next-accumulator]
  (let [alpha (/ next-accumulator sim-dt)]
    (swap! state assoc :engine/accumulator next-accumulator)
    ((:engine/render @state) alpha)))


(defn- tick [time-ms]
  (when (:engine/running @state)
    (.requestAnimationFrame js/window tick)

    (let [next-wall-time (/ time-ms 1000)
          this-wall-time (:engine/wall-time @state)
          wall-dt (min (- next-wall-time this-wall-time) wall-dt-limit)
          accumulator (+ (:engine/accumulator @state) wall-dt)
          integrate (:engine/integrate @state)]

      (swap! state assoc :engine/wall-time next-wall-time)

      (if (< accumulator sim-dt)
        (finish-tick accumulator)

        (loop [this-sim-state (:engine/this-sim-state @state)
               this-sim-time (:engine/sim-time @state)
               this-accumulator accumulator]
          (let [next-sim-state (integrate this-sim-state this-sim-time sim-dt)
                next-sim-time (+ this-sim-time sim-dt)
                next-accumulator (- this-accumulator sim-dt)]
            
            (if (>= next-accumulator sim-dt)
              (recur next-sim-state next-sim-time next-accumulator)
              
              (do
                (swap! state assoc :engine/prev-sim-state this-sim-state)
                (swap! state assoc :engine/this-sim-state next-sim-state)
                (swap! state assoc :engine/sim-time next-sim-time)
                (finish-tick next-accumulator)))))))))

(defn- first-tick [time-ms]
  (swap! state assoc :engine/wall-time (/ time-ms 1000))
  (.requestAnimationFrame js/window tick))

;; PUBLIC

(defn start [integrate render]
  (swap! state assoc :engine/integrate integrate)
  (swap! state assoc :engine/render render)
  (when-not (:engine/running @state)
    (swap! state assoc :engine/running true)
    (.requestAnimationFrame js/window first-tick)))

(defn stop []
  (when (:engine/running @state)
    (swap! state assoc :engine/running false)))
