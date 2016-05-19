(ns app.orchestra
  (:require [app.math :as math]
            [app.phasor :as phasor]
            [app.player :as player]
            [app.span :as span]
            [app.state :refer [state]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def key-change-steps 7)

; These trigger rescale
(defonce min-velocity 0.5)
(defonce max-velocity 2)
(defonce spawn-time (span/make 6 6)) ;(span/make 4 9))
(defonce key-change-time (span/make 100 100)) ;(span/make 60 600))


; PLAYBACK RATE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn advance-playback-rate [state dt]
  (let [accel (get-in state [:orchestra :acceleration :value])
        playback-rate (* (get-in state [:orchestra :playback-rate])
                         (math/pow accel dt))]
   (assoc-in state [:orchestra :playback-rate] playback-rate)))


(defn rescale-players [players factor]
  (mapv #(player/rescale % factor) players))

(defn rescale [state factor]
  (-> state
    (update-in [:orchestra :playback-rate] / factor)
    (update-in [:orchestra :players] rescale-players factor)))


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

(defn spawn-player-if-none [state time]
  (if (empty? (get-in state [:orchestra :players]))
    (spawn state time)
    state))

(defn update-players [players dt playback-rate transposition]
  (->> players
       (mapv #(player/tick % dt playback-rate transposition))
       (filterv :alive)))

(defn tick-players [state dt time]
  (-> state
    (update-in [:orchestra :players] update-players
               dt
               (get-in state [:orchestra :playback-rate])
               (get-in state [:orchestra :transposition]))
    (spawn-player-if-none time)))


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
  (let [new-player (player/make (first (get-in state [:orchestra :players]))
                                (get-in state [:orchestra :next-player-index]))]
    (-> state
        (update-in [:orchestra :players] conj new-player)
        (update-in [:orchestra :next-player-index] inc)
        (assoc-in [:orchestra :spawn-time] (next-spawn-time time))
        (check-key-change time))))


(defn tick-spawn [state time]
  (if (>= time (get-in state [:orchestra :spawn-time]))
    (spawn state time)
    state))


; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn init [state time]
  (assoc state :orchestra {:acceleration      (phasor/make 0.99 0.99 0.99 300);(phasor/make 1 0.95 1.04 300)
                           :key-change-time   (next-key-change-time time)
                           :next-player-index 0
                           :playback-rate 1
                           :players []
                           :spawn-time        (next-spawn-time time)
                           :transposition 1}))

(defn tick [state dt time]
  (-> state
      (update-in [:orchestra :acceleration] phasor/tick time)
      (tick-playback-rate dt)
      (tick-players dt time)
      (tick-spawn time)))
