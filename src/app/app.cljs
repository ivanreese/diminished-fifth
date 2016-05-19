(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.state :refer [state melodies samples]]
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
  (reset! state {})
  (swap! state engine/init tick)
  (swap! state orchestra/init (get-in @state [:engine :time]))
  (play))

(defn init []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (restart))))

(defonce initialized (do (init) true))
