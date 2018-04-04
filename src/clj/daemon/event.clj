(ns daemon.event
  (:require [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defmulti handle "Handle incoming `event`"
  (fn [data {:keys [op]}] op))

(defmethod handle :default [_ {:keys [op]}]
  (error (format "Received unknown op [%s]" op))
  {})
