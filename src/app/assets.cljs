(ns app.assets
  (:require [app.util :refer [log]]
            [app.state :refer [audio-context buffers]]
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
     (.decodeAudioData @audio-context audio-data (callback-to-channel decode-ch))
     {:name url
      :index index
      :buffer (<! decode-ch)})))

; (defn load-samples [manifest]
;   (go
;    (->> (get manifest "samples")
;         (map #(str "samples" "/" %))
;         (map-indexed sample-loader)
;         (async/merge)
;         (async/into [])
;         (<!)
;         (sort-by :index))))

(defn make-sample [index url]
  {:name url
   :index index})

(defn load-samples [manifest]
  (->> (get manifest "samples")
       (map #(str "samples/" %))
       (map-indexed make-sample)))

(defn request-sample [sample]
  (let [index (:index sample)
        url (:name sample)]
    (when (nil? (get @buffers index))
      (swap! buffers assoc index sample)
      (go
       (let [decode-ch (chan)
             audio-xhr (<! (ajax-audio-channel url))
             audio-data (aget audio-xhr "target" "response")]
         (.decodeAudioData @audio-context audio-data (callback-to-channel decode-ch))
         (swap! buffers assoc-in [index :buffer] (<! decode-ch)))))))


; If the sample is loaded, return the buffer
; If not, start loading the sample
(defn get-buffer [sample]
  (if-let [buffer (get-in @buffers [(:index sample) :buffer])]
    buffer
    (do
     (request-sample sample)
     nil)))
