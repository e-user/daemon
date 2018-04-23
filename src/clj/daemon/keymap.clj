(ns daemon.keymap
  (:require [taoensso.timbre :as timbre]
            [clojure.set :as set]))

(timbre/refer-timbre)

(def modifiers #{:ctrl :alt :shift :meta})

(defmulti dispatch (fn [buffer keys] (:keymap buffer)))
