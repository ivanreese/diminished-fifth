(ns app.state)

(defonce state (atom {}))
(defonce samples (atom {}))
(defonce melodies (atom {}))
(defonce callback (atom nil))
(defonce context (atom nil))
(defonce history (atom {}))
(defonce history-min (atom {}))
(defonce history-max (atom {}))
                      
