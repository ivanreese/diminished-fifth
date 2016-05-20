(ns app.render
  (:require [app.canvas :as canvas]))

(defn render [state context]
  (-> @context
    (canvas/clear!)
    (canvas/lineCap! "round")
    (canvas/lineJoin! "round")
    (canvas/lineWidth! "3")))
