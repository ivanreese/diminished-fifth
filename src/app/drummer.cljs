(ns app.drummer
  (:require [app.audio :as audio]
            [app.color :as color]
            [app.history :as history]
            [app.math :as math]
            [app.state :refer [state melodies samples]]
            [app.util :refer [log]]
            [cljs.pprint :refer [pprint]]))

(defn get-duration [reference-drummer]
  14.4)


(defn make [position index velocity]
  (let [sample-index (mod index (count @samples))]
    ; (history/init-history index :upcoming-note)
    (history/init-history index :current-pitch)
    (history/init-history index :position)
    (history/init-history index :volume)
    {:type :drummer
     :index index ; Used by history and renderer
    ;  :melody-index melody-index
     :sample (nth @samples sample-index)
     :position position ; The current time we're at in the pattern, in ms
     :raw-position position
    ;  :upcoming-note upcoming-note
    ;  :current-pitch (* initial-transposition (:pitch upcoming-note))
    ;  :transposition initial-transposition ; Adjusted every time the track repeats by transposeOnRepeat
     :scale (math/clip (math/pow 2 (math/round (math/log2 (/ 1 velocity)))) (/ 1 32) 8)
     :volume (if (zero? index) 1 0)
     :alive true ; When we die, we'll get filtered out of the list of players. Also used by history.
     :dying false
     :color (color/hsl (mod (* index 11) 360) 45 70)}))


; (defn tick [player dt velocity key-transposition]
;   (-> player
;       (update-position dt velocity)
;       (update-dying dt velocity)
;       (update-volume dt velocity)
;       (update-alive)
;       (update-played-note key-transposition)
;       (history/add-history-prop :current-pitch 2)
;       (history/trim-history)))

(defn rescale [player factor]
  (update player :scale * factor))
