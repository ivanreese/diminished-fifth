(ns ^:figwheel-always dev.reload
  (:require [app.render :as render]))

(render/resize!)
(render/render!)
