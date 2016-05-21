(ns app.phasor
  (:require [app.math :as math]))

(defn tick [phasor time]
  (assoc phasor :value (math/sin (/ time (:length phasor)))))

(defn make [value length]
  (let [p {:length length}]
    (tick p 0)))
