(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]))

(defonce state (atom nil))

(defn log [s]
  (.log js/console s))

(defn- loadManifest [cb]
  (GET "/manifest.json" {:handler cb}))

(defn- initialize! [window]
  (loadManifest log))

(defonce initialized (do (initialize! js/window) true))
