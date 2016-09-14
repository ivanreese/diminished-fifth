(ns app.drummer
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.history :as history]
            [app.math :as math]
            [app.state :refer [state melodies samples]]
            [app.util :refer [snoop-logg]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.05)
(def min-velocity (/ 1 32))
(def max-velocity 16)
(def max-position 64)

(defn update-history [player]
  (when (:history-active player)
    (history/add-history-prop player :next-position 1))
  player)

(defn get-duration [reference-drummer]
  (:duration reference-drummer))

(defn update-position [player dt velocity]
  (let [velocity (* velocity dt (:scale player))
        position (+ (:position player) velocity)]
    (-> player
      (assoc :position position))))

(defn update-volume [player dt velocity]
  (assoc player :volume (math/clip
                         (if (:dying player)
                           (- (:volume player)
                              (* dt (+ 0.5 (/ velocity 2)) fade-rate))
                           (+ (:volume player)
                              (* dt (+ 0.5 (/ velocity 2)) fade-rate))))))

(defn update-dying [player dt velocity]
  (assoc player :dying
         (or (:dying player)
             (> (:position player) max-position))))

(defn update-alive [player]
  (assoc player :alive
         (or (not (:dying player))
             (> (:volume player) 0))))

(defn play-note! [player next-pos]
  (audio/play (:sample player)
              {:pos (- (:position player) next-pos)
               :pitch 1
               :volume (* (:volume player) (:max-volume player))})
  player)

(defn update-upcoming-note [player]
  (update player :next-position + (:duration player)))

(defn update-played-note [player]
  (let [player-pos (:position player)
        next-pos (:next-position player)]
    (if (< player-pos next-pos)
      player
      (-> player
        (assoc :history-active true)
        (play-note! next-pos)
        (update-upcoming-note)))))

(def subtypes [:kick :snare :tick :cym :special])
(def sample-choices   [7 10 16 17 18 19 34 45 47 72 74 79 80])
(def duration-choices [28.8 14.4 7.2 3.6 1.8 .6])

(defn make [position index velocity]
  (let [sample-index (nth sample-choices (mod index (count sample-choices))) ;(mod index (count @samples))]
        duration (nth duration-choices (mod index (count duration-choices)))]
    (history/init-history index :next-position)
    {:type :drummer
     :index index ; Used by history and renderer
     :sample (nth @samples sample-index)
     :position position ; The current time we're at in the pattern, in ms
     :next-position (* (+ 1 (quot position duration)) duration)
     :scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
     :volume 0
     :max-volume (+ 0.5 (Math/random))
     :duration duration
     :history-active false
     :alive true ; When we die, we'll get filtered out of the list of players. Also used by history.
     :dying false
     :color (color/hsl (mod (* index 11) 360) 0 70)}))

(defn tick [player dt velocity]
  (-> player
      (update-position dt velocity)
      (update-dying dt velocity)
      (update-volume dt velocity)
      (update-alive)
      (update-played-note)
      (update-history)
      (history/trim-history)))

(defn rescale [player factor]
  (update player :scale * factor))
