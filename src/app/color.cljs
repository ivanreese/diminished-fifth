(ns app.color
  (:require [app.math :as math]))

(defn rgb [r g b]
  (str "rgb(" r "," g "," b ")"))

(defn hsl [h s l]
  (str "hsl(" h "," s "%," l "%)"))

(defn rgbn [r g b]
  (rgb (* r 255) (* g 255) (* b 255)))

(defn random-color []
  (let [color #(int (math/random 0 256))]
    (rgb (color) (color) (color))))

(defn random-hue [sat light]
  (hsl (int (math/random 0 360)) sat light))
