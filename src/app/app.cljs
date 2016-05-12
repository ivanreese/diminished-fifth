(ns ^:figwheel-always app.app
  (:require [app.assets :refer [load-assets ajax-channel melody-loader sample-loader]]
            [app.audio :refer [play]]
            [app.state :refer [state]]
            [app.engine :as engine]
            [app.util :refer [log]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- acceleration [sim-state time]
  100)
  ; (let [k 10 b 1]
  ;   (- (* (- k) (:x sim-state))
  ;      (*    b  (:v sim-state)))))

(defn- evaluate [initial t dt d]
  (let [x (+ (:x initial) (* (:dx d) dt))
        v (+ (:v initial) (* (:dv d) dt))
        state {:x x :v v}
        dv (acceleration state (+ t dt))]
    {:dx v :dv dv}))

(defn- calc-derivative [k a b c d]
  (let [ka (k a) kb (k b) kc (k c) kd (k d)]
    (/ (+ ka (* 2 (+ kb kc)) kd) 6)))

(defn- integrate [sim-state sim-time sim-dt]
  (let [a (evaluate sim-state sim-time 0 {:dx 0 :dv 0})
        b (evaluate sim-state sim-time (/ sim-dt 2) a)
        c (evaluate sim-state sim-time (/ sim-dt 2) b)
        d (evaluate sim-state sim-time sim-dt c)
        dxdt (calc-derivative :dx a b c d)
        dvdt (calc-derivative :dv a b c d)]
    {:x (+ (:x state) (* dxdt sim-dt))
     :v (+ (:v state) (* dvdt sim-dt))}))
    

(defn- render [alpha]
  (js/console.clear)
  ; (log (:engine/this-sim-state @state)))
  (play (first (:samples @state)) (first (:notes (first (:melodies @state)))))
  (engine/stop))

(defn- reloaded []
  (engine/start integrate render))

(defn- initialize []
  (go
    (let [manifest (<! (ajax-channel "/manifest.json"))]
      (swap! state assoc :melodies (<! (load-assets manifest "melodies" melody-loader)))
      (swap! state assoc :samples (<! (load-assets manifest "samples" sample-loader)))
      (swap! state assoc :app/loaded true)
      (reloaded))))

(defonce initialized (do (initialize) true))

(if (:app/loaded @state) (reloaded))
