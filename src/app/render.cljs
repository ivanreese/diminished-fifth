(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]))

(defn get-name [player]
  (-> player
      :sample
      :name
      (clojure.string/replace "/samples/" "")
      (clojure.string/replace ".mp3" "")))

(defn stroke-box [context c x y w h]
  (-> context
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/rect! x y w h)
    (canvas/stroke!)))

(defn draw-player [context state player index player-count width height]
  (let [pad 6
        outer-w (/ width player-count)
        w (- outer-w (* 2 pad))
        h 400
        x (+ (* index outer-w) pad)
        y pad
        c (:color player)]
    (-> context
      (stroke-box c x y w h)
      (canvas/fillStyle! c)
      (canvas/font! "40px Futura")
      (canvas/fillText! (get-name player) (+ x 30) (+ y 60))
      (canvas/font! "32px Futura")
      (canvas/fillText! (str "Position " (math/to-precision (:position player) 2)) (+ x 30) (+ y 100))
      (canvas/fillText! (str "Next note " (:next-note player)) (+ x 30) (+ y 140))
      (canvas/fillText! (str "Volume " (math/to-precision (:volume player) 2)) (+ x 30) (+ y 180))
      (canvas/fillText! (str "Dying " (:dying player)) (+ x 30) (+ y 220))
      (canvas/fillText! (str "Index " (:index player)) (+ x 30) (+ y 260)))))

(defn render-players [context state]
  (let [all-players (:players state)
        player-count (count all-players)
        w (:width state)
        h (:height state)]
    (loop [players all-players index 0]
      (if (empty? players)
        state
        (do
          (draw-player context state (first players) index player-count w h)
          (recur (rest players) (inc index)))))))
  
(defn render! [state context]
  (-> context
      (canvas/clear!)
      (render-players state)))
