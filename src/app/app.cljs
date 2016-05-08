(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close! sliding-buffer put! alts!]])
  (:require-macros [cljs.core.async.macros :as m :refer [go alt!]]))

(defn soon [fn]
  (js/window.setTimeout fn 30))

(defn log [s]
  (js/console.log (clj->js s)))

(defn ajax-channel [url]
  (let [ch (chan)]
    (GET url
         {:handler #(go (>! ch %))})
    ch))

(defn- initialize! [window]
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))
          melody-paths (map #(str "/melodies/" %) (get manifest "melodies"))
          samples (get manifest "samples")]
      (log (vector? (into [] (map ajax-channel melody-paths))))
      (log samples))))

; (defonce initialized (do (initialize! js/window) true))
(soon #(do (js/console.clear) (soon initialize!)))
