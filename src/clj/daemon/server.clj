(ns daemon.server
  (:require [taoensso.timbre :as timbre]
            [aleph.http :as http]
            [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [daemon.socket :as socket]))

(timbre/refer-timbre)

(defonce server (atom nil))

(defn log-request [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [{:keys [status] :as res} (handler req)]
      (info (format "%s %s -> %s" request-method uri status))
      res)))

(defn alias-request [handler]
  (fn [{:keys [uri] :as req}]
    (if (= uri "/")
      (handler (merge req {:uri "/index.html"}))
      (handler req))))

(def routes
  (log-request
    (alias-request
      (compojure/routes
        (socket/handler)
        (route/resources "/")
        (route/not-found "No such page.")))))

(defn start! []
  (when-not @server
    (reset! server (http/start-server #'routes {:port 8080}))))

(defn stop! []
  (when @server
    (.close @server)
    (reset! server nil)))
