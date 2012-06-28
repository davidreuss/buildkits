(ns buildkits.buildpack
  (:require [buildkits.db :as db]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql])
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
  (let [s3 (RestS3Service. (AWSCredentials. (System/getenv "AWS_ACCESS_KEY")
                                            (System/getenv "AWS_SECRET_KEY")))
        bucket (or (System/getenv "AWS_BUCKET") "buildkits-dev")
        key (format "buildpacks/%s.tgz" buildpack-name)
        obj (doto (S3Object. key content)
              (.setAcl (AccessControlList/REST_CANNED_PUBLIC_READ)))]
    (.putObject s3 bucket obj)))

;; why is this not in clojure.java.io?
(defn get-bytes [file]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy file baos)
    (.delete file)
    (.toByteArray baos)))

(defn publish [username key buildpack-name buildpack]
  (if (check-api-key username key)
    (sql/with-connection db/db
      (if-let [pack (db/get-buildpack buildpack-name)]
        (if (= (:owner pack) username)
          (let [content (get-bytes (:tempfile buildpack))]
            (db/update buildpack-name content)
            (s3-put buildpack-name content)
            {:status 200})
          {:status 403})
        (let [content (get-bytes (:tempfile buildpack))]
            (db/create username buildpack-name content)
            (s3-put buildpack-name content)
            {:status 201})))
    {:status 401}))

(defn rollback [name]
  (sql/with-query-results [latest second] [(str "SELECT * FROM revisions "
                                                "WHERE buildpack_name = ? "
                                                "ORDER BY created_at DESC "
                                                "LIMIT 2")
                                           name]
    (if latest
      (do (sql/delete-rows :revisions ["buildpack_name = ? AND created_at = ?"
                                       name (:created_at latest)])
          (when second
            (s3-put name (:tarball second)))
          {:status 200})
      {:status 404})))

(defn attempt-rollback [username key buildpack-name]
  (if (check-api-key username key)
    (sql/with-connection db/db
      (if-let [pack (db/get-buildpack buildpack-name)]
        (if (= (:owner pack) username)
          (rollback (:name pack))
          {:status 403})
        {:status 404}))
    {:status 401}))