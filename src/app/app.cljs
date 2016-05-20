(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.canvas :as canvas]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.render :refer [render!]]
            [app.state :refer [state melodies samples callback context]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn play []
  (swap! state engine/start))

(defn pause []
  (swap! state engine/stop))

(defn tick [dt]
   (swap! state orchestra/tick dt (get-in @state [:engine :time]))
   (render! @state @context))

(defn resize [& args]
  (let [w (* 2 (.-innerWidth js/window))
        h (* 2 (.-innerHeight js/window))]
    (swap! state assoc :width w)
    (swap! state assoc :height h)
    (canvas/resize! @context w h)))

(defn restart []
  (reset! state {:engine {:time 0}})
  (resize)
  (swap! state orchestra/init (get-in @state [:engine :time]))
  (play))

(defn init []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (reset! context (canvas/create!))
      (js/window.addEventListener "resize" resize)
      (resize)
      (restart))))

(defonce initialized (do (init) true))

(reset! callback tick)
