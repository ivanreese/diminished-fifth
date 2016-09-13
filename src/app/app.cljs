(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.audio :as audio]
            [app.canvas :as canvas]
            [app.engine :as engine]
            [app.orchestra :as orchestra]
            [app.render :as render]
            [app.state :refer [state melodies samples callback text-context history history-min history-max]]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def preload-elm (js/document.querySelector ".preload"))
(defonce tick-once-mode (atom false))
(defonce toggle-gui-mode (atom true))

(defn doRender []
  (render/render! @state @text-context))

(defn tick-once []
  (let [mode (not @tick-once-mode)
        btn (js/document.querySelector ".tick-once.button")]
    (reset! tick-once-mode mode)
    (if mode
      (.setAttribute btn "active" true)
      (.removeAttribute btn "active"))))

(defn toggle-gui []
  (let [mode (not @toggle-gui-mode)
        btn (js/document.querySelector ".toggle-gui.button")]
    (reset! toggle-gui-mode mode)
    (doRender)
    (if mode
      (.removeAttribute btn "active")
      (.setAttribute btn "active" true))))

(defn play []
  (swap! state engine/start))

(defn pause []
  (swap! state engine/stop))

(defn tick [dt]
  (when @tick-once-mode (pause))
  (swap! state orchestra/tick dt (get-in @state [:engine :time]))
  (when @toggle-gui-mode
    (doRender)))

(defn resize [& args]
  (render/resize! @text-context)
  (render/render! @state @text-context))


(defn restart []
  (reset! state {})
  (reset! history {})
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
    (doRender)))

(defn setup-button [class callback]
  (.addEventListener (js/document.querySelector (str "." class))
                     "click"
                     callback))

(defn init []
  (.removeEventListener preload-elm "click" init)
  (audio/setup)
  (set! (.-textContent preload-elm) "Loading Audio Files")
  (go
    (let [manifest (<! (ajax-channel "manifest.json"))]
      (reset! melodies (<! (load-assets manifest "melodies" melody-loader)))
      (reset! samples (<! (load-assets manifest "samples" sample-loader)))
      (reset! text-context (canvas/create!))
      (js/document.addEventListener "resize" resize)
      (.setAttribute preload-elm "hide", "")
      (.removeAttribute (js/document.querySelector ".buttons") "hide")
      (setup-button "play" play)
      (setup-button "pause" pause)
      (setup-button "restart" restart)
      (setup-button "tick-once" tick-once)
      (setup-button "redraw" doRender)
      (setup-button "toggle-gui" toggle-gui)
      (setup-button "fullscreen" fullscreen)
      (setup-button "sound-check" sound-check)
      (setup-button "transpose" transpose)
      (restart)
      (play))))

(defn preload []
  (set! (.-textContent preload-elm) "Click To Init")
  (.addEventListener preload-elm "click" init))

(defonce initialized (do (preload) true))
