(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.player :as player]
            [app.orchestra :as orchestra]
            [app.state :refer [history history-min history-max]]
            [app.util :refer [snoop-logg]]))

(def dpi 2)
(def pad (- (* 8 dpi) 0.5))
(def top (+ 48 pad))
(def columns (atom 1))
(defonce scale (atom 1))
(defonce slow-context (canvas/create!))
(defonce fast-context (canvas/create!))

(defn mod-hash-color [val]
  (color/hsl (mod (+ 100 (hash val)) 360) 70 70))

(defn get-name [player]
  (-> player
      :sample
      :name
      (clojure.string/replace "samples/" "")
      (clojure.string/replace ".mp3" "")))

(defn stroke-box [context c x y w h]
  (-> context
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/rect! x y w h)
    (canvas/stroke!)))

(defn stroke-left [context c x y h]
  (-> context
    (canvas/beginPath!)
    (canvas/strokeStyle! c)
    (canvas/moveTo! x y)
    (canvas/lineTo! x (+ y h))
    (canvas/stroke!)))

(defn begin-stack! [context x y f]
  (canvas/font! context f)
  [context x y])

(defn stack-text! [stack text]
  (let [context (nth stack 0)
        x (nth stack 1)
        y (nth stack 2)
        w (canvas/textWidth context text)]
    (canvas/fillText! context text x y)
    (assoc stack 1 (+ x 20 (* w)))))

(defn stack-fillStyle! [stack style]
  (canvas/fillStyle! (nth stack 0) style)
  stack)

(defn end-stack! [stack]
  (first stack))

(defn draw-dying! [stack player velocity]
  (if (:dying player)
    (cond
      (>= (:transposition player) player/max-transposition)
      (stack-text! stack (str "Dying: Xpos"))
      
      (> velocity player/max-death-velocity)
      (stack-text! stack (str "Dying: Vel"))
      
      :else
      (stack-text! stack (str "Dying")))
    stack))

(defn draw-ahead! [stack player]
  (if (:ahead player)
    (stack-text! stack (str "Ahead"))
    stack))

(defn draw-pos [context player base-x base-y w h duration]
  (let [pos (:position player)]
    (-> context
      (canvas/beginPath!)
      (canvas/arc! (+ base-x (- w (* (/ w duration) pos))) (+ base-y -8 (/ h 2)) (+ 4 (* (- h 4) (Math/pow (/ pos duration) 8))) 0 tau true)
      (canvas/fill!))))

(defn draw-history [context subject-key base-x base-y width height max-history color]
  (doseq [prop-tuple (get @history subject-key)
          :let [prop-key (nth prop-tuple 0)
                values (nth prop-tuple 1)
                n-values (alength values)
                min-v (get-in @history-min [subject-key prop-key])
                max-v (get-in @history-max [subject-key prop-key])
                v-range (max 0.0000001 (- max-v min-v)) ;; avoid a divide by 0 error
                n-values' (- n-values 1)]]
    (when (> n-values 1)
      (when color (canvas/strokeStyle! context (mod-hash-color (name prop-key))))
      (loop [i 0 draw-fn canvas/moveTo!]
        (let [v (aget values i)]
          (when-not (nil? v)
            (draw-fn context
                     (+ base-x (* width (/ i n-values')))
                     (+ base-y (* height (- 1 (/ (- v min-v) v-range))))))
          (when (< i n-values')
            (recur (inc i) canvas/lineTo!))))))
  context)

(defn draw-player [state context player index player-count width height]
  (let [gutter (* 3 dpi pad)
        ygutter (/ (* 20 dpi pad @scale @columns) player-count)
        totalSpaceForGutters (* gutter (- @columns 1))
        w (/ (- width totalSpaceForGutters) @columns)
        h (/ (- height top ygutter) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        rowIndex (mod index @columns)
        x (+ pad (* rowIndex (+ gutter w)))
        y (+ top pad (/ ygutter 2) (* (int (/ index @columns)) h) h)
        textTop (+ pad (* 2 dpi @scale))
        opacity (min 1 (* 3 (:volume player)))
        c (:color player)
        playerName (str (:index player) " " (get-name player))
        textHeight (* 16 dpi @scale)]
    (-> context
      ; (stroke-box "#FFF" x (+ y ygutter) w (- h ygutter))
      (canvas/globalAlpha! opacity)
      (canvas/fillStyle! c)
      (canvas/strokeStyle! c)
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! playerName x (+ y h))
      (begin-stack! (+ x pad (canvas/textWidth context playerName))
                    (+ y h)
                    (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Scale " (math/to-fixed (:scale player) 2)))
      (stack-text! (str "Vol " (math/to-fixed (:volume player) 1)))
      (stack-text! (str "Note " (:upcoming-note player)))
      (stack-text! (str "Xpos " (:transposition player)))
      (stack-text! (str "Pos " (math/to-fixed (:position player) 2)))
      (stack-text! (str "Pitch " (math/to-fixed (:current-pitch player) 2)))
      (draw-dying! player (get-in state [:orchestra :velocity]))
      (end-stack!)
      (canvas/beginPath!)
      (draw-history (:index player) x (+ y ygutter) w (- h textHeight ygutter) 1000 false)
      (canvas/stroke!))))

(defn draw-drummer [state context player index player-count width height]
  (let [gutter (* 3 dpi pad)
        ygutter (/ (* 20 dpi pad @scale @columns) player-count)
        totalSpaceForGutters (* gutter (- @columns 1))
        w (/ (- width totalSpaceForGutters) @columns)
        h (/ (- height top ygutter) (+ 1 (math/ceil (/ (count (:players state)) @columns))))
        rowIndex (mod index @columns)
        x (+ pad (* rowIndex (+ gutter w)))
        y (+ top pad (/ ygutter 2) (* (int (/ index @columns)) h) h)
        textTop (+ pad (* 2 dpi @scale))
        opacity (min 1 (* 3 (:volume player)))
        c (mod-hash-color (get-name player))
        playerName (str (:index player) " " (get-name player))
        textHeight (* 16 dpi @scale)]
    (-> context
      ; (stroke-box "#FFF" x (+ y ygutter) w (- h ygutter))
      (canvas/globalAlpha! opacity)
      (canvas/fillStyle! c)
      (canvas/strokeStyle! c)
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! playerName x (+ y h))
      (begin-stack! (+ x pad (canvas/textWidth context playerName))
                    (+ y h)
                    (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Scale " (math/to-fixed (:scale player) 2)))
      (stack-text! (str "Vol " (math/to-fixed (:volume player) 1)))
      (stack-text! (str "Pos " (math/to-fixed (:position player) 2)))
      (stack-text! (str "Duration " (math/to-fixed (:duration player) 2)))
      (draw-ahead! player)
      (draw-dying! player (get-in state [:orchestra :velocity]))
      (end-stack!)
      (draw-pos player x (+ y ygutter) w (- h textHeight ygutter) (:duration player)))))

(defn render-players [state context]
  (let [all-players (:players state)
        player-count (count all-players)
        w (- (:width state) (* 2 pad))
        h (- (:height state) (* 2 pad))]
    (loop [players all-players index 0]
      (when-not (empty? players)
        (let [player (first players)
              type (:type player)]
          (cond
            (= type :player)
            (draw-player state context player index player-count w h)
            
            (= type :drummer)
            (draw-drummer state context player index player-count w h))
          
          (recur (rest players) (inc index)))))))

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
      (canvas/strokeStyle! "#FFF")
      (canvas/font! (str (* 14 dpi @scale) "px sans-serif"))
      (canvas/fillText! "Orchestra" x (+ y height))
      (begin-stack! (+ x pad (canvas/textWidth context "Orchestra")) (+ y height) (str (* 12 dpi @scale) "px sans-serif"))
      (stack-text! (str "Time " (math/to-fixed (get-in state [:engine :time]) 2)))
      (stack-text! (str "Count " (get-in state [:engine :count])))
      (stack-text! (str "Xpos " (math/to-fixed (get-in state [:orchestra :transposition]) 2)))
      (stack-text! (str "Drums " (math/to-fixed (* 100 (get-in state [:orchestra :drum-frac])) 0) "%"))
      (stack-text! (str "Vel " (math/to-fixed (get-in state [:orchestra :velocity]) 4)))
      (end-stack!)
      (canvas/beginPath!)
      (draw-history :orchestra pad pad width (- (+ height top) pad textHeight) 12000 true)
      (canvas/stroke!))))

(defn resize! []
  (let [w (* dpi (.-innerWidth js/window))
        h (* dpi (.-innerHeight js/window))]
    (swap! app.state/state assoc :width w)
    (swap! app.state/state assoc :height h)
    (swap! app.state/state assoc :dpi dpi)
    (canvas/resize! slow-context w h)
    (canvas/resize! fast-context w h)
    (reset! scale (/ h 1000 dpi))
    (reset! columns (max 1 (quot w 960)))))

(defn render! []
  (let [state @app.state/state]
    (when (get-in state [:engine :running])
      (canvas/clear! fast-context)
      (render-players state fast-context)
      (render-orchestra state fast-context))))

(resize!)
(render!)
