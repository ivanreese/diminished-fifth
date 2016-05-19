(ns app.orchestra
  (:require [app.math :as math]
            [app.player :as player]
            [app.state :refer [state]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

; NEGATIVE OVERRIDES (DISABLE TO OPT-IN TO EXPERIMENTAL BEHAVIOUR)
(def key-change-steps 7)

; POSITIVE OVERRIDES (ENABLE TO OPT-IN TO EXPERIMENTAL BEHAVIOUR)
; (def sample-ramp-time 0.2)

; These trigger rescale
(defonce min-velocity 0.5)
(defonce max-velocity 2)

(defn new-accumulator [initial min max]
  {:initial initial
   :value initial
   :min min
   :max max})

(def init-state {:orchestra/acceleration      (new-accumulator 1    0.95   1.04)
                 :orchestra/spawn-time        (new-accumulator 6    4      9)
                 :orchestra/key-change-time   (new-accumulator 120 60    600)
                 :orchestra/players []
                 :orchestra/next-index 0
                 :orchestra/playback-rate 1
                 :orchestra/transposition 1})

(defn decide [choices process]
  (get choices (int (* (count choices) (process)))))

(defn update-acceleration [accumulator]
  (assoc accumulator :value
         (math/scale (math/sin (/ (:engine/time @state) 300)) -1 1 (:min accumulator) (:max accumulator))))

(defn update-spawn-time [accumulator]
  (assoc accumulator :value
         (+ (:engine/time @state)
            (math/random (:min accumulator) (:max accumulator)))))

(defn update-key-change-time [accumulator]
  (assoc accumulator :value
         (+ (:engine/time @state)
            (math/random (:min accumulator) (:max accumulator)))))

(defn update-transposition [transposition]
  (let [steps (or key-change-steps (+ 1 (int (rand 12))))]
    (loop [transposition' (* transposition
                             (math/pow 2 (/ steps 12)))]
      (if (> transposition' 1.5)
        (recur (/ transposition' 2))
        transposition'))))

(defn key-change []
  (swap! state update :orchestra/key-change-time update-key-change-time)
  (swap! state update :orchestra/transposition update-transposition))
  
(defn rescale-players [players factor]
  (mapv #(player/rescale % factor) players))

(defn rescale [factor]
  (swap! state update :orchestra/playback-rate / factor)
  (swap! state update :orchestra/players rescale-players factor))

(defn tick-playback-rate [playback-rate dt]
  (* playback-rate (math/pow (get-in @state [:orchestra/acceleration :value]) dt)))

(defn tick-update-players [players dt]
  (let [playback-rate (:orchestra/playback-rate @state)
        transposition (:orchestra/transposition @state)]
    (->> (:orchestra/players @state)
         (mapv #(player/tick % dt playback-rate transposition))
         (filterv :alive))))

(defn spawn []
  (swap! state update :orchestra/spawn-time update-spawn-time)
  (swap! state update :orchestra/players conj (player/make (:orchestra/next-index @state)
                                                           (if-let [player (first (:orchestra/players @state))]
                                                             (player/get-sync-position player)
                                                             0)))
  (swap! state update :orchestra/next-index inc)
  (when (<= (get-in @state [:orchestra/key-change-time :value]) 0)
    (key-change)))
  

; PUBLIC


(defn init []
  (swap! state merge init-state))

(defn start []
  (if (= 0 (count (:orchestra/players @state)))
    (spawn)))

(defn tick [dt]
  ; (log (first (:orchestra/players @state)))
  (swap! state update :orchestra/acceleration update-acceleration)
  (swap! state update :orchestra/playback-rate tick-playback-rate dt)
  (swap! state update :orchestra/players tick-update-players dt)
  (when (< (:orchestra/playback-rate @state) min-velocity)
    (swap! state update :orchestra/playback-rate / min-velocity)
    (swap! state update :orchestra/players rescale-players min-velocity))
  (when (> (:orchestra/playback-rate @state) max-velocity)
    (swap! state update :orchestra/playback-rate / max-velocity)
    (swap! state update :orchestra/players rescale-players max-velocity))
  (when (>= (:engine/time @state) (get-in @state [:orchestra/spawn-time :value]))
    (spawn)))
