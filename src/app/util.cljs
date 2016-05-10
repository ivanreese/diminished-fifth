(ns app.util)

(defn log [s]
  (js/console.log (clj->js s)))
