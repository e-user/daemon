(ns daemon.socket
  (:require [compojure.core :as compojure :refer [GET]]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(def not-implemented
  {:status 501
   :headers {"content-type" "application/text"}
   :body "Not implemented."})

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn websocket-handler [req]
  (-> (http/websocket-connection req)
    (d/chain (fn [socket] (s/connect socket socket)))
    (d/catch (fn [_] non-websocket-request))))

(defn handler
  ([prefix]
   (compojure/context prefix []
     (GET "/" [] websocket-handler)))
  ([] (handler "/socket")))
