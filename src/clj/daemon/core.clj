(ns daemon.core
  (:gen-class)
  (:require [daemon.server :as server]
            [daemon.log :as log]))

(defn -main [& args]
  (log/configure!)
  (server/start!))
