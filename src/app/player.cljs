(ns app.player
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.history :as history]
            [app.math :as math]
            [app.state :refer [state melodies samples]]
            [app.util :refer [snoop-logg]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.03)
(def transpose-on-repeat 2)
(def initial-transposition 1)
(def min-transposition (/ initial-transposition 16))
(def max-transposition (* initial-transposition 8))
(def min-death-velocity (/ 1 64))
(def max-death-velocity 32)
(def drone-frac .1)


;; MAKE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn get-sync-position [reference-player]
  (let [position (:position reference-player)
        duration (:duration reference-player)]
    (/ (if (< position 0) (+ position duration) position)
       (:scale reference-player))))
  
(defn determine-starting-note [melody player-position]
  (let [notes (:notes melody)
        upcoming-note-index (:index (first (filter #(>= (:position %) player-position) notes)))]
    (or upcoming-note-index 0)))


;; UPDATES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn update-position [player dt scaled-velocity]
  (update player :position + (* scaled-velocity dt)))

(defn update-dying [player scaled-velocity]
  (assoc player :dying (or (:dying player)
                           (< scaled-velocity min-death-velocity)
                           (> scaled-velocity max-death-velocity)
                           (<= (:transposition player) min-transposition)
                           (>= (:transposition player) max-transposition))))

(defn update-volume [player dt velocity]
  (assoc player :volume (math/clip ((if (:dying player) - +)
                                    (:volume player)
                                    (* dt (+ 0.5 (/ velocity 2)) fade-rate)))))

(defn update-alive [player]
  (assoc player :alive (or (not (:dying player))
                           (> (:volume player) 0))))

(defn update-history [player]
  (if (:history-active player)
    (-> player
      (history/add-history-prop :current-pitch 1)
      (history/trim-history))
    player))


; PLAY NOTE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn update-upcoming-note [player]
  (let [melody (:melody player)
        notes (:notes melody)
        duration (:duration melody)
        upcoming-note (inc (:upcoming-note player))]
    (if (< upcoming-note (count notes))
      (assoc player :upcoming-note upcoming-note)
      (-> player
        (assoc :upcoming-note 0)
        (update :position - duration)
        (update :transposition * transpose-on-repeat)))))

(defn play-note! [player note pitch]
  (if (> 64 pitch (/ 1 16))
    (let [xpos-vol-factor (/ 1 (+ 1 (/ (Math/pow (Math/log2 (:transposition player)) 2) 128)))] ;; 1/(1+x^2) is a gauss-like fn. We're using 1/(1+(x^2 / 128)), which tweaks it to work better with the stuff we get
      (audio/play (:sample player)
                  {:pos (- (:position player) (:position note))
                   :pitch pitch
                   :volume (* (:volume player) (:volume note) xpos-vol-factor)})))
  player)

(defn update-played-note [player key-transposition]
  (let [note (nth (:notes (:melody player)) (:upcoming-note player))
        player-pos (:position player)
        note-pos (:position note)
        note-pitch (if (:drone player) 1 (:pitch note))
        pitch (* note-pitch (:transposition player) key-transposition)]
    (if (< player-pos note-pos)
      player
      (-> player
        (assoc :history-active true)
        (assoc :current-pitch pitch)
        (play-note! note pitch)
        (update-upcoming-note)))))


;; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make [sync-position index velocity]
  (let [melody-index (mod index (count @melodies))
        sample-index (mod index (count @samples))
        scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
        melody (nth @melodies melody-index)
        position (mod (* sync-position scale) (:duration melody))
        upcoming-note (determine-starting-note melody position)]
    (history/init-history index :current-pitch)
    {:type :player
     :index index
     :melody melody
     :sample (nth @samples sample-index)
     :position position
     :duration (:duration melody)
     :upcoming-note upcoming-note
     :current-pitch (* initial-transposition (:pitch upcoming-note))
     :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
     :scale scale
     :volume 0
     :history-active false
     :drone (< (Math/random) drone-frac)
     :alive true ; When we die, we'll get filtered out of the list of players
     :dying false
     :color (color/hsl (mod (* index 11) 360) 60 70)}))

(defn tick [player dt velocity key-transposition]
  (let [scaled-velocity (* velocity (:scale player))]
    (-> player
        (update-position dt scaled-velocity)
        (update-dying scaled-velocity)
        (update-volume dt velocity)
        (update-alive)
        (update-played-note key-transposition)
        (update-history))))
