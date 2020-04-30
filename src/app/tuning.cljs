(ns app.tuning
  (:require [app.math :as math]))

(defn pythagorean [pitch]
  (let [midi-note (math/round (* 12 (math/log2 pitch)))
        octave (math/floor (/ midi-note 12))
        pitch-class (mod midi-note 12)]
    (* (math/pow 2 octave)
       (case pitch-class
         0 1
         1 (/ 256 243)
         2 (/ 9 8)
         3 (/ 32 27)
         4 (/ 81 64)
         5 (/ 4 3)
         6 (/ 1024 729) ; diminished fifth, baby!
         7 (/ 3 2)
         8 (/ 128 81)
         9 (/ 27 16)
         10 (/ 16 9)
         11 (/ 243 128)))))
