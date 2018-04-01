(ns daemon.log
  (:require [taoensso.timbre :as timbre]
            [aleph.netty :as netty]))

(timbre/refer-timbre)

(def netty-appender (assoc (timbre/spit-appender {:fname "./log/netty.log"})
                      :ns-whitelist ["io.netty.*"]))
(def daemon-appender (assoc (timbre/spit-appender {:fname "./log/out.log"})
                       :ns-blacklist ["io.netty.*"]))

(def config
  {:level :debug
   :appenders {:netty netty-appender
               :spit daemon-appender}})

(defn configure! []
  (timbre/merge-config! config)
  (netty/set-logger! :slf4j))
