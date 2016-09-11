(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.player :as player]
            [app.state :refer [state history history-min history-max]]))

(def dpi 2)
(def pad (- (* 4 dpi) 0.5))
(def top (+ 72 pad))
(def columns (atom 2))
(defonce scale (atom 1))

(defn get-name [player]
  (-> player
      :sample
      :name
      (clojure.string/replace "samples/" "")
      (clojure.string/replace ".mp3" "")))

(defn stroke-box [ctx c x y w h]
  (-> ctx
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/rect! x y w h)
    (canvas/stroke!)))

(defn stroke-left [ctx c x y h]
  (-> ctx
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/moveTo! x y)
    (canvas/lineTo! x (+ y h))
    (canvas/stroke!)))

(defn begin-stack! [ctx x y f]
  (canvas/font! ctx f)
  [ctx x y])

(defn stack-text! [stack text]
  (let [ctx (nth stack 0)
        x (nth stack 1)
        y (nth stack 2)
        w (canvas/textWidth ctx text)]
    (canvas/fillText! ctx text x y)
    (assoc stack 1 (+ x 20 (* w)))))

(defn stack-fillStyle! [stack style]
  (canvas/fillStyle! (nth stack 0) style)
  stack)

(defn end-stack! [stack]
  (first stack))

(defn draw-dying! [stack player velocity]
  (if (:dying player)
    (let [stack (stack-text! stack (str "Dying"))]
      (cond
        (>= (:transposition player) player/max-transposition) (stack-text! stack (str "max-transposition"))
        (> (* velocity (:scale player)) player/max-velocity) (stack-text! stack (str "max-velocity"))
        :else stack))
    stack))
  
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
      (canvas/strokeStyle! ctx (color/hsl (mod (hash (name prop-key)) 360) 70 70))
      (canvas/moveTo! ctx
                      base-x
                      (+ base-y (* height (- 1 (/ (- (nth values 0) min-v) v-range)))))
      (while (< @i n-values)
             (canvas/lineTo! ctx
                             (+ base-x (* width (/ @i (- n-values 1))))
                             (+ base-y (* height (- 1 (/ (- (nth values @i) min-v) v-range)))))
             (swap! i inc))
      (canvas/stroke! ctx)))
  ctx)

(defn draw-player [state context player index player-count width height]
  (let [w (/ width @columns)
        h (/ (- height top) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        rowIndex (mod index @columns)
        x (+ pad (* rowIndex w))
        y (+ top pad (* (int (/ index @columns)) h) h)
        textTop (+ pad (* 12 dpi @scale))
        opacity (min 1 (* 1 (:volume player)))
        c (:color player)
        playerName (str (:index player) " " (get-name player))
        textHeight (+ pad (* 20 dpi @scale))]
    (when (> rowIndex 0)
      (stroke-left context c x y h))
    (-> context
      (canvas/globalAlpha! opacity)
      (canvas/fillStyle! c)
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! playerName (+ x pad) (+ y textTop))
      (begin-stack! (+ x pad 40 (canvas/textWidth context playerName))
                    (+ y textTop)
                    (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Volume " (math/to-fixed (:volume player) 2)))
      (stack-text! (str "Upcoming Note " (:upcoming-note player)))
      (stack-text! (str "Scale " (:scale player)))
      (stack-text! (str "Transposition " (:transposition player)))
      (stack-fillStyle! (color/hsl (mod (hash "position") 360) 70 70))
      (stack-text! (str "Position " (math/to-fixed (:position player) 2)))
      (stack-fillStyle! (color/hsl (mod (hash "current-pitch") 360) 70 70))
      (stack-text! (str "Current Pitch " (math/to-fixed (:current-pitch player) 3)))
      (stack-fillStyle! (color/hsl 0 70 70))
      (draw-dying! player (get-in state [:orchestra :velocity]))
      (end-stack!)
      (draw-history (:index player) x (+ y textHeight) w (- h textHeight pad) 1000))))

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
  (let [y top
        width (- (:width state) (* 2 pad))
        height (/ (- (:height state) (* 2 pad)) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        textHeight (+ pad (* 12 dpi @scale))]
    (-> context
      (canvas/globalAlpha! 1)
      ; (stroke-box "#FFF" pad pad width height)
      (canvas/fillStyle! "#FFF")
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! "Orchestra" (* 2 pad) y)
      (begin-stack! (+ pad 40 (canvas/textWidth context "Orchestra")) y (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Time " (math/to-fixed (get-in state [:engine :time]) 2)))
      (stack-text! (str "Count " (get-in state [:engine :count])))
      (stack-text! (str "Transposition " (math/to-fixed (get-in state [:orchestra :transposition]) 4)))
      (stack-fillStyle! (color/hsl (mod (hash "velocity") 360) 70 70))
      (stack-text! (str "Velocity " (math/to-fixed (get-in state [:orchestra :velocity]) 4)))
      (end-stack!)
      (draw-history :orchestra (+ (* 120 dpi @scale) pad) (+ y textHeight) (- width (* 120 dpi @scale)) (- height textHeight pad) 12000))))

(defn resize! [w h]
  (reset! scale (/ h 1000 dpi)))
  ; (reset! columns (max 1 (quot w 600))))

(defn render! [state context]
  (canvas/clear! context)
  (render-players state context)
  (render-orchestra state context))
