(ns buildkits.buildpack
  (:require [buildkits.db :as db]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]
            [cheshire.core :as json]
            [clojure.data.codec.base64 :as base64]
            [compojure.core :refer [defroutes GET PUT POST DELETE ANY]])
  (:import (org.jets3t.service.security AWSCredentials)
           (org.jets3t.service.acl AccessControlList)
           (org.jets3t.service.impl.rest.httpclient RestS3Service)
           (org.jets3t.service.acl.gs AllUsersGrantee)
           (org.jets3t.service.acl Permission)
           (org.jets3t.service.model S3Object)))

;; for actions coming from the heroku-buildpacks plugin
(defn check-api-key [username key]
  (try (= username (.getEmail (.getUserInfo (com.heroku.api.HerokuAPI. key))))
       (catch com.heroku.api.exception.RequestFailedException _)))

(defn org-member? [username org]
  (sql/with-query-results [member] [(str "SELECT * FROM memberships, organizations "
                                         "WHERE email = ? AND organizations.name = ?")
                                    username org]
    (boolean member)))

(defn s3-put [org buildpack-name content]
  (when-let [access_key (System/getenv "AWS_ACCESS_KEY")]
    (let [s3 (RestS3Service. (AWSCredentials. access_key
                                              (System/getenv "AWS_SECRET_KEY")))
          bucket (or (System/getenv "AWS_BUCKET") "buildkits-dev")
          key (format "buildpacks/%s/%s.tgz" org buildpack-name)
          obj (doto (S3Object. key content)
                (.setAcl (AccessControlList/REST_CANNED_PUBLIC_READ)))]
      (.putObject s3 bucket obj))))

;; why is this not in clojure.java.io?
(defn get-bytes [input]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy input baos)
    (when (instance? java.io.File input)
      (.delete input))
    (.toByteArray baos)))

(defn create [username org buildpack-name content]
  (let [bytes (get-bytes content)
        rev-id (db/create username org buildpack-name bytes)]
    (s3-put org buildpack-name bytes)
    {:status 201 :body (json/encode {:revision rev-id})}))

(defn update [username org buildpack content]
  (let [bytes (get-bytes content)
        rev-id (db/update org (:id buildpack) bytes)]
    (s3-put org (:name buildpack) bytes)
    {:status 200 :body (json/encode {:revision rev-id})}))

(defn- rollback-query [org buildpack-id target]
  (if (= target "previous")
    [(str "SELECT * FROM revisions WHERE buildpack_id = ?"
          " ORDER BY id DESC OFFSET 1 LIMIT 1") buildpack-id]
    ["SELECT * FROM revisions WHERE buildpack_id = ? AND id = ?"
     buildpack-id (Integer. target)]))

(defn rollback [username org buildpack target]
  (sql/with-query-results [rev] (rollback-query org (:id buildpack) target)
    (if [rev]
      (update username org buildpack (:tarball rev))
      {:status 404})))

(defn revisions [_ _ buildpack]
  (sql/with-query-results revs [(str "SELECT id, published_by, created_at"
                                     " FROM revisions"
                                     " WHERE buildpack_id = ? ORDER BY id")
                                (:id buildpack)]
    {:status 200 :body (json/encode revs)}))

(defn share [_ org email]
  (sql/with-query-results [{:keys [id]}]
    ["SELECT * FROM organizations WHERE name = ?" org]
    ;; TODO: ensure user exists
    (sql/with-query-results [member] [(str "SELECT * FROM memberships WHERE"
                                           " email = ? AND organization_id = ?")
                                      email id]
      (if member
        {:status 409}
        (do (sql/insert-record "memberships" {:email email :organization_id id})
            {:status 201})))))

(defn unshare [_ org email]
  (sql/with-query-results [membership]
    [(str "SELECT * FROM memberships, organizations WHERE email = ? AND"
          " organization_id = organizations.id AND organizations.name = ?")
     email org]
    (if membership
      (do (sql/delete-rows "memberships" ["id = ?" (:id membership)])
          {:status 200})
      {:status 404})))

(def ^:dynamic *not-found* (constantly {:status 404}))

(defn check-auth [headers org callback & args]
  (if-let [authorization (get headers "authorization")]
    (let [[username key] (-> authorization (.split " ") second
                             .getBytes base64/decode String. (.split ":"))]
      (sql/with-connection db/db
        (if (check-api-key username key)
          (if (org-member? username org)
            (apply callback username org args)
            {:status 403})
          {:status 401})))
    {:status 401}))

(defn check-pack-auth [headers org buildpack-name callback & args]
  (let [pack-callback (fn [username org & args]
                        (if-let [pack (db/get-buildpack org buildpack-name)]
                          (apply callback username org pack args)
                          (apply *not-found* username org args)))]
    (apply check-auth headers org pack-callback args)))

(defroutes app
  (GET "/buildpacks" []
       {:status 200 :headers {"content-type" "application/json"}
        :body (sql/with-connection db/db
                (json/encode (db/get-buildpacks)))})
  (GET "/buildpacks/:org/:name/revisions" {{:keys [org name]} :params
                                           headers :headers}
       (check-pack-auth headers org name revisions))
  (POST "/buildpacks/:org/:name" {{:keys [org name buildpack]} :params
                                  headers :headers}
        (binding [*not-found* create]
          (check-pack-auth headers org name update (:tempfile buildpack))))
  (POST "/buildpacks/:org/:name/revisions/:target"
        {{:keys [org name target]} :params headers :headers}
        (check-pack-auth headers org name rollback target))
  (POST "/buildpacks/:org/share/:email" {{:keys [org email]} :params
                                         headers :headers}
        (check-auth headers org share email))
  (DELETE "/buildpacks/:org/share/:email" {{:keys [org email]} :params
                                           headers :headers}
          (check-auth headers org unshare email)))