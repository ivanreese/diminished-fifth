(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.audio :as audio]
            [app.canvas :as canvas]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.render :refer [render!]]
            [app.state :refer [state melodies samples callback context history]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tick-once-mode (atom false))

(defn tick-once []
  (let [mode (not @tick-once-mode)
        btn (js/document.querySelector ".tick-once.button")]
    (reset! tick-once-mode mode)
    (if mode
      (.setAttribute btn "active" true)
      (.removeAttribute btn "active"))))

(defn play []
  (swap! state engine/start))

(defn pause []
  (swap! state engine/stop))

(defn tick [dt]
   (when @tick-once-mode (pause))
   (swap! state orchestra/tick dt (get-in @state [:engine :time]))
   (when (or (zero? (mod (get-in @state [:engine :count]) 3))
             @tick-once-mode)
     (render! @state @context)))

(defn resize [& args]
  (let [w (* 2 (.-innerWidth js/window))
        h (* 2 (.-innerHeight js/window))]
    (swap! state assoc :width w)
    (swap! state assoc :height h)
    (canvas/resize! @context w h)))

(defn restart []
  (reset! state {})
  (reset! history {})
  (resize)
  (swap! state engine/restart)
  (swap! state orchestra/init (get-in @state [:engine :time]))
  (render! @state @context))

(defn sound-check []
  (let [sample (nth @samples (int (rand (count @samples))))
        melody (nth @melodies (int (rand (count @melodies))))
        notes (:notes melody)
        note (nth notes (int (rand (count notes))))]
    (audio/play sample note)))

(defn setup-button [class callback]
  (.addEventListener (js/document.querySelector (str "." class))
                     "click"
                     callback))

(defn init []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (reset! context (canvas/create!))
      (js/window.addEventListener "resize" resize)
      (setup-button "play" play)
      (setup-button "pause" pause)
      (setup-button "restart" restart)
      (setup-button "tick-once" tick-once)
      (setup-button "sound-check" sound-check)
      (resize)
      (restart))))

(defonce initialized (do (init) true))

(reset! callback tick)
