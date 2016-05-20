(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.canvas :as canvas]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.state :refer [state melodies samples callback context]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn play []
  (swap! state engine/start))

(defn tick [dt]
   (swap! state orchestra/tick dt (get-in @state [:engine :time])))

(defn pause []
  (swap! state engine/stop))

(defn restart []
  (reset! state {:engine {:time 0}})
  (swap! state orchestra/init (get-in @state [:engine :time]))
  (play))

(defn resize [& args]
  (canvas/resize! @context
                  (js/window.-innerWidth)
                  (js/window.-innerHeight)))

(defn init []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (reset! context (canvas/create!))
      (js/window.addEventListener "resize" resize)
      (restart))))

(defonce initialized (do (init) true))

(reset! callback tick)
