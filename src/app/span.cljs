(ns app.span
  (:require [app.math :as math]))

(defn make [min max]
  [min max])

(defn within [i span]
  (and (> i (nth span 0))
       (< i (nth span 1))))

(defn clip [i span]
  (math/clip i (nth span 0) (nth span 1)))

(defn scale [i in out]
  (math/scale i (nth in 0) (nth in 1) (nth out 0) (nth out 1)))

(defn random [span]
  (math/random (nth span 0) (nth span 1)))
