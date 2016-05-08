(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn soon [fn]
  (js/window.setTimeout fn 30))

(defn log [s]
  (js/console.log (clj->js s)))

(defn ajax-channel [url]
  (let [ch (chan)]
    (GET url
         {:handler #(go (>! ch %) (close! ch))})
    ch))

(defn- initialize! [window]
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))
          sample-paths (map #(str "/samples/" %) (get manifest "samples"))
          melody-paths (map #(str "/melodies/" %) (get manifest "melodies"))
          melodies (<! (async/into [] (async/merge (map ajax-channel melody-paths))))]
      (log sample-paths)
      (log melodies))))

; (defonce initialized (do (initialize! js/window) true))
(soon #(do (js/console.clear) (soon initialize!)))
