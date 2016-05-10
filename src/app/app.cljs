(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.audio :refer [play]]
            [app.state :refer [state]]
            [app.time :as time]
            [app.util :refer [log]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- tick [dt]
  (log dt)
  (play (first (:samples @state)) (first (:notes (first (:melodies @state)))))
  (time/stop))

(defn- reloaded []
  (js/console.clear)
  (time/start tick))

(defn- initialize []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (swap! state assoc :melodies (<! (load-assets manifest "melodies" melody-loader)))
      (swap! state assoc :samples (<! (load-assets manifest "samples" sample-loader)))
      (swap! state assoc :app/loaded true)
      (reloaded))))

(defonce initialized (do (initialize) true))

(if (:app/loaded @state) (reloaded))
