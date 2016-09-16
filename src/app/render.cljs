(ns app.render
  (:require [app.canvas :as canvas]
            [app.color :as color]
            [app.math :as math :refer [tau]]
            [app.player :as player]
            [app.state :refer [history history-min history-max]]
            [app.util :refer [snoop-logg]]))

(def dpi 2)
(def pad (- (* 8 dpi) 0.5))
(def top (+ 48 pad))
(def columns (atom 1))
(defonce scale (atom 1))
(defonce inited (atom false))

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

(defn draw-history [ctx subject-key base-x base-y width height max-history color]
  (doseq [prop-tuple (get @history subject-key)
          :let [prop-key (nth prop-tuple 0)
                values (nth prop-tuple 1)
                n-values (alength values)
                min-v (get-in @history-min [subject-key prop-key])
                max-v (get-in @history-max [subject-key prop-key])
                v-range (max 0.0000001 (- max-v min-v)) ;; avoid a divide by 0 error
                n-values' (- n-values 1)]]
    (when (> n-values 1)
      (when color (canvas/strokeStyle! ctx (mod-hash-color (name prop-key))))
      (canvas/beginPath! ctx)
      (loop [i 0 draw-fn canvas/moveTo!]
        (let [v (aget values i)]
          (when-not (nil? v)
            (draw-fn ctx
                     (+ base-x (* width (/ i n-values')))
                     (+ base-y (* height (- 1 (/ (- v min-v) v-range))))))
          (when (< i n-values')
            (recur (inc i) canvas/lineTo!))))
      (canvas/stroke! ctx)))
  ctx)

(defn draw-sync-markers [ctx player base-x base-y width height]
  (canvas/beginPath! ctx)
  (let [xunit (/ width 28.8)]
    (loop [x (mod (/ (:position player) (:scale player)) 1.8)]
      (when (< x 28.8)
        (canvas/moveTo! ctx (+ base-x (* x xunit)) base-y)
        (canvas/lineTo! ctx (+ base-x (* x xunit)) (+ base-y height))
        (recur (+ x 1.8)))))
  (canvas/stroke! ctx)
  ctx)

(defn draw-melody [ctx player base-x base-y width height]
  (let [drone (:drone player)
        xstep (/ width 28.8)
        ystep (/ height 5)
        melody (:melody player)]
    (canvas/beginPath! ctx)
    (canvas/lineWidth! ctx 4)
    (loop [notes (:notes melody)]
      (when-not (empty? notes)
        (let [note (first notes)
              bottom (if drone (/ height 3) height)
              top (- height (* ystep (if drone 2 (:pitch note))))]
          (-> ctx
            ; (canvas/beginPath!)
            ; (canvas/arc! (+ base-x (* xstep (:position note))) (+ base-y (- height (* ystep (:pitch note)))) 2 0 tau false)
            ; (canvas/fill!)
            (canvas/moveTo! (+ base-x (* xstep (:position note))) (+ base-y bottom))
            (canvas/lineTo! (+ base-x (* xstep (:position note))) (+ base-y top)))
          (recur (rest notes)))))
    (canvas/stroke! ctx)
    (canvas/lineWidth! ctx 1)
    (let [x (+ base-x (* xstep (mod (:position player) (:duration melody))))]
      (-> ctx
        (canvas/beginPath!)
        (canvas/moveTo! x base-y)
        (canvas/lineTo! x (+ base-y height))
        (canvas/stroke!))))
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
      (draw-history (:index player) x (+ y ygutter) w (- h textHeight ygutter) 1000 false)
      (canvas/strokeStyle! "black")
      (draw-sync-markers player x (+ y ygutter) w (- h textHeight ygutter))
      (canvas/strokeStyle! c)
      (draw-melody player x (+ y ygutter) w (- h textHeight ygutter)))))

(defn draw-drum-pattern [ctx player base-x base-y width height]
  (let [xstep (/ width 28.8)
        ystep (/ height 5)]
    (let [x (+ base-x (* xstep (mod (:position player) 28.8)))]
      (-> ctx
        (canvas/beginPath!)
        (canvas/moveTo! x base-y)
        (canvas/lineTo! x (+ base-y height))
        (canvas/stroke!))))
  ctx)

(defn draw-drummer [state context player index player-count width height]
  (let [gutter (* 3 dpi pad)
        ygutter (* 1 dpi pad @scale @columns)
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
      (stack-text! (str "Pos " (math/to-fixed (:position player) 2)))
      (stack-text! (str "Duration " (math/to-fixed (:duration player) 2)))
      (draw-ahead! player)
      (draw-dying! player (get-in state [:orchestra :velocity]))
      (end-stack!)
      (draw-history (:index player) x (+ y ygutter) w (- h textHeight ygutter) 1000 false)
      (canvas/strokeStyle! "black")
      (draw-sync-markers player x (+ y ygutter) w (- h textHeight ygutter))
      (canvas/strokeStyle! c)
      (draw-drum-pattern player x (+ y ygutter) w (- h textHeight ygutter)))))

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
      (stack-text! (str "Vel " (math/to-fixed (get-in state [:orchestra :velocity]) 4)))
      (end-stack!)
      (draw-history :orchestra pad pad width (- (+ height top) pad textHeight) 12000 true))))

(defn resize! []
  (when @inited
    (let [context @app.state/text-context
          w (* dpi (.-innerWidth js/window))
          h (* dpi (.-innerHeight js/window))]
      (swap! app.state/state assoc :width w)
      (swap! app.state/state assoc :height h)
      (swap! app.state/state assoc :dpi dpi)
      (canvas/resize! context w h)
      (reset! scale (/ h 1000 dpi))
      (reset! columns (max 1 (quot w 960))))))

(defn render! []
  (when @inited
    (let [context @app.state/text-context
          state @app.state/state]
      (canvas/clear! context)
      (render-players state context)
      (render-orchestra state context))))

(defn init []
  (reset! app.state/text-context (canvas/create!))
  (reset! inited true))
