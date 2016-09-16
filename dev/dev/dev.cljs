(ns dev.dev
  (:require [app.app]
            [dev.reload]
            [figwheel.client :refer [start]]))

(enable-console-print!)

(start)
