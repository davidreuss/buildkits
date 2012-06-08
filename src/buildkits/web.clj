(ns buildkits.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [ring.util.response :as res]
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
  (GET "/" {{username :username} :session :as req}
       (sql/with-connection db/db
         (html/dashboard (db/get-buildpacks) username (db/get-kit username))))
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
  (PUT "/kit/:buildpack/:pos" [buildpack pos :as {{:keys [username]} :session}]
       (sql/with-connection db/db
         (db/add-to-kit username buildpack pos))
       (res/redirect "/"))
  (DELETE "/kit/:buildpack" [buildpack :as {{:keys [username]} :session}]
          (sql/with-connection db/db
            (db/remove-from-kit username buildpack))
          (res/redirect "/"))
  (route/not-found "Not found"))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT")))
        store (cookie/cookie-store {:key (System/getenv "SESSION_SECRET")})]
    (jetty/run-jetty (-> #'app
                         (resource/wrap-resource "static")
                         (handler/site {:session {:store store}}))
                     {:port port :join? false})))
