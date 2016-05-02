(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]))

(defonce state (atom nil))

(defn log [s]
  (.log js/console (clj->js s)))

(defn- saveAndLog [v]
  (do
   (swap! state merge v)
   (log @state)))

(defn- loadManifest []
  (GET "/manifest.json"
       {:handler saveAndLog}))

(defn- initialize! [window]
  (loadManifest))

(defonce initialized (do (initialize! js/window) true))
