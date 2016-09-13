(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.player :as player]
            [app.state :refer [state history history-min history-max]]))

(def dpi 2)
(def pad (- (* 8 dpi) 0.5))
(def top (+ 48 pad))
(def columns (atom 2))
(defonce scale (atom 1))

(defn mod-hash-color [val]
  (color/hsl (mod (+ 100 (hash val)) 360) 70 70))

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
    (cond
      (>= (:transposition player) player/max-transposition) (stack-text! stack (str "Dying: Xpos"))
      (> (* velocity (:scale player)) player/max-velocity) (stack-text! stack (str "Dying: Vel"))
      :else (stack-text! stack (str "Dying")))
    stack))

(defn draw-history [ctx subject-key base-x base-y width height max-history]
  (doseq [prop-tuple (get @history subject-key)
          :let [prop-key (nth prop-tuple 0)
                values (nth prop-tuple 1)
                n-values (alength values)
                min-v (get-in @history-min [subject-key prop-key])
                max-v (get-in @history-max [subject-key prop-key])
                v-range (- max-v min-v)
                n-values' (- n-values 1)]]
                ; i (volatile! 1)
    (when (> n-values 1)
      (canvas/beginPath! ctx)
      (canvas/strokeStyle! ctx (mod-hash-color (name prop-key)))
      (canvas/moveTo! ctx
                      base-x
                      (+ base-y (* height (- 1 (/ (- (nth values 0) min-v) v-range)))))
      (loop [i 1 last -1]
        (let [v (aget values i)]
          (when-not (nil? v)
            (canvas/lineTo! ctx
                            (+ base-x (* width (/ i n-values')))
                            (+ base-y (* height (- 1 (/ (- v min-v) v-range))))))
          (when (< i n-values')
            (recur (inc i) v))))
      ; (while (< @i n-values)
      ;        (canvas/lineTo! ctx
      ;                        (+ base-x (* width (/ @i n-values')))
      ;                        (+ base-y (* height (- 1 (/ (- (aget values @i) min-v) v-range)))))
      ;        (vswap! i inc))
      (canvas/stroke! ctx)))
  ctx)

(defn draw-player [state context player index player-count width height]
  (let [gutter (* 3 dpi pad)
        ygutter (* 1 dpi pad @scale @columns)
        totalSpaceForGutters (* gutter (- @columns 1))
        w (/ (- width totalSpaceForGutters) @columns)
        h (/ (- height top ygutter) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        rowIndex (mod index @columns)
        x (+ pad (* rowIndex (+ gutter w)))
        y (+ top pad (/ ygutter 2) (* (int (/ index @columns)) h) h)
        textTop (+ pad (* 2 dpi @scale))
        opacity (min 1 (* 1 (:volume player)))
        c (:color player)
        playerName (str (:index player) " " (get-name player))
        textHeight (* 16 dpi @scale)]
    (-> context
      ; (stroke-box "#FFF" x (+ y ygutter) w (- h ygutter))
      (canvas/globalAlpha! opacity)
      (canvas/fillStyle! c)
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! playerName x (+ y h))
      (begin-stack! (+ x pad (canvas/textWidth context playerName))
                    (+ y h)
                    (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Vol " (math/to-fixed (:volume player) 1)))
      (stack-text! (str "Note " (:upcoming-note player)))
      (stack-text! (str "Scale " (:scale player)))
      (stack-text! (str "Xpos " (:transposition player)))
      (stack-text! (str "Pos " (math/to-fixed (:position player) 2)))
      (stack-fillStyle! (mod-hash-color "current-pitch"))
      (stack-text! (str "Pitch " (math/to-fixed (:current-pitch player) 2)))
      (stack-fillStyle! (color/hsl 0 70 70))
      (draw-dying! player (get-in state [:orchestra :velocity]))
      (end-stack!)
      (draw-history (:index player) x (+ y ygutter) w (- h textHeight ygutter) 1000))))

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
  (let [x pad
        y top
        width (- (:width state) (* 2 pad))
        height (/ (- (:height state) (* 2 pad)) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        textHeight (* 16 dpi @scale)]
    (-> context
      (canvas/globalAlpha! 1)
      ; (stroke-box "#FFF" x top width height)
      (canvas/fillStyle! "#FFF")
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! "Orchestra" x (+ y height))
      (begin-stack! (+ x pad (canvas/textWidth context "Orchestra")) (+ y height) (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Time " (math/to-fixed (get-in state [:engine :time]) 2)))
      (stack-text! (str "Count " (get-in state [:engine :count])))
      (stack-text! (str "Xpos " (math/to-fixed (get-in state [:orchestra :transposition]) 2)))
      (stack-fillStyle! (mod-hash-color "velocity"))
      (stack-text! (str "Vel " (math/to-fixed (get-in state [:orchestra :velocity]) 4)))
      (end-stack!)
      (draw-history :orchestra pad pad width (- (+ height top) pad textHeight) 12000))))

(defn resize! [w h]
  (reset! scale (/ h 1000 dpi))
  (reset! columns (max 1 (quot w 1280))))

(defn render! [state context]
  (canvas/clear! context)
  (render-players state context)
  (render-orchestra state context))
