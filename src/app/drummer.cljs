(ns app.drummer
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.history :as history]
            [app.math :as math]
            [app.state :refer [manifest state melodies samples]]
            [app.util :refer [snoop-logg]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.05)
(def max-age (* 4 28.8))
(def ahead-frac 0.1)
(def ahead-dist 0.45)

(defn rescale [player factor]
  (update player :scale * factor))
  
(defn get-sync-position [reference-player]
  (/ (:raw-position reference-player)
     (:scale reference-player)))

(defn update-position [player dt scaled-velocity]
  (-> player
    (update :position + (* scaled-velocity dt))
    (update :raw-position + (* scaled-velocity dt))))

(defn update-volume [player dt velocity]
  (assoc player :volume (math/clip
                         (if (:dying player)
                           (- (:volume player)
                              (* dt (+ 0.5 (/ velocity 2)) fade-rate))
                           (+ (:volume player)
                              (* dt (+ 0.5 (/ velocity 2)) fade-rate))))))

(defn update-dying [player]
  (assoc player :dying (or (:dying player)
                           (>= (:age player) (/ max-age (:duration player))))))

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

(defn update-played-note [player]
  (let [player-pos (:position player)
        duration (:duration player)]
    (if (< player-pos duration)
      player
      (-> player
        (assoc :history-active true)
        (play-note! duration)
        (update :position - duration)
        (update :age + 1)))))

(def subtypes [:kick :snare :tick :cym :special])
(def sample-choices [7 10 16 17 18 19 34 45 47 72 74 79])
(def duration-choices [28.8 19.2 14.4 9.6 7.2 4.8 3.6 2.4])

(defn get-subtype [index]
  (let [subtype-key (nth subtypes (mod index (count subtypes)))
        subtype (get-in @manifest ["drummer" (name subtype-key)])]
    (nth subtype (mod index (count subtype)))))

(defn make [sync-position index velocity]
  (let [sample-index (nth sample-choices (mod index (count sample-choices))) ;(mod index (count @samples))]
        scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
        duration (nth duration-choices (mod index (count duration-choices)))
        ahead (< (Math/random) ahead-frac)
        position (mod (* sync-position scale) duration)
        subtype-sample-name (get-subtype index)]
    {:type :drummer
     :index index
     :ahead ahead
     :sample (nth @samples sample-index)
     :position (+ position (if ahead ahead-dist 0))
     :raw-position position
     :scale scale
     :volume 0
     :age 0
     :max-volume (+ 0.5 (/ (Math/random) 1.5))
     :duration duration
     :history-active false
     :alive true
     :dying false
     :color (color/hsl (mod (* index 11) 360) 0 70)}))

(defn tick [player dt velocity]
  (let [scaled-velocity (* velocity (:scale player))]
    (-> player
        (update-position dt scaled-velocity)
        (update-dying)
        (update-volume dt velocity)
        (update-alive)
        (update-played-note))))
