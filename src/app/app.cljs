(ns ^:figwheel-always app.app
  (:require [app.manifest :refer [loadManifest]]))

(defn- initialize! [window]
  (loadManifest))

(defonce initialized (do (initialize! js/window) true))
