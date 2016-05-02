;;;; App
;; The app namespace holds most the state, and most of the control flow, in our system.
;; This file is the entry point of the system, and orchestrates everything.

(ns ^:figwheel-always app.app
  (:require [data.manifest :as manifest]))

(defn- initialize!
  "In the correct order, set up all the subsystems in our app. Takes the js window object. Return value should be ignored."
  [window]
  "Stubby")

;; PUBLIC

;; This is how we bootstrap the app. In the future, different production environments might require changing this.
(defonce initialized (do (initialize! js/window) true))
