(ns daemon.server
  (:require [taoensso.timbre :as timbre]
            [aleph.http :as http]
            [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [daemon.socket :as socket]
            daemon.input
            [daemon.buffer :as buffer]
            [aleph.middleware.session :as session]))

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
  (let [routes (compojure/routes
                 (socket/handler)
                 (GET "/session" [] {:status 200 :body "OK" :session {}})
                 (route/resources "/")
                 (route/not-found "No such page."))]
    (-> routes log-request alias-request (session/wrap-session {:cookie-name "daemon"}))))

(defn start! []
  (when-not @server
    (reset! server (http/start-server #'routes {:port 8080}))))

(defn stop! []
  (when @server
    (.close @server)
    (reset! server nil)))
