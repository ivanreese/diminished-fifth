(ns app.orchestra
  (:require [app.math :as math]
            [app.phasor :as phasor]
            [app.player :as player]
            [app.span :as span]
            [app.state :refer [state history history-max history-min]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def key-change-steps 7)
(def min-rescale-velocity 0.5) ; These trigger rescale
(def max-rescale-velocity 2)
(def min-players 1)
(def max-players 18)
(def spawn-time (span/make 6 6)) ;(span/make 4 9))
(def key-change-time (span/make 240 240)) ;(span/make 60 600))
(def min-velocity 0)
(def max-velocity 2)
(def velocity-pos-scale 1)
(def velocity-cycle-time 90)


; HISTORY ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn add-history [state key value skip]
  (when (zero? (mod (get-in state [:engine :count]) skip))
    (let [value (or value 0)]
      (swap! history update-in [:orchestra key] conj value)
      (swap! history-min update-in [:orchestra key] min value)
      (swap! history-max update-in [:orchestra key] max value)))
  state)

(defn init-history [key]
  (swap! history assoc-in [:orchestra key] [])
  (swap! history-min assoc-in [:orchestra key] Infinity)
  (swap! history-max assoc-in [:orchestra key] -Infinity))


; PLAYBACK RATE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn advance-velocity [state dt time]
  (let [velocity (math/scale (math/pow (math/sin (/ time velocity-cycle-time)) 3) -1 1 min-velocity max-velocity)
        scaled-velocity (max (* (+ 1 (/ time 1200)) velocity) velocity)]
    (add-history state :velocity scaled-velocity 30)
    (assoc-in state [:orchestra :velocity] scaled-velocity)))

(defn rescale-players [players factor]
  (mapv #(player/rescale % factor) players))

(defn rescale [state factor]
  (-> state
    (update-in [:orchestra :scale] / factor)
    (update :players rescale-players factor)))

(defn restrict-velocity [state]
  (cond
    (< (* (get-in state [:orchestra :scale]) (get-in state [:orchestra :velocity])) min-rescale-velocity)
    (rescale state min-rescale-velocity)
    (> (* (get-in state [:orchestra :scale]) (get-in state [:orchestra :velocity])) max-rescale-velocity)
    (rescale state max-rescale-velocity)
    true
    state))

(defn tick-velocity [state dt time]
  (-> state
      (advance-velocity dt time)))
      ; (restrict-velocity)))


; PLAYERS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare spawn)

(defn ensure-min-players [state time]
  (if (> min-players (count (:players state)))
    (spawn state time)
    state))

(defn update-players [players dt velocity transposition]
  (->> players
       (mapv #(player/tick % dt velocity transposition))
       (filterv :alive)))

(defn tick-players [state dt time]
  (-> state
      (update :players update-players
              dt
              (get-in state [:orchestra :velocity])
              (get-in state [:orchestra :transposition]))
      (ensure-min-players time)))


; SPAWN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn next-key-change-time [time]
  (+ time (span/random key-change-time)))

(defn next-spawn-time [time]
  (+ time (span/random spawn-time)))


(defn update-transposition [transposition]
  (loop [t (* transposition (math/pow 2 (/ key-change-steps 12)))]
    (if (> t 1.5)
      (recur (/ t 2))
      t)))


(defn key-change [state time]
  (-> state
    (assoc-in [:orchestra :key-change-time] (next-key-change-time time))
    (update-in [:orchestra :transposition] update-transposition)))
    
(defn check-key-change [state time]
  (if (>= time (get-in state [:orchestra :key-change-time]))
    (key-change state time)
    state))

(defn spawn [state time]
  (if (>= (count (:players state))
         max-players)
    state
    (let [new-player (player/make (last (:players state))
                                  (get-in state [:orchestra :next-player-index])
                                  (get-in state [:orchestra :velocity]))]
      (-> state
          (update :players conj new-player)
          (update-in [:orchestra :next-player-index] inc)
          (assoc-in [:orchestra :spawn-time] (next-spawn-time time))
          (check-key-change time)))))


(defn tick-spawn [state time]
  (if (>= time (get-in state [:orchestra :spawn-time]))
    (spawn state time)
    state))


; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn init [state time]
  (init-history :velocity)
  (-> state
      (assoc :players [])
      (assoc :orchestra {:key-change-time   (next-key-change-time time)
                         :next-player-index 0 ;715;(int (math/random 0 1000))
                         :velocity 1
                         :scale 1
                         :spawn-time        (next-spawn-time time)
                         :transposition 1})))

(defn tick [state dt time]
  (-> state
      (tick-velocity dt time)
      (tick-players dt time)
      (tick-spawn time)))
