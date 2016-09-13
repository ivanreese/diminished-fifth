(ns app.history
  (:require [app.state :refer [state history history-max history-min]]))

;; we expect the 'player' to have props for :index and :alive


(defn trim-history [player]
  (when-not (:alive player)
    (swap! history dissoc (:index player)))
  player)

(defn add-history [id key value skipN]
  (when (zero? (mod (get-in @state [:engine :count]) skipN)) ; Only add history once every skipN ticks
    (let [value (or value 0)
          arr (get-in @history [id key])
          slot (alength arr)
          prev-slot (- slot 1)
          prev-value (when (>= prev-slot 0) (aget arr prev-slot))
          prev-prev-slot (- prev-slot 1)
          prev-prev-value (when (>= prev-prev-slot 0) (aget arr prev-prev-slot))]
      (aset arr slot value)
      
      ; This nils-out all consecutive redundant values, preserving the leading and trailing values on an edge
      (when (and (= value prev-value)
                 (>= prev-prev-slot 0)
                 (or (nil? prev-prev-value)
                     (= prev-prev-value value)))
        (aset arr prev-slot nil))
      
      ; (if (nil? (get-in @history-min [id key]))
      ;   (swap! history-min assoc-in [id key] value)
      (swap! history-min update-in [id key] min value)
      ; (if (nil? (get-in @history-max [id key]))
      ;   (swap! history-max assoc-in [id key] value)
      (swap! history-max update-in [id key] max value)))
  id)


(defn add-history-player [player key val skipN]
  (add-history (:index player) key val skipN)
  player) ;; pass through

(defn add-history-prop [player key skipN]
  (add-history-player player key (get player key) skipN))


(defn init-history [id key]
  (swap! history assoc-in [id key] #js [])
  (swap! history-min assoc-in [id key] Infinity)
  (swap! history-max assoc-in [id key] -Infinity))
