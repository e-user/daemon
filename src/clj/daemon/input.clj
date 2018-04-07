(ns daemon.input
  (:require [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as spec]
            [daemon.log :as log]
            [daemon.event :as event]
            [daemon.buffer :as buffer]
            [daemon.util :refer :all]))

(timbre/refer-timbre)

(spec/def ::data (spec/keys :req-un [::key ::buffer ::pos]))
(spec/def ::key (spec/keys :req-un [::ctrl? ::alt? ::shift? ::meta? ::code ::name ::char]))
(spec/def ::buffer string?)
(spec/def ::pos (spec/coll-of int? :count 2))

(spec/def ::ctrl? boolean?)
(spec/def ::alt? boolean?)
(spec/def ::shift? boolean?)
(spec/def ::meta? boolean?)
(spec/def ::code pos-int?)
(spec/def ::name string?)
(spec/def ::char string?)

(defmethod event/handle "input" [data {:keys [sink]}]
  (do1 {}
    (let [data' (spec/conform ::data data)]
     (if (spec/invalid? data')
       (log/log! (spec/explain-str ::data data))
       (let [{:keys [key buffer pos]} data'
             {:keys [ctrl? alt? shift? meta? code char name]} key] ; TODO
         (when (>= code 32)
           (buffer/insert! buffer pos char)))))))
