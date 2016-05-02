;;;; App
;; The app namespace holds most the state, and most of the control flow, in our system.
;; This file is the entry point of the system, and orchestrates everything.

(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]))

(defn log [s]
  (.log js/console s))

(defn- loadManifest [cb]
  (GET "/manifest.json" {:handler cb}))

(defn- initialize! [window]
  (loadManifest log))

(defonce initialized (do (initialize! js/window) true))
