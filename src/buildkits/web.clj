(ns buildkits.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.resource :as resource]
            [ring.util.response :as res]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.java.jdbc :as sql]
            [clojure.data.codec.base64 :as base64]
            [buildkits.db :as db]
            [buildkits.html :as html]
            [buildkits.kit :as kit]
            [buildkits.buildpack :as buildpack]))

(defn get-token [code]
  (-> (http/post "https://api.heroku.com/oauth/token"
                 {:form-params {:client_id (System/getenv "OAUTH_CLIENT_ID")
                                :client_secret (System/getenv "OAUTH_CLIENT_SECRET")
                                :code code :grant_type "authorization_code"}})
      (:body) (json/decode true) :access_token))

(defn get-username [token]
  (-> (http/get (str "https://api.heroku.com/user?bearer_token=" token))
      (:body) (json/decode true) :email))

(defroutes app
  (GET "/" {{username :username} :session :as req}
       {:body (sql/with-connection db/db
                (html/dashboard (db/get-buildpacks) username
                                (db/get-kit username)))})
  (GET "/buildkit/:name.tgz" [name]
       (sql/with-connection db/db
         {:status 200
          :headers {"Content-Type" "application/octet-stream"}
          :body (kit/compose name (db/get-kit name))}))
  (GET "/oauth" [code]
       (assoc (res/redirect "/")
         :session {:username (get-username (get-token code))}))
  (GET "/logout" []
       (assoc (res/redirect "/") :session nil))
  (PUT "/kit/:buildpack/:pos" [buildpack pos :as {{:keys [username]} :session}]
       (sql/with-connection db/db
         (db/add-to-kit username buildpack (Integer. pos)))
       (res/redirect "/"))
  (DELETE "/kit/:buildpack" [buildpack :as {{:keys [username]} :session}]
          (sql/with-connection db/db
            (db/remove-from-kit username buildpack))
          (res/redirect "/"))
  (GET "/buildpacks" []
       {:status 200 :headers {"content-type" "application/json"}
        :body (sql/with-connection db/db
                (json/encode (db/get-buildpacks)))})
  (POST "/buildpacks/:name" {{:keys [name buildpack]} :params :as req}
        (let [auth (get (:headers req) "authorization")
              basic (base64/decode (.getBytes (second (.split auth " "))))
              [username key] (.split (String. basic) ":")]
          (buildpack/publish username key name buildpack)))
  (route/not-found "Not found"))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT")))
        store (cookie/cookie-store {:key (System/getenv "SESSION_SECRET")})]
    (jetty/run-jetty (-> #'app
                         (resource/wrap-resource "static")
                         (handler/site {:session {:store store}}))
                     {:port port :join? false})))
