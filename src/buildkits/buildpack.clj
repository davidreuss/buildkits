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

(defn s3-put [buildpack-name content]
  (when-let [access_key (System/getenv "AWS_ACCESS_KEY")]
    (let [s3 (RestS3Service. (AWSCredentials. access_key
                                              (System/getenv "AWS_SECRET_KEY")))
          bucket (or (System/getenv "AWS_BUCKET") "buildkits-dev")
          key (format "buildpacks/%s.tgz" buildpack-name)
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

(defn create [username buildpack-name content]
  (let [bytes (get-bytes content)
        rev-id (db/create username buildpack-name bytes)]
    (s3-put buildpack-name bytes)
    {:status 201 :body (json/encode {:revision rev-id})}))

(defn update [username buildpack _ content]
  (let [bytes (get-bytes content)
        rev-id (db/update (:name buildpack) bytes)]
    (s3-put (:name buildpack) bytes)
    {:status 200 :body (json/encode {:revision rev-id})}))

(defn- rollback-query [buildpack-name target]
  (if (= target "previous")
    [(str "SELECT * FROM revisions WHERE buildpack_name = ?"
          " ORDER BY id DESC OFFSET 1 LIMIT 1") buildpack-name]
    ["SELECT * FROM revisions WHERE buildpack_name = ? AND id = ?"
     buildpack-name (Integer. target)]))

(defn rollback [username buildpack target]
  (sql/with-query-results [rev] (rollback-query (:name buildpack) target)
    (if [rev]
      (update username buildpack nil (:tarball rev))
      {:status 404})))

(defn revisions [username buildpack]
  (sql/with-query-results revs [(str "SELECT id, created_at from revisions"
                                     " WHERE buildpack_name = ? ORDER BY id")
                                (:name buildpack)]
    {:status 200 :body (json/encode revs)}))

(def ^:dynamic *not-found* (constantly {:status 404}))

(defn check-auth [headers buildpack-name found & args]
  (let [[username key] (-> (get headers "authorization") (.split " ") second
                           .getBytes base64/decode String. (.split ":"))]
    (if (check-api-key username key)
      (sql/with-connection db/db
        (if-let [pack (db/get-buildpack buildpack-name)]
          (if (= (:owner pack) username)
            (apply found username pack args)
            {:status 403})
          (apply *not-found* username args)))
      {:status 401})))

(defroutes app
  (GET "/buildpacks" []
       {:status 200 :headers {"content-type" "application/json"}
        :body (sql/with-connection db/db
                (json/encode (db/get-buildpacks)))})
  (GET "/buildpacks/:name/revisions" {{:keys [name]} :params headers :headers}
       (check-auth headers name revisions))
  (POST "/buildpacks/:name" {{:keys [name buildpack]} :params headers :headers}
        (binding [*not-found* create]
          (check-auth headers name update name (:tempfile buildpack))))
  (POST "/buildpacks/:name/revisions/:target" {{:keys [name target]} :params
                                                 headers :headers}
        (check-auth headers name rollback target)))