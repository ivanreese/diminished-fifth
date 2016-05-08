(ns ^:figwheel-always app.app
  (:require [app.manifest :refer [loadManifest!]]
            [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close! sliding-buffer put! alts!]])
  (:require-macros [cljs.core.async.macros :as m :refer [go alt!]]))

(defonce state (atom nil))

(defn log [s]
  (js/console.log (clj->js s)))

(defn- saveAndLog [v]
  (do
   (swap! state merge v)
   (log @state)))

(defn- initialize! [window]
  (go
    (-> (loadManifest!)
        (<!)
        (log))))

; (defonce initialized (do (initialize! js/window) true))
(js/console.clear)
(def initialized (do (initialize! js/window) true))
