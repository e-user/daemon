(ns daemon.input
  (:require [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as spec]
            [daemon.socket :as socket]
            [daemon.event :as event]))

(timbre/refer-timbre)

(spec/def ::data (spec/keys :req-un [::ctrl? ::alt? ::shift? ::meta? ::code ::name]))
(spec/def ::ctrl? boolean?)
(spec/def ::alt? boolean?)
(spec/def ::shift? boolean?)
(spec/def ::meta? boolean?)
(spec/def ::code pos-int?)
(spec/def ::name string?)

(defmethod event/handle "input" [data {:keys [sink]}]
  (let [data' (spec/conform ::data data)]
    (if (spec/invalid? data')
      (socket/report-error sink (spec/explain-str ::message data))
      (let [{:keys [ctrl? alt? shift? meta? code name]} data']
        (info (format "TODO handle input")))))
  {})
