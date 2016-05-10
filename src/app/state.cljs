(ns app.state)

(defonce state (atom {:players []
                      :nextIndex 0
                      :playbackRate 1
                      :transposition 1}))
