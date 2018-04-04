(ns daemon.socket
  (:require [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer [GET]]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [clojure.spec.alpha :as spec]
            [cheshire.core :as json]
            [daemon.event :as event])
  (:import [com.fasterxml.jackson.core JsonParseException]))

(timbre/refer-timbre)

(spec/def ::message (spec/keys :req-un [::op ::data ::seq-id]))
(spec/def ::op string?)
(spec/def ::data map?)
(spec/def ::seq-id any?)

(defmethod event/handle "hello" [& _]
  {})

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn handle-message [socket s]
  (debug (format "Received raw message %s" s))
  (try 
    (let [raw (json/parse-string s keyword)
          event (spec/conform ::message raw)]
      (if (spec/invalid? event)
        (error (format "Invalid message: %s" (spec/explain-str ::message raw)))
        (let [{:keys [op data seq-id]} event]
          (info (format "Message conformed to %s" event))
          (s/put! socket (json/generate-string {:op :ack
                                                :data (event/handle data {:sink (s/sink-only socket) :op op :seq-id seq-id})
                                                :seq-id seq-id})))))
    (catch JsonParseException e
      (error (format "Couldn't parse message as JSON: %s" (pr-str e))))))

(defn responder
  "Consume all messages from `socket` and feed them to `handle-message`"
  [socket]
  (s/consume (fn [event] (handle-message socket event)) socket))

(defn websocket-handler
  "Handle incoming `request` and initiate a WebSocket connect"
  [request]
  (-> (http/websocket-connection request)
    (d/chain responder)
    (d/catch (constantly non-websocket-request))))

(defn handler
  "Deamon websocket handler for requests to `prefix`"
  ([prefix]
   (compojure/context prefix []
     (GET "/" [] websocket-handler)))
  ([] (handler "/socket")))
