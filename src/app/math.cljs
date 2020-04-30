(ns app.math)

(defonce tau (* js/Math.PI 2))
(defonce rad (/ js/Math.PI 180))

(defonce pow js/Math.pow)
(defonce sqrt js/Math.sqrt)
(defonce round js/Math.round)
(defonce ceil js/Math.ceil)
(defonce floor js/Math.floor)
(defonce log2 js/Math.log2)
(defonce sin js/Math.sin)

(defn to-precision [i p]
  (let [factor (pow 10 p)]
    (-> i
        (* factor)
        (js/Math.round)
        (/ factor))))

(defn to-fixed [i p]
  (.toFixed i p))

(defn clip
  ([input]
   (clip input 0 1))
  ([input out-min out-max]
   (min (max input out-min) out-max)))

(defn scale [input in-min in-max out-min out-max]
  (if (<= in-max in-min)
      out-min
    (-> input
        (- in-min)
        (/ (- in-max in-min))
        (* (- out-max out-min))
        (+ out-min))))

(defn random [out-min out-max]
  (scale (rand) 0 1 out-min out-max))

(defn scaled-sin [i out-min out-max]
  (scale (sin (* i tau)) -1 1 out-min out-max))
