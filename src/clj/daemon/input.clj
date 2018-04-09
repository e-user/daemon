(ns daemon.input
  (:require [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as spec]
            [daemon.log :as log]
            [daemon.event :as event]
            [daemon.buffer :as buffer]
            [daemon.util :refer :all]))

(timbre/refer-timbre)

(defn keyword-string? [s]
  (if (string? s)
    (keyword s)
    ::spec/invalid))

(spec/def ::data (spec/keys :req-un [::key ::buffer ::pos]))
(spec/def ::key (spec/keys :req-un [::ctrl? ::alt? ::shift? ::meta? ::code ::name ::char ::id]))
(spec/def ::buffer string?)
(spec/def ::pos (spec/coll-of int? :count 2))

(spec/def ::ctrl? boolean?)
(spec/def ::alt? boolean?)
(spec/def ::shift? boolean?)
(spec/def ::meta? boolean?)
(spec/def ::code pos-int?)
(spec/def ::name string?)
(spec/def ::char string?)
(spec/def ::id (spec/conformer keyword-string? str))

(defn printable? [n]
  (or (= n 32) (<= 48 n 90) (<= 186 n 222)))

(defmethod event/handle "input" [data {:keys [sink]}]
  (do1 {}
    (let [data' (spec/conform ::data data)]
     (if (spec/invalid? data')
       (log/log! (spec/explain-str ::data data))
       (let [{:keys [key buffer pos]} data'
             {:keys [ctrl? alt? shift? meta? code char name id]} key] ; TODO
         (when (printable? code)
           (buffer/insert! buffer pos char)))))))
