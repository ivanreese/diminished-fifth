(ns app.orchestra
  (:require [app.math :as math]
            [app.phasor :as phasor]
            [app.player :as player]
            [app.span :as span]
            [app.state :refer [state history]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def key-change-steps 7)

; These trigger rescale
(def min-velocity 0.5)
(def max-velocity 2)
(def min-players 1)
(def max-players 18)
(def spawn-time (span/make 6 6)) ;(span/make 4 9))
(def key-change-time (span/make 100 100)) ;(span/make 60 600))


; HISTORY ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn trim-history-prop [m k v]
  (assoc m k (if (> (count v) 20000) (drop-last 1 v) v)))

(defn trim-history-all-props [history]
  (reduce-kv trim-history-prop {} history))

(defn trim-history [state]
  (swap! history update :orchestra trim-history-all-props)
  state)

(defn add-history [state key value]
  (swap! history update-in [:orchestra key] conj value)
  state)


; TICK ACCELERATION ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn tick-acceleration [state time]
  (let [accel (phasor/tick (get-in state [:orchestra :acceleration]) time)]
    (add-history state :acceleration (:value accel))
    (assoc-in state [:orchestra :acceleration] accel)))


; PLAYBACK RATE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn advance-playback-rate [state dt]
  (let [accel (get-in state [:orchestra :acceleration :value])
        playback-rate (* (get-in state [:orchestra :playback-rate])
                         (math/pow accel dt))]
   (add-history state :playback-rate playback-rate)
   (add-history state :scaled-playback-rate (* playback-rate (get-in state [:orchestra :scale])))
   (assoc-in state [:orchestra :playback-rate] playback-rate)))


(defn rescale-players [players factor]
  (mapv #(player/rescale % factor) players))

(defn rescale [state factor]
  (-> state
    (update-in [:orchestra :scale] * factor)
    (update-in [:orchestra :playback-rate] / factor)
    (update :players rescale-players factor)))


(defn restrict-playback-rate [state]
  (cond
    (< (get-in state [:orchestra :playback-rate]) min-velocity)
    (rescale state min-velocity)
    (> (get-in state [:orchestra :playback-rate]) max-velocity)
    (rescale state max-velocity)
    true
    state))

(defn tick-playback-rate [state dt]
  (-> state
      (advance-playback-rate dt)
      (restrict-playback-rate)))


; PLAYERS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare spawn)

(defn ensure-min-players [state time]
  (if (> min-players (count (:players state)))
    (spawn state time)
    state))

(defn update-players [players dt playback-rate transposition]
  (->> players
       (mapv #(player/tick % dt playback-rate transposition))
       (filterv :alive)))

(defn tick-players [state dt time]
  (-> state
      (update :players update-players
              dt
              (get-in state [:orchestra :playback-rate])
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
                                  (get-in state [:orchestra :next-player-index]))]
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
  (-> state
      (assoc :players [])
      (assoc :orchestra {:acceleration      (phasor/make 1 0.95 1.04 100) ; (phasor/make 1 1 1 300) ;
                         :key-change-time   (next-key-change-time time)
                         :next-player-index 0 ;715;(int (math/random 0 1000))
                         :playback-rate 1
                         :scale 1
                         :spawn-time        (next-spawn-time time)
                         :transposition 1})))

(defn tick [state dt time]
  (-> state
      (tick-acceleration time)
      (tick-playback-rate dt)
      (tick-players dt time)
      (tick-spawn time)
      (trim-history)))
