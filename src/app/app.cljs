(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.audio :refer [play]]
            [app.canvas :as canvas]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.state :refer [state melodies samples]]
            [app.util :refer [log]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn restart []
  (orchestra/init))

(defn start []
  (engine/start)
  (orchestra/start))

(defn tick [dt]
  (orchestra/tick dt))

(defn stop []
  (engine/stop))

(defn reloaded []
  (stop))

(defn init []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (swap! state assoc :app/loaded true)
      (engine/init tick)
      (restart)
      (reloaded))))

(defonce initialized (do (init) true))

(if (:app/loaded @state) (reloaded))
