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
(defn get-bytes [file]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy file baos)
    (.delete file)
    (.toByteArray baos)))

(defn create [content username buildpack-name]
  (let [bytes (get-bytes content)]
    (db/create username buildpack-name bytes)
    (s3-put buildpack-name bytes)
    {:status 201 :body (json/encode {:revision 0})}))

(defn update [content username buildpack]
  (let [bytes (get-bytes content)
        rev-id (db/update (:name buildpack) bytes)]
    (s3-put (:name buildpack) bytes)
    {:status 200 :body (json/encode {:revision rev-id})}))

(defn rollback [username buildpack]
  (sql/with-query-results [latest second] [(str "SELECT * FROM revisions "
                                                "WHERE buildpack_name = ? "
                                                "ORDER BY created_at DESC "
                                                "LIMIT ?")
                                           (:name buildpack) 2]
    (if latest
      (do (sql/delete-rows :revisions ["buildpack_name = ? AND created_at = ?"
                                       (:name buildpack) (:created_at latest)])
          (when second
            (s3-put (:name buildpack) (:tarball second)))
          {:status 200 :body (json/encode {:revision (:id second)})})
      {:status 404})))

(defn revisions [username buildpack]
  (sql/with-query-results revs [(str "SELECT id, created_at from revisions"
                                     " WHERE buildpack_name = ? ORDER BY id")
                                (:name buildpack)]
    {:status 200 :body (json/encode revs)}))

(defn check-auth [headers buildpack-name found & [not-found]]
  (let [[username key] (-> (get headers "authorization") (.split " ") second
                           .getBytes base64/decode String. (.split ":"))]
    (if (check-api-key username key)
      (sql/with-connection db/db
        (if-let [pack (db/get-buildpack buildpack-name)]
          (if (= (:owner pack) username)
            (found username pack)
            (if not-found
              (not-found )))
          {:status 404}))
      {:status 401})))

(defroutes app
  (GET "/buildpacks" []
       {:status 200 :headers {"content-type" "application/json"}
        :body (sql/with-connection db/db
                (json/encode (db/get-buildpacks)))})
  (POST "/buildpacks/:name" {{:keys [name buildpack]} :params headers :headers}
        (check-auth headers name
                    (partial update (:tempfile buildpack))
                    (partial create (:tempfile buildpack))))
  (DELETE "/buildpacks/:name" {{:keys [name]} :params headers :headers}
          (check-auth headers name rollback))
  (GET "/buildpacks/:name/revisions" {{:keys [name]} :params headers :headers}
       (check-auth headers name revisions)))