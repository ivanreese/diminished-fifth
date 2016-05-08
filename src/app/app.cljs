(ns ^:figwheel-always app.app
  (:require [app.manifest :refer [loadManifest!]]
            [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close! sliding-buffer put! alts!]])
  (:require-macros [cljs.core.async.macros :as m :refer [go alt!]]))

(defonce state (atom nil))

(defn soon [fn]
  (js/window.setTimeout fn 30))

(defn log [s]
  (js/console.log (clj->js s)))

(defn- saveAndLog [v]
  (do
   
   (log @state)))

(defn- initialize! [window]
  (go
    (let [manifest (<! (loadManifest!))
          samples (get manifest "samples")
          melodies (get manifest "melodies")]
      (log samples)
      (log melodies))))
    
        

; (defonce initialized (do (initialize! js/window) true))
(soon #(do (js/console.clear) (soon initialize!)))
