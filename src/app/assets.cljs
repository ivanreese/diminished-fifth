(ns app.assets
  (:require [app.math :as math]
            [app.util :refer [log]]
            [app.audio :as audio]
            [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Rich Hickey says I should just use put! instead of this (since put! doesn't require a go block)
(defn callback-to-channel [ch]
  (fn [result]
    (go (>! ch result)
        (close! ch))))

(defn on-err [err]
  (log err))

(defn ajax-channel [url]
  (let [ch (chan)]
    (GET url {:handler (callback-to-channel ch)
              :error-handler on-err})
    ch))

(defn ajax-audio-channel [url]
  (let [ch (chan)
        xhr (js/window.XMLHttpRequest.)]
    (set! (.-responseType xhr) "arraybuffer")
    (.addEventListener xhr "load" (callback-to-channel ch))
    (.open xhr "GET" url)
    (.send xhr)
    ch))

(defn sample-loader [index url]
  (go
   (let [decode-ch (chan)
         audio-xhr (<! (ajax-audio-channel url))
         audio-data (aget audio-xhr "target" "response")]
     (audio/decode audio-data (callback-to-channel decode-ch))
     {:name url
      :index index
      :buffer (<! decode-ch)})))

(defn make-note [index line]
  (-> [:pitch :volume :position]
      (zipmap (mapv js/Number (clojure.string/split line " ")))
      (assoc :index index)
      (update :position / 1000)
      (update :volume math/to-precision 12)))

(defn melody-loader [index url]
  (go
   (let [melody (<! (ajax-channel url))
         lines (clojure.string/split-lines melody)]
     {:name url
      :index index
      :duration (/ (last lines) 1000)
      :notes (map-indexed make-note
                          (drop-last lines))})))

(defn load-assets [manifest type loader-fn]
  (go
   (->> (get manifest type)
        (map #(str type "/" %))
        (map-indexed loader-fn)
        (async/merge)
        (async/into [])
        (<!)
        (sort-by :index))))
