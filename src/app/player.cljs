(ns app.player
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.history :as history]
            [app.math :as math]
            [app.state :refer [state melodies samples]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.02)
(def transpose-on-repeat 2)
(def initial-transposition 0.5)
(def min-transposition (/ initial-transposition 8))
(def max-transposition (* initial-transposition 8))
(def min-velocity (/ 1 32))
(def max-velocity 16)
(def drone-frac 0.2)

;; ASSETS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn get-note-at-index [melody index]
  (nth (:notes melody) index))

(defn get-player-melody [player]
  (nth @melodies (:melody-index player)))

(defn get-melody-at-index [index]
  (nth @melodies index))

(defn get-upcoming-note [player]
  (get-note-at-index (get-player-melody player)
                     (:upcoming-note player)))

;; MAKE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn get-duration [reference-player]
  (:duration (get-player-melody reference-player)))

(defn determine-starting-note [melody-index player-position]
  (let [notes (:notes (get-melody-at-index melody-index))
        upcoming-note-index (:index (first (filter #(>= (:position %) player-position) notes)))]
    (or upcoming-note-index 0)))


;; UPDATES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn update-position [player dt velocity]
  (let [velocity (* velocity dt (:scale player))
        position (+ (:position player) velocity)
        raw-position (+ (:raw-position player) velocity)]
    (-> player
      ; (history/add-history-player :position raw-position 6)
      (assoc :position position)
      (assoc :raw-position raw-position))))

(defn update-dying [player dt velocity]
  (assoc player :dying
         (or (:dying player)
            ;  (< (* velocity (:scale player)) min-velocity)
             (> (* velocity (:scale player)) max-velocity)
             (<= (:transposition player) min-transposition)
             (>= (:transposition player) max-transposition))))

(defn update-volume [player dt velocity]
  (let [volume (math/clip
                (if (:dying player)
                  (- (:volume player)
                     (* dt (+ 0.5 (/ velocity 2)) fade-rate))
                  (+ (:volume player)
                     (* dt (+ 0.5 (/ velocity 2)) fade-rate))))]
    (-> player
      ; (history/add-history-player :volume volume 30)
      (assoc :volume volume))))

(defn update-alive [player]
  (assoc player :alive
         (or (not (:dying player))
             (> (:volume player) 0))))

(defn update-history [player]
  (when (:history-active player)
    (history/add-history-prop player :current-pitch 1))
  player)


; PLAY NOTE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn update-upcoming-note [player]
  (let [melody (get-player-melody player)
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
  (let [note (get-upcoming-note player)
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


(defn make [sync-position index velocity] ;; What stops the position from being WAY too high?
  (let [melody-index (mod index (count @melodies))
        sample-index (mod index (count @samples))
        position (mod sync-position (:duration (get-melody-at-index melody-index)))
        upcoming-note (determine-starting-note melody-index position)]
    ; (history/init-history index :upcoming-note)
    (history/init-history index :current-pitch)
    ; (history/init-history index :position)
    ; (history/init-history index :volume)
    {:type :player
     :index index
     :melody-index melody-index
     :sample (nth @samples sample-index)
     :position position ; The current time we're at in the pattern, in ms
     :raw-position position
     :upcoming-note upcoming-note
     :current-pitch (* initial-transposition (:pitch upcoming-note))
     :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
     :scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
     :volume (if (zero? index) 1 0)
     :history-active false
     :drone (< (Math/random) drone-frac)
     :alive true ; When we die, we'll get filtered out of the list of players
     :dying false
     :color (color/hsl (mod (* index 11) 360) 60 70)}))

(defn tick [player dt velocity key-transposition]
  (-> player
      (update-position dt velocity)
      (update-dying dt velocity)
      (update-volume dt velocity)
      (update-alive)
      (update-played-note key-transposition)
      (update-history)
      (history/trim-history)))

(defn rescale [player factor]
  (update player :scale * factor))
