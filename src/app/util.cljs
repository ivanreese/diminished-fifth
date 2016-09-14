(ns app.util)

(defn snoop-logg [s]
  (js/console.log (clj->js s))
  s)
