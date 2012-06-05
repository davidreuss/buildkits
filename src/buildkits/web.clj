(ns buildkits.web
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.resource :as resource]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [compojure.core :refer [defroutes GET PUT POST DELETE]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.java.jdbc :as sql]
            [buildkits.db :as db]
            [buildkits.html :as html]
            [buildkits.buildpacks :as buildpacks]))

(defroutes app
  (GET "/" {session :session}
       (sql/with-connection db/db
         (html/dashboard (db/get-buildpacks))))
  (PUT "/kit/:buildpack/:position" {:keys [session buildpack position]}
       (buildpacks/add session buildpack position))
  (DELETE "/kit/:buildpack" {:keys [session buildpack]}
          (buildpacks/remove session buildpack))
  (PUT "/:buildpack" {params :params session :session}
       (buildpacks/create session params))
  (DELETE "/:buildpack" {session :session buildpack :buildpack}
       (buildpacks/create session buildpack))
  (route/not-found "Not found"))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT")))]
    (jetty/run-jetty (-> #'app
                         (resource/wrap-resource "static")
                         (handler/site))
                     {:port port :join? false})))
