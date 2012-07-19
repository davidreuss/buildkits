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

(defn add-revision-id []
  (sql/do-commands (str "ALTER TABLE revisions ADD COLUMN id INTEGER"
                        " NOT NULL DEFAULT 1")))

(defn add-buildpacks-serial []
  (sql/do-commands "ALTER TABLE buildpacks ADD COLUMN id INTEGER")
  (sql/with-query-results packs ["SELECT name FROM buildpacks"]
    (doseq [[id pack] (map-indexed vector packs)]
      (sql/update-values "buildpacks" ["name = ?" (:name pack)] {:id id}))
    (sql/do-commands "CREATE SEQUENCE buildpacks_id_seq"
                     (str "ALTER TABLE buildpacks ALTER COLUMN id"
                          " SET DEFAULT nextval('buildpacks_id_seq')")
                     "ALTER TABLE buildpacks ALTER COLUMN id SET NOT NULL"
                     "ALTER SEQUENCE buildpacks_id_seq OWNED BY buildpacks.id")
    (sql/with-query-results _ ["SELECT setval('buildpacks_id_seq', ?)"
                               (count packs)])))

(defn add-orgs []
  (sql/create-table "organizations"
                    [:id :serial "PRIMARY KEY"]
                    [:name :varchar "NOT NULL"])
  (sql/create-table "memberships"
                    [:id :serial "PRIMARY KEY"]
                    [:email :varchar "NOT NULL"]
                    [:organization_id :integer "NOT NULL"])
  (sql/do-commands "ALTER TABLE buildpacks ADD COLUMN organization_id INTEGER")
  (let [heroku (sql/insert-values "organizations" [:name] ["heroku"])]
    (sql/with-query-results packs ["select id, attributes from buildpacks"]
      (doseq [{:keys [id attributes]} packs
              :let [owner (get attributes "owner")]]
        (if (re-find #"@heroku\.com" owner)
          (do (sql/insert-values "memberships" [:email :organization_id]
                                 [owner (:id heroku)])
              (sql/update-values "buildpacks" ["id = ?" id]
                                 {:organization_id (:id heroku)}))
          (throw (Exception. (str "Orphaned buildpack from " attributes)))))))
  (sql/do-commands (str "ALTER TABLE buildpacks ALTER COLUMN organization_id"
                        " SET NOT NULL")))

(defn add-revisions-published-by []
  (sql/do-commands "ALTER TABLE revisions ADD COLUMN published_by varchar")
  (sql/with-query-results
    revs [(str "SELECT revisions.id, revisions.buildpack_name,"
               " buildpacks.attributes FROM revisions, buildpacks"
               " WHERE revisions.buildpack_name = buildpacks.name")]
    (doseq [rev revs]
      (sql/update-values "revisions" ["id = ? and buildpack_name = ?"
                                      (:id rev) (:buildpack_name rev)]
                         {:published_by (get (:attributes rev) "owner")})))
  (sql/do-commands "ALTER TABLE revisions ALTER COLUMN published_by SET NOT NULL"))

(defn revisions-use-buildpack-id []
  (sql/do-commands "ALTER TABLE revisions ADD COLUMN buildpack_id INTEGER")
  (sql/with-query-results
    revs [(str "SELECT revisions.id AS num, revisions.buildpack_name, "
               " buildpacks.id FROM revisions, buildpacks"
               " WHERE revisions.buildpack_name = buildpacks.name")]
    (doseq [rev revs]
      (sql/update-values "revisions" ["id = ? and buildpack_name = ?"
                                      (:num rev) (:buildpack_name rev)]
                         {:buildpack_id (:id rev)})))
  (sql/do-commands "ALTER TABLE revisions ALTER COLUMN buildpack_id SET NOT NULL")
  (sql/do-commands "ALTER TABLE revisions DROP COLUMN buildpack_name"))

(defn kits-use-buildpack-id []
  (sql/do-commands "ALTER TABLE kits ADD COLUMN buildpack_id INTEGER")
  (sql/with-query-results
    kits [(str "SELECT kits.*, buildpacks.* FROM kits, buildpacks"
               " WHERE kits.buildpack_name = buildpacks.name")]
    (doseq [kit kits]
      (sql/update-values "kits" ["kit = ? and buildpack_name = ?"
                                 (:kit kit) (:buildpack_name kit)]
                         {:buildpack_id (:id kit)})))
  (sql/do-commands "ALTER TABLE kits ALTER COLUMN buildpack_id SET NOT NULL")
  (sql/do-commands "ALTER TABLE kits DROP COLUMN buildpack_name"))

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
           #'drop-tarball-column
           #'add-revision-id
           #'add-buildpacks-serial
           #'add-orgs
           #'add-revisions-published-by
           #'revisions-use-buildpack-id
           #'kits-use-buildpack-id))