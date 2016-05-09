(ns ^:figwheel-always app.app
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defonce window js/window)
(defonce audio-context (let [AC (or (.-AudioContext window) (.-webkitAudioContext window))] (AC.)))
(defonce sample-rate (.-sampleRate audio-context))


(defn soon [fn]
  (js/window.setTimeout fn 100))

(defn log [s]
  (js/console.log (clj->js s)))


(defn make-impulse [n length decay]
  (* (- 1 (* 2 (.random js/Math)))
     (.pow js/Math (- 1 (/ n length)) decay)))

(defn make-reverb [seconds decay reverse]
  (let [wet (.createGain audio-context)
        dry (.createGain audio-context)
        input (.createGain audio-context)
        output (.createGain audio-context)
        convolver (.createConvolver audio-context)
        length (* sample-rate seconds)
        impulse (.createBuffer audio-context 2 length sample-rate)
        impulseL (.getChannelData impulse 0)
        impulseR (.getChannelData impulse 1)]
    (doseq [i (range length)]
      (let [n (if reverse (- length i) i)]
        (aset impulseL i (make-impulse n length decay))
        (aset impulseR i (make-impulse n length decay))))
    (set! (.-buffer convolver) impulse)
    (.connect input dry)
    (.connect dry output)
    (.connect input convolver)
    (.connect convolver wet)
    (.connect wet output)
    {:input input
     :output output
     :wet wet.gain
     :dry dry.gain}))


(def master
  (let [input (.createGain audio-context)
        analyser (.createAnalyser audio-context)
        reverb (make-reverb 1, 100, false)
        compressor (.createDynamicsCompressor audio-context)
        output (.createGain audio-context)]
    (aset input "gain" "value" .8)
    (aset compressor "knee" "value" 40)
    (aset output "gain" "value" .66)
    (aset (:wet reverb) "value" 0.0)
    (aset (:dry reverb) "value" 1.0)
    (.connect input analyser)
    (.connect analyser (:input reverb))
    (.connect (:output reverb) compressor)
    (.connect compressor output)
    (.connect output (.-destination audio-context))
    {:input input
     :analyser analyser}))
  

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
     (.decodeAudioData audio-context audio-data (callback-to-channel decode-ch))
     {:name url
      :index index
      :buffer (<! decode-ch)})))


(defn melody-loader [index url]
  (go
    (let [melody (<! (ajax-channel url))
          lines (clojure.string/split-lines melody)]
      {:name url
       :index index
       :duration (count lines)
       :notes (map (fn [line]
                     (zipmap [:pitch :volume :position]
                             (clojure.string/split line " ")))
                   (drop-last lines))})))


(defn play [sample]
  (let [source (.createBufferSource audio-context)]
    (aset source "buffer" (:buffer sample))
    (aset source "playbackRate" "value" 1)
    (.connect source (:input master))
    (.start source)))


(defn load-asset [manifest type loader-fn]
  (go
   (->> (get manifest type)
        (map #(str "/" type "/" %))
        (map-indexed loader-fn)
        (async/merge)
        (async/into [])
        (<!)
        (sort-by :index))))


(defn- initialize! []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))
          melodies (<! (load-asset manifest "melodies" melody-loader))
          samples (<! (load-asset manifest "samples" sample-loader))]
      (play (first samples)))))


; (defonce initialized (do (initialize!) true))
(soon #(do (js/console.clear) (soon initialize!)))
