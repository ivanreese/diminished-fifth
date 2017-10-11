(defproject diminished ""
  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.async "0.2.374"]
                 [figwheel "0.5.3-2"]
                 [cljs-ajax "0.5.5"]]
  
  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-figwheel "0.5.3-1"]]
  
  :hooks [leiningen.cljsbuild]
  
  :figwheel {:css-dirs ["resources/public/styles"]
             :server-port 3000}
             ;:server-ip "192.168.1.13"}
  
  :clean-targets ^{:protect false} ["resources/public/scripts"]
  
  :cljsbuild {:builds {:main {:source-paths ["src"]
                              :compiler {:output-to "resources/public/scripts/main.js"
                                         :output-dir "resources/public/scripts"
                                         :compiler-stats true}}}}
  
  :profiles {
             ; lein figwheel
             :dev {:cljsbuild {:builds {:main {:source-paths ["dev"]
                                               :figwheel true
                                               :compiler {:main "dev.dev"
                                                          :asset-path "scripts"
                                                          :optimizations :none
                                                          :source-map-timestamp true}}}}}
             
             ; lein with-profile prod compile
             :prod {:cljsbuild {:builds {:main {:compiler {:optimizations :advanced
                                                           :elide-asserts true
                                                           :pretty-print false}}}}}})
