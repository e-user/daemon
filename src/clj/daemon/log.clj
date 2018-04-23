(ns daemon.log
  (:require [taoensso.timbre :as timbre]
            [aleph.netty :as netty]))

(timbre/refer-timbre)

;; This is used to write to the Messages buffer. Defined in `daemon.buffer`
(declare log!)

(def println-appender (assoc (timbre/println-appender {:stream :auto})
                        :ns-blacklist ["io.netty.*"]))
(def netty-appender (assoc (timbre/spit-appender {:fname "./log/netty.log"})
                      :ns-whitelist ["io.netty.*"]))
(def daemon-appender (assoc (timbre/spit-appender {:fname "./log/out.log"})
                       :ns-blacklist ["io.netty.*"]))
(def buffer-appender
  {:enabled?   true
   :async?     false
   :min-level  :info
   :rate-limit nil
   :ns-blacklist ["io.netty.*"]
   :fn (fn [{:keys [level msg_]}]
         (log! (format "%s %s" level (force msg_))))})

(def config
  {:level :debug
   :appenders {:println println-appender
               :netty netty-appender
               :spit daemon-appender
               :buffer buffer-appender}})

(defn configure! []
  (timbre/merge-config! config)
  (netty/set-logger! :slf4j))
