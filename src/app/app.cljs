(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce window js/window)
(defonce audio-context-sym (or (.-AudioContext window) (.-webkitAudioContext window)))
(defonce audio-context (new audio-context-sym))

(defn soon [fn]
  (js/window.setTimeout fn 30))

(defn log [s]
  (js/console.log (clj->js s)))

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

(defn sample-channel [url]
  (go
   (let [decode-ch (chan)
         audio-xhr (<! (ajax-audio-channel url))
         audio-data (aget audio-xhr "target" "response")]
     (.decodeAudioData audio-context audio-data (callback-to-channel decode-ch))
     {:buffer (<! decode-ch)})))


(defn make-melody [melody]
  (let [lines (clojure.string/split-lines melody)]
    {:duration (count lines)
     :notes (map (fn [line]
                   (zipmap [:pitch :volume :position]
                           (clojure.string/split line " ")))
                 (drop-last lines))}))

(defn play [sample]
  (let [source (.createBufferSource audio-context)]
    (set! (.-buffer source) (:buffer sample))
    (.connect source (.-destination audio-context))
    (.start source)))

(defn- initialize! []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))
          melody-paths (map #(str "/melodies/" %) (get manifest "melodies"))
          sample-paths (map #(str "/samples/" %) (get manifest "samples"))
          melodies (map make-melody (<! (async/into [] (async/merge (map ajax-channel melody-paths)))))
          samples (<! (async/into [] (async/merge (map sample-channel sample-paths))))]
      (play (first samples))
      (log melodies))))


; (defonce initialized (do (initialize!) true))
(soon #(do (js/console.clear) (soon initialize!)))
