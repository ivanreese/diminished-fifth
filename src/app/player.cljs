(ns app.player
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.math :as math]
            [app.state :refer [melodies samples history]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.01)
(def transpose-on-repeat 2)
(def initial-transposition 1)
(def min-transposition (/ initial-transposition 8))
(def max-transposition (* initial-transposition 8))
(def min-velocity (/ 1 32))
(def max-velocity 16)


;; HISTORY ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn trim-history-prop [m k v]
  (assoc m k (if (> (count v) 10000) (drop-last 1 v) v)))

(defn trim-history-all-props [history]
  (reduce-kv trim-history-prop {} history))

(defn trim-history [player]
  (if (:alive player)
    (swap! history update (:index player) trim-history-all-props)
    (swap! history dissoc (:index player)))
  player)

(defn add-history [player key value]
  (swap! history update-in [(:index player) key] conj value)
  player)

(defn add-history-prop [player key]
  (add-history player key (key player)))

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


(defn get-sync-position [reference-player]
  (if (nil? reference-player)
    0
    (let [duration (:duration (get-player-melody reference-player))]
      (mod
       (/ (mod (+ (:position reference-player) duration)
               duration) ; This mod ensures that we aren't < 0
          (:scale reference-player))
       duration))))

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
      (add-history :position raw-position)
      (assoc :position position)
      (assoc :raw-position raw-position))))

(defn update-dying [player dt velocity]
  (assoc player :dying
         (or (:dying player)
             (< (* velocity (:scale player)) min-velocity)
             (> (* velocity (:scale player)) max-velocity)
             (<= (:transposition player) min-transposition)
             (>= (:transposition player) max-transposition))))

(defn update-volume [player dt velocity]
  (let [volume (math/clip
                (if (:dying player)
                  (- (:volume player)
                     (* dt velocity fade-rate))
                  (+ (:volume player)
                     (* dt velocity fade-rate))))]
    (-> player
      (add-history :volume volume)
      (assoc :volume volume))))

(defn update-alive [player]
  (assoc player :alive
         (or (not (:dying player))
             (> (:volume player) 0))))


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

(defn play-note! [player note key-transposition]
  (audio/play (:sample player)
              {:pos (- (:position player) (:position note))
               :pitch (* (:pitch note) (:transposition player) key-transposition)
               :volume (* (:volume player) (/ (:volume note) (:transposition player)))}))

(defn update-played-note [player key-transposition]
  (let [note (get-upcoming-note player)
        player-pos (:position player)
        note-pos (:position note)]
    (if (< player-pos note-pos)
      player
      (do
        (play-note! player note key-transposition)
        (update-upcoming-note player)))))


;; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make [reference-player index]
  (let [melody-index (mod index (count @melodies))
        sample-index (mod index (count @samples))
        position (get-sync-position reference-player)]
    {:index index
     :melody-index melody-index
     :sample (nth @samples sample-index)
     :position position ; The current time we're at in the pattern, in ms
     :raw-position position
     :upcoming-note (determine-starting-note melody-index position)
     :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
     :scale 1 ; Adjusted when the Orchestra rescales. Applied to incoming velocity values
     :volume (if (zero? index) 1 0)
     :alive true ; When we die, we'll get filtered out of the list of players
     :dying false
     :color (color/hsl (mod (* index 27) 360) 50 70)}))

(defn tick [player dt velocity key-transposition]
  (-> player
      (update-position dt velocity)
      (update-dying dt velocity)
      (update-volume dt velocity)
      (update-alive)
      (update-played-note key-transposition)
      (add-history-prop :upcoming-note)
      (trim-history)))

(defn rescale [player factor]
  (update player :scale * factor))
