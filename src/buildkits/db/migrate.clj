(ns buildkits.db.migrate
  (:require [clojure.java.jdbc :as sql]
            [clojure.java.io :as io]
            [buildkits.db :as db])
  (:import (java.sql Timestamp)))

(defn hstore-extension []
  (try (sql/do-commands "CREATE EXTENSION hstore")
       ;; Another DB could have already created this extension.
       (catch Exception _)))

(defn initial-schema []
  (sql/create-table "buildpacks"
                    [:name :varchar "PRIMARY KEY"]
                    [:tarball :bytea "NOT NULL"]
                    [:attributes :hstore])
  (sql/create-table "kits"
                    [:kit :varchar "NOT NULL"]
                    [:buildpack_name :varchar "NOT NULL"]
                    [:position :integer "NOT NULL"]))

(defn add-revisions-table []
  (sql/create-table "revisions"
                    [:buildpack_name :varchar "NOT NULL"]
                    [:tarball :bytea "NOT NULL"]
                    [:created_at :timestamp
                     "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]))

(defn populate-revisions []
  (sql/with-query-results packs ["select * from buildpacks"]
    (doseq [{:keys [name tarball]} packs]
      (sql/with-query-results
        [{n :count}] ["select count(*) from revisions where buildpack_name = ?" name]
        (when (zero? n)
          (sql/insert-record :revisions
                             {:buildpack_name name
                              :tarball tarball
                              :created_at (Timestamp.
                                           (System/currentTimeMillis))}))))))

(defn drop-tarball-column []
  (sql/do-commands "ALTER TABLE buildpacks DROP COLUMN tarball"))

;; migrations mechanics

(defn run-and-record [migration]
  (println "Running migration:" (:name (meta migration)))
  (migration)
  (sql/insert-values "migrations" [:name :created_at]
                     [(str (:name (meta migration)))
                      (Timestamp. (System/currentTimeMillis))]))

(defn migrate [& migrations]
  (sql/with-connection db/db
    (try (sql/create-table "migrations"
                           [:name :varchar "NOT NULL"]
                           [:created_at :timestamp
                            "NOT NULL"  "DEFAULT CURRENT_TIMESTAMP"])
         (catch Exception _))
    (sql/transaction
     (let [has-run? (sql/with-query-results run ["SELECT name FROM migrations"]
                      (set (map :name run)))]
       (doseq [m migrations
               :when (not (has-run? (str (:name (meta m)))))]
         (run-and-record m))))))

(defn -main []
  (migrate #'hstore-extension
           #'initial-schema
           #'add-revisions-table
           #'populate-revisions
           #'drop-tarball-column))