(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.state :refer [history]]))

(def orchestra-height 280)
(def player-height 225)
(def columns 3)

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

(defn begin-stack! [context x y f]
  (canvas/font! context f)
  [context x y])

(defn stack-text! [stack text]
  (let [c (nth stack 0)
        x (nth stack 1)
        y (nth stack 2)]
    (canvas/fillText! c text x y)
    (assoc stack 2 (+ y 30))))

(defn end-stack! [stack]
  (first stack))

(defn draw-dying! [context player x y]
  (if (:dying player)
    (canvas/fillText! context "Dying" x y)
    context))
  
(defn draw-seg [i v context base-x base-y width height min-v max-v max-history]
  (canvas/lineTo! context
                  (+ base-x (* width (/ (+ i 1) max-history)))
                  (+ base-y (* (math/scale v min-v max-v height 0)))))

(defn draw-history [context key base-x base-y width height max-history]
  (doseq [prop (get @history key)
          :let [name (name (first prop))
                values (second prop)
                min-v (reduce min values)
                max-v (reduce max values)
                v (first values)
                x base-x
                y (+ base-y (* (math/scale v min-v max-v height 0)))]]
    (-> context
        (canvas/beginPath!)
        (canvas/strokeStyle! (color/hsl (mod (hash name) 360) 50 50))
        (canvas/moveTo! x y))
    (dorun (map #(draw-seg %1 %2 context base-x base-y width height min-v max-v max-history) (range) (rest values)))
    (canvas/stroke! context))
  context)

(defn draw-player [context state player index player-count width height]
  (let [pad 5.5
        w (- (/ width columns) pad)
        h player-height
        x (+ pad (* (mod index columns) w))
        y (+ (* (int (/ index columns)) h) pad orchestra-height)
        opacity (min 1 (* 10 (:volume player)))
        c (:color player)]
    (-> context
        (canvas/globalAlpha! opacity)
        (stroke-box c x y w h)
        (canvas/fillStyle! c)
        (canvas/font! "36px Futura")
        (canvas/fillText! (get-name player) (+ x pad) (+ y 35))
        (begin-stack! (+ x pad) (+ y 65) "24px Futura")
        (stack-text! (str "Position " (math/to-precision (:position player) 2)))
        (stack-text! (str "Next note " (:next-note player)))
        (stack-text! (str "Volume " (math/to-precision (:volume player) 2)))
        (stack-text! (str "Index " (:index player)))
        (stack-text! (str "Scale " (:scale player)))
        (stack-text! (str "Transposition " (:transposition player)))
        (end-stack!)
        (draw-dying! player (+ x w -70) (+ 30 y))
        (draw-history (:index player) (+ 300 x) y (- w 300) h 5000))))

(defn render-players [context state]
  (let [all-players (:players state)
        player-count (count all-players)
        w (:width state)
        h (:height state)]
    (loop [players all-players index 0]
      (if (empty? players)
        context
        (do
          (draw-player context state (first players) index player-count w h)
          (recur (rest players) (inc index)))))))

(defn render-orchestra [context state]
  (let [width (:width state)]
    (-> context
      (canvas/globalAlpha! 1)
      (stroke-box "#FFF" 5.5 5.5 (- (:width state) 16.5) orchestra-height)
      (canvas/fillStyle! "#FFF")
      (canvas/font! "36px Futura")
      (canvas/fillText! "Orchestra" 11 40)
      (begin-stack! 11 65.5 "24px Futura")
      (stack-text! (str "Acceleration " (math/to-precision (get-in state [:orchestra :acceleration :value]) 4)))
      (stack-text! (str "Playback Rate " (math/to-precision (get-in state [:orchestra :playback-rate]) 4)))
      (stack-text! (str "Scaled Playback " (math/to-precision (* (get-in state [:orchestra :playback-rate]) (get-in state [:orchestra :scale])) 4)))
      (stack-text! (str "Scale " (math/to-precision (get-in state [:orchestra :scale]) 4)))
      (stack-text! (str "Transposition " (math/to-precision (get-in state [:orchestra :transposition]) 4)))
      (stack-text! (str "Wall Time " (math/to-precision (get-in state [:engine :wall-time]) 1)))
      (stack-text! (str "Time " (math/to-precision (get-in state [:engine :time]) 1)))
      (stack-text! (str "Count " (get-in state [:engine :count])))
      (end-stack!)
      (draw-history :orchestra 300.5 5.5 (- width 310.5) orchestra-height 20000))))

(defn render! [state context]
  (-> context
      (canvas/clear!)
      (render-players state)
      (render-orchestra state)))
