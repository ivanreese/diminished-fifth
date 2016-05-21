(ns app.phasor
  (:require [app.math :as math]))

(defn tick [phasor time]
  (assoc phasor :value (math/scaled-sin (/ time (:length phasor))
                                        (:min phasor)
                                        (:max phasor))))

(defn make [value min max length]
  (let [p {:min min
           :max max
           :length length}]
    (tick p 0)))
