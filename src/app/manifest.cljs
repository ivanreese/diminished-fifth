(ns app.manifest
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close! sliding-buffer put! alts!]])
  (:require-macros [cljs.core.async.macros :as m :refer [go alt!]]))

(defn loadManifest! []
  (let [ch (chan)]
    (GET "/manifest.json"
         {:handler #(go (>! ch %))})
    ch))
