(ns app.app
  (:require [app.assets :refer [ajax-channel load-samples]]
            [app.audio :as audio]
            [app.engine :as engine]
            [app.history :as history]
            [app.melodies :as melodies]
            [app.orchestra :as orchestra]
            [app.render :as render]
            [app.state :refer [manifest state samples callback]]
            [app.util :refer [snoop-logg]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def preload-elm (js/document.querySelector ".preload"))

(defn play []
  (swap! state engine/start))

(defn pause []
  (swap! state engine/stop))

(defn tick [dt]
  (swap! state orchestra/tick dt (get-in @state [:engine :time]))
  (when-not (:gui-disabled @state)
    (render/render!))
  (when (:tick-once-mode @state)
    (swap! state assoc :tick-once-mode false)
    (pause)))

(defn resize [& args]
  (render/resize!)
  (render/render!))

(defn restart []
  (reset! state {})
  (history/init)
  (swap! state engine/restart tick)
  (swap! state orchestra/init (get-in @state [:engine :time]))
  (resize))

(defn fullscreen []
  (js/document.body.webkitRequestFullscreen))

(defn sound-check []
  (let [sample (nth @samples (int (rand (count @samples))))
        key-transposition (get-in @state [:orchestra :transposition])]
    (audio/play sample {:pitch key-transposition
                        :volume 0.3
                        :pos 0})))

(defn transpose []
  (swap! state update-in [:orchestra :transposition] orchestra/update-transposition)
  (when-not (get-in @state [:engine :running])
    (render/render!)))

(defn tick-once []
  (swap! state assoc :tick-once-mode true)
  (play))

(defn gui []
  (let [gui-disabled (not (:gui-disabled @state))
        btn (js/document.querySelector ".gui.button")]
    (swap! state assoc :gui-disabled gui-disabled)
    (render/render!)
    (if gui-disabled
      (.setAttribute btn "active" true)
      (.removeAttribute btn "active"))))

(defn mute []
  (let [muted (not (:mute @state))
        btn (js/document.querySelector ".mute.button")]
    (swap! state assoc :mute muted)
    (render/render!)
    (if muted
      (.setAttribute btn "active" true)
      (.removeAttribute btn "active"))))

(defn setup-button [class callback]
  (.addEventListener (js/document.querySelector (str "." class))
                     "click"
                     callback))

(defn init []
  (.removeEventListener js/window "click" init)
  (.removeEventListener preload-elm "click" init)
  (audio/setup)
  (melodies/init)
  (set! (.-textContent preload-elm) "Loading Audio Files")
  (go
    (let [manifest-data (<! (ajax-channel "manifest.json"))]
      (reset! manifest manifest-data)
      (reset! samples (load-samples manifest-data))
      (js/window.addEventListener "resize" resize)
      (.setAttribute preload-elm "hide", "")
      (.removeAttribute (js/document.querySelector ".buttons") "hide")
      (setup-button "play" play)
      (setup-button "pause" pause)
      (setup-button "restart" restart)
      (setup-button "tick-once" tick-once)
      (setup-button "redraw" render/render!)
      (setup-button "gui" gui)
      (setup-button "mute" mute)
      (setup-button "fullscreen" fullscreen)
      (setup-button "sound-check" sound-check)
      (setup-button "transpose" transpose)
      (restart)
      (play))))

(defn preload []
  (set! (.-textContent preload-elm) "Click To Init")
  (.addEventListener preload-elm "click" init)
  (.addEventListener js/window "click" init))

(defonce initialized (do (preload) true))
