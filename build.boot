(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources/public"}

 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [adzerk/boot-cljs "1.7.228-1"]
                 [pandeiro/boot-http "0.7.3"]
                 [adzerk/boot-reload "0.4.7"]
                 [adzerk/boot-cljs-repl "0.3.0"]
                 [com.cemerick/piggieback "0.2.1"]
                 [weasel "0.7.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/core.async "0.2.374"]
                 [cljs-ajax "0.5.4"]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]])
