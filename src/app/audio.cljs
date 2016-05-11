(ns app.audio
  (:require [app.util :refer [log]]
            [app.state :refer [state]]))

(defonce audio-context (let [AC (or (.-AudioContext js/window)
                                    (.-webkitAudioContext js/window))]
                         (AC.)))

(defn make-impulse [n length decay]
  (* (- 1 (* 2 (.random js/Math)))
     (.pow js/Math (- 1 (/ n length)) decay)))

(defn make-reverb [seconds decay reverse]
  (let [wet (.createGain audio-context)
        dry (.createGain audio-context)
        input (.createGain audio-context)
        output (.createGain audio-context)
        convolver (.createConvolver audio-context)
        length (* (:sample-rate @state) seconds)
        impulse (.createBuffer audio-context 2 length (:sample-rate @state))
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

(defn- make-master []
  (let [input (.createGain audio-context)
        analyser (.createAnalyser audio-context)
        reverb (make-reverb 1, 100, false)
        compressor (.createDynamicsCompressor audio-context)
        output (.createGain audio-context)]
    (aset input "gain" "value" 1)
    (aset compressor "knee" "value" 40)
    (aset (:wet reverb) "value" 2.0)
    (aset (:dry reverb) "value" 1.0)
    (aset output "gain" "value" 1)
    (.connect input analyser)
    (.connect analyser (:input reverb))
    (.connect (:output reverb) compressor)
    (.connect compressor output)
    (.connect output (.-destination audio-context))
    {:input input
     :analyser analyser}))

;; PUBLIC

(defn play [sample note]
  (let [source (.createBufferSource audio-context)
        gain (.createGain audio-context)]
    (aset source "buffer" (:buffer sample))
    (aset source "playbackRate" "value" (:pitch note))
    (aset gain "gain" "value" (:volume note))
    (.connect source gain)
    (.connect gain (:input (:master @state)))
    (.start source)))

;; SETUP

(defn- initialize []
  (swap! state assoc :sample-rate (.-sampleRate audio-context))
  (swap! state assoc :master (make-master)))

(defonce initialized (do (initialize) true))
