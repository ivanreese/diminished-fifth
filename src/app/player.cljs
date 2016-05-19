(ns app.player
  (:require [app.audio :as audio]
            [app.math :as math]
            [app.state :refer [state melodies samples]]
            [app.util :refer [log]]))
            

(def fade-rate 0.15)
; Starting playback pitch (recommended: reciprocal of transpos-on-repeat)
(def initial-transposition 1)
; After the clip is transposed too low or too high, we don't really want to hear it anymore and it will be killed off
(def min-transposition (/ initial-transposition 8))
(def max-transposition (* initial-transposition 8))
; Transpose the pitch of a player by this much
(def transpose-amount 2)
; After the melody is played too quickly or slowly, we don't really want to hear it anymore and it will be killed off
(def min-velocity (/ 1 32))
(def max-velocity 16)

(defn get-note-at-index [melody index]
  (nth (:notes melody) index))

(defn get-player-sample [player]
  (nth @samples (:sample-index player)))

(defn get-player-melody [player]
  (nth @melodies (:melody-index player)))

(defn get-next-note [player]
  (get-note-at-index (get-player-melody player)
                     (:next-note player)))

(defn determine-starting-note [player]
  (let [notes (:notes (get-player-melody player))
        player-position (:position player)
        next-note-index (:index (first (filter #(>= (:position %) player-position) notes)))]
    (assoc player :next-note (or next-note-index 0))))

(defn update-position [player dt velocity]
  (let [position (+ (:position player)
                    (* velocity dt (:scale player)))]
    (assoc player :position position)))

(defn update-dying [player dt velocity]
  (assoc player :dying
         (or (:dying player)
             (< (* velocity (:scale player)) min-velocity)
             (> (* velocity (:scale player)) max-velocity)
             (<= (:transposition player) min-transposition)
             (>= (:transposition player) max-transposition))))

(defn update-volume [player dt]
  (assoc player :volume
         (math/clip
           (if (:dying player)
             (- (:volume player)
                (* dt fade-rate))
             (+ (:volume player)
                (* dt fade-rate))))))

(defn update-alive [player]
  (assoc player :alive
         (or (not (:dying player))
             (> (:volume player) 0))))

(defn update-next-note [player]
  (let [melody (get-player-melody player)
        notes (:notes melody)
        duration (:duration melody)
        next-note (inc (:next-note player))]
    (if (< next-note (count notes))
      (assoc player :next-note next-note)
      (-> player
        (assoc :next-note 0)
        (update :position - duration)
        (update :transposition * transpose-amount)))))


(defn update-played-note [player key-transposition]
  (let [note (get-next-note player)
        player-pos (:position player)
        note-pos (:position note)]
    (if (>= player-pos note-pos)
      (do
        (audio/play (get-player-sample player)
                    {:pos (- player-pos note-pos)
                     :pitch (* (:pitch note) (:transposition player) key-transposition)
                     :volume (* (:volume player) (/ (:volume note) (:transposition player)))})
        (update-next-note player))
      player)))


;; PUBLIC

(defn make [index sync-position]
  (let [melody-index (mod index (count @melodies))
        sample-index (mod index (count @samples))
        position (mod sync-position (:duration (nth @melodies melody-index)))]
    (-> {:index index
         :melody-index melody-index
         :sample-index sample-index
         :position position ; The current time we're at in the pattern, in ms
         :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
         :scale 1 ; Adjusted when the Orchestra rescales. Applied to incoming velocity values
         :volume (if (zero? index) 1 0)
         :alive true ; If we're not alive, we won't be updated, but we will be saved and restarted during a future spawn
         :dying false}
        (determine-starting-note))))

(def p (first (:orchestra/players @state)))

(defn tick [player dt velocity key-transposition]
  (if-not (:alive player)
          player
          (-> player
              (update-position dt velocity)
              (update-dying dt velocity)
              (update-volume dt)
              (update-alive)
              (update-played-note key-transposition))))

(defn rescale [player factor]
  (update player :scale * factor))

; This fancy nonsense is necessary because position might be less than 0
(defn get-sync-position [player]
  (let [duration (:duration (get-player-melody player))]
    (/ (mod (+ (:position player) duration)
            duration)
       (:scale player))))
