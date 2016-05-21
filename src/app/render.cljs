(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.player :as player]
            [app.state :refer [state history history-min history-max]]))

(def dpi 1)
(def pad (- (* 8 dpi) 0.5))
(def columns 3)
(def rows 7) ; Including the orchestra row

(defn get-name [player]
  (-> player
      :sample
      :name
      (clojure.string/replace "/samples/" "")
      (clojure.string/replace ".mp3" "")))

(defn stroke-box [ctx c x y w h]
  (-> ctx
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/rect! x y w h)
    (canvas/stroke!)))

(defn begin-stack! [ctx x y f]
  (canvas/font! ctx f)
  [ctx x y])

(defn stack-text! [stack text]
  (let [ctx (nth stack 0)
        x (nth stack 1)
        y (nth stack 2)]
    (canvas/fillText! ctx text x y)
    (assoc stack 2 (+ y (* dpi 15)))))

(defn stack-fillStyle! [stack style]
  (canvas/fillStyle! (nth stack 0) style)
  stack)

(defn end-stack! [stack]
  (first stack))

(defn draw-dying! [ctx player x y velocity]
  (when (:dying player)
    (canvas/fillText! ctx "Dying" x y)
    (when (>= (:transposition player) player/max-transposition)
      (canvas/fillText! ctx "max-transposition" (- x (* dpi 63)) (+ y (* dpi 15))))
    (when (> (* velocity (:scale player)) player/max-velocity)
      (canvas/fillText! ctx "max-velocity" (- x (* 15 dpi)) (+ y (* 30 dpi)))))
  ctx)
  
(defn draw-history [ctx subject-key base-x base-y width height max-history]
  (doseq [prop-tuple (get @history subject-key)
          :let [prop-key (nth prop-tuple 0)
                values (nth prop-tuple 1)
                n-values (count values)
                min-v (get-in @history-min [subject-key prop-key])
                max-v (get-in @history-max [subject-key prop-key])
                v-range (- max-v min-v)
                i (atom 1)]]
    (when (> n-values 1)
      (canvas/beginPath! ctx)
      (canvas/strokeStyle! ctx (color/hsl (mod (hash (name prop-key)) 360) 50 50))
      (canvas/moveTo! ctx
                      (+ base-x width)
                      (+ base-y (* height (- 1 (/ (- (nth values 0) min-v) v-range)))))
      (while (< @i n-values)
             (canvas/lineTo! ctx
                             (+ base-x (* width (- 1 (/ @i n-values))))
                             (+ base-y (* height (- 1 (/ (- (nth values @i) min-v) v-range)))))
             (swap! i inc))
      (canvas/stroke! ctx)))
  ctx)

(defn draw-player [state context player index player-count width height]
  (let [w (/ width columns)
        h (/ height (+ 1 (math/ceil (/ (count (:players state)) columns))))
        x (+ pad (* (mod index columns) w))
        y (+ pad (* (int (/ index columns)) h) h)
        opacity (min 1 (* 10 (:volume player)))
        c (:color player)]
    (-> context
      (canvas/globalAlpha! opacity)
      (stroke-box c x y w h)
      (canvas/fillStyle! c)
      (canvas/font! (str (* 18 dpi) "px Futura"))
      (canvas/fillText! (str (:index player) " " (get-name player)) (+ x pad) (+ y pad (* 16 dpi)))
      (begin-stack! (+ x pad) (+ y pad (* 32 dpi)) (str (* 12 dpi) "px Futura"))
      (stack-fillStyle! (color/hsl (mod (hash "position") 360) 50 50))
      (stack-text! (str "Position " (math/to-precision (:position player) 2)))
      (stack-fillStyle! (color/hsl (mod (hash "current-pitch") 360) 50 50))
      (stack-text! (str "Current Pitch " (math/to-precision (:current-pitch player) 3)))
      (stack-fillStyle! c)
      (stack-text! (str "Upcoming Note " (:upcoming-note player)))
      (stack-text! (str "Volume " (math/to-precision (:volume player) 2)))
      (stack-text! (str "Scale " (:scale player)))
      (stack-text! (str "Transposition " (:transposition player)))
      (end-stack!)
      (draw-dying! player (+ x w (* -36 dpi)) (+ (* 16 dpi) y) (get-in state [:orchestra :velocity]))
      (draw-history (:index player) (+ (* 150 dpi) x) y (- w (* 150 dpi)) h 1000))))

(defn render-players [state context]
  (let [all-players (:players state)
        player-count (count all-players)
        w (- (:width state) (* 2 pad))
        h (- (:height state) (* 2 pad))]
    (loop [players all-players index 0]
      (when-not (empty? players)
        (draw-player state context (first players) index player-count w h)
        (recur (rest players) (inc index))))))

(defn render-orchestra [state context]
  (let [width (- (:width state) (* 2 pad))
        height (/ (- (:height state) (* 2 pad)) (+ 1 (math/ceil (/ (count (:players state)) columns))))]
    (-> context
      (canvas/globalAlpha! 1)
      (stroke-box "#FFF" pad pad width height)
      (canvas/fillStyle! "#FFF")
      (canvas/font! (str (* 18 dpi) "px Futura"))
      (canvas/fillText! "Orchestra" (* 2 pad) (+ (* 2 pad) (* 16 dpi)))
      (begin-stack! (* 2 pad) (+ (* 2 pad) (* 32 dpi)) (str (* 12 dpi) "px Futura"))
      (stack-fillStyle! (color/hsl (mod (hash "velocity") 360) 50 50))
      (stack-text! (str "Velocity " (math/to-precision (get-in state [:orchestra :velocity]) 4)))
      (stack-fillStyle! (color/hsl (mod (hash "transposition") 360) 50 50))
      (stack-text! (str "Transposition " (math/to-precision (get-in state [:orchestra :transposition]) 4)))
      (stack-fillStyle! "#FFF")
      (stack-text! (str "Wall Time " (math/to-precision (get-in state [:engine :wall-time]) 2)))
      (stack-text! (str "Time " (math/to-precision (get-in state [:engine :time]) 2)))
      (stack-text! (str "Count " (get-in state [:engine :count])))
      (end-stack!)
      (draw-history :orchestra (+ (* 120 dpi) pad) pad (- width (* 120 dpi)) height 12000))))

(defn render! [state context]
  (canvas/clear! context)
  (render-players state context)
  (render-orchestra state context))
