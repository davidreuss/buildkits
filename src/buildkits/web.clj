(ns buildkits.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [ring.util.response :as res]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [compojure.core :refer [defroutes GET PUT POST DELETE]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.java.jdbc :as sql]
            [buildkits.db :as db]
            [buildkits.html :as html]
            [buildkits.buildpacks :as buildpacks]))

(defn get-token [code]
  (->> (http/post "https://github.com/login/oauth/access_token"
                  {:form-params {:client_id "4e138466e2422c3e0524"
                                 :client_secret (System/getenv "OAUTH_CLIENT_SECRET")

                                 :code code}})
      (:body) (re-find #"access_token=([^&]+)") (second)))

(defn get-username [token]
  (-> (http/get (str "https://api.github.com/user?access_token=" token))
      (:body) (json/decode true) :login))

(defroutes app
  (GET "/" {identity :identity {username :username} :session :as req}
       (prn req)
       (sql/with-connection db/db
         (html/dashboard (db/get-buildpacks) username)))
  (GET "/buildkit/:name.tgz" [name]
       (sql/with-connection db/db
         {:status 200
          :headers {"Content-Type" "application/octet-stream"}
          :body (buildpacks/composed-kit name (db/get-kit name))}))
  (GET "/oauth" [code]
       (assoc (res/redirect "/")
         :session {:username (get-username (get-token code))}))
  (GET "/logout" []
       (assoc (res/redirect "/") :session nil))
  (PUT "/kit/:buildpack/:position" {:keys [session buildpack position]}
       (buildpacks/add session buildpack position))
  (DELETE "/kit/:buildpack" {:keys [session buildpack]}
          (buildpacks/remove session buildpack))
  (route/not-found "Not found"))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT")))]
    (jetty/run-jetty (-> #'app
                         (resource/wrap-resource "static")
                         ;; (friend/authenticate friend-opts)
                         (handler/site {:session {:store (cookie/cookie-store)}}))
                     {:port port :join? false})))
