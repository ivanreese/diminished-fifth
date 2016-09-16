(ns app.orchestra
  (:require [app.drummer :as drummer]
            [app.history :as history]
            [app.math :as math]
            [app.phasor :as phasor]
            [app.player :as player]
            [app.span :as span]
            [app.state :refer [state]]
            [app.util :refer [snoop-logg]]
            [cljs.pprint :refer [pprint]]))

(def key-change-steps 7)
(def min-players 1)
(def max-players 64)
(def spawn-time (span/make 1 6))
(def key-change-time (span/make 240 240)) ;(span/make 60 600))
(def min-sin (- 1 .03))
(def max-sin (+ 1 .03))
(def rescale-vel-min 0.5)
(def rescale-vel-max 2)
(def cycle-time 40)
(def drummer-frac .5)
(def initial-vel 1)


; VELOCITY ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn tick-velocity [state dt time]
  (let [accel (math/scale (math/pow (math/sin (/ time cycle-time)) 3) -1 1 min-sin max-sin)
        velocity (* (get-in state [:orchestra :velocity]) (Math/pow accel dt))
        step 10]
    (history/add-history :orchestra :accel accel step)
    (history/add-history :orchestra :velocity velocity step)
    (assoc-in state [:orchestra :velocity] velocity)))


; RESCALE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn rescale-player [player factor]
  (case (:type player)
        :player (update player :scale * factor)
        :drummer (drummer/rescale player factor)))

(defn rescale-players [players factor]
  (mapv #(rescale-player % factor) players))

(defn rescale [state factor]
  (-> state
    (update-in [:orchestra :velocity] / factor)
    (update :players rescale-players factor)))

(defn tick-rescale [state]
  (let [velocity (get-in state [:orchestra :velocity])]
    (if (> velocity rescale-vel-max)
      (rescale state rescale-vel-max)
      (if (< velocity rescale-vel-min)
        (rescale state rescale-vel-min)
        state))))


; PLAYERS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(declare spawn)

(defn ensure-min-players [state time]
  (if (> min-players (count (:players state)))
    (spawn state time)
    state))

(defn do-tick [player dt velocity transposition]
  (case (:type player)
        :player (player/tick player dt velocity transposition)
        :drummer (drummer/tick player dt velocity)))

(defn update-players [players dt velocity transposition]
  (->> players
       (mapv #(do-tick % dt velocity transposition))
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

(defn get-sync-position [player]
  (case (:type player)
        :player (player/get-sync-position player)
        :drummer (drummer/get-sync-position player)))
    
(defn spawn [state time]
  (let [players (:players state)
        nplayers (count players)]
    (if (>= nplayers max-players)
      state
      (let [make-player (if (< (Math/random) drummer-frac) drummer/make player/make)
            position (if (zero? nplayers) 0 (get-sync-position (last players)))
            new-player (make-player position
                                    (get-in state [:orchestra :next-player-index])
                                    (get-in state [:orchestra :velocity]))]
        (-> state
            (update :players conj new-player)
            (update-in [:orchestra :next-player-index] inc)
            (assoc-in [:orchestra :spawn-time] (next-spawn-time time))
            (check-key-change time))))))

(defn tick-spawn [state time]
  (if (>= time (get-in state [:orchestra :spawn-time]))
    (spawn state time)
    state))


; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn init [state time]
  (history/init-history :orchestra :velocity)
  (history/init-history :orchestra :accel)
  (-> state
      (assoc :players [])
      (assoc :orchestra {:key-change-time (next-key-change-time time)
                         :next-player-index 0; (int (math/random 0 10000))
                         :velocity initial-vel
                         :spawn-time (next-spawn-time time)
                         :transposition 1})))

(defn tick [state dt time]
  (-> state
      (tick-velocity dt time)
      (tick-rescale)
      (tick-players dt time)
      (tick-spawn time)))
