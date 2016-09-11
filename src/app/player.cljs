(ns app.player
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.math :as math]
            [app.state :refer [state melodies samples history history-max history-min]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(def fade-rate 0.02)
(def transpose-on-repeat 2)
(def initial-transposition 1)
(def min-transposition (/ initial-transposition 8))
(def max-transposition (* initial-transposition 8))
(def min-velocity (/ 1 32))
(def max-velocity 16)
(def vol 0.1)

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


;; HISTORY ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn trim-history [player]
  (when-not (:alive player)
    (swap! history dissoc (:index player)))
  player)

(defn add-history [player key value skip]
  (when (zero? (mod (get-in @state [:engine :count]) skip))
    (let [value (or value 0)]
      (swap! history update-in [(:index player) key] conj value)
      (if (nil? (get-in @history-min [(:index player) key]))
        (swap! history-min assoc-in [(:index player) key] value)
        (swap! history-min update-in [(:index player) key] min value))
      (if (nil? (get-in @history-max [(:index player) key]))
        (swap! history-max assoc-in [(:index player) key] value)
        (swap! history-max update-in [(:index player) key] max value))))
  player)

(defn add-history-prop [player key skip]
  (add-history player key (key player) skip))

(defn init-history [index key]
  (swap! history assoc-in [index key] [])
  (swap! history-min assoc-in [index key] Infinity)
  (swap! history-max assoc-in [index key] -Infinity))

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
      (add-history :position raw-position 6)
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
      ; (add-history :volume volume 30)
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

(defn play-note! [player note pitch]
  (if (> 32 pitch (/ 1 8))
    (audio/play (:sample player)
                {:pos (- (:position player) (:position note))
                 :pitch pitch
                 :volume (* vol (:volume player) (/ (:volume note) (:transposition player)))}))
  player)

(defn update-played-note [player key-transposition]
  (let [note (get-upcoming-note player)
        player-pos (:position player)
        note-pos (:position note)
        pitch (* (:pitch note) (:transposition player) key-transposition)]
    (if (< player-pos note-pos)
      player
      (-> player
        (assoc :current-pitch pitch)
        (play-note! note pitch)
        (update-upcoming-note)))))


;; PUBLIC ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make [reference-player index velocity]
  (let [melody-index (mod index (count @melodies))
        sample-index (mod index (count @samples))
        position (get-sync-position reference-player)
        upcoming-note (determine-starting-note melody-index position)]
    ; (init-history index :upcoming-note)
    (init-history index :current-pitch)
    (init-history index :position)
    (init-history index :volume)
    {:index index
     :melody-index melody-index
     :sample (nth @samples sample-index)
     :position position ; The current time we're at in the pattern, in ms
     :raw-position position
     :upcoming-note upcoming-note
     :current-pitch (* initial-transposition (:pitch upcoming-note))
     :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
     :scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
     :volume (if (zero? index) 1 0)
     :alive true ; When we die, we'll get filtered out of the list of players
     :dying false
     :color (color/hsl (mod (* index 11) 360) 45 70)}))

(defn tick [player dt velocity key-transposition]
  (-> player
      (update-position dt velocity)
      (update-dying dt velocity)
      (update-volume dt velocity)
      (update-alive)
      (update-played-note key-transposition)
      (add-history-prop :current-pitch 2)
      (trim-history)))

(defn rescale [player factor]
  (update player :scale * factor))
