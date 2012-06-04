(ns buildkits.db
  (:require [cemerick.friend.credentials :as creds]
            [clojure.java.jdbc :as sql]
            [clojure.java.io :as io])
  (:import (org.openstreetmap.osmosis.hstore PGHStore)))

(def db (or (System/getenv "DATABASE_URL") "postgres://localhost:5432/buildkits"))

(defn hstore [m]
  (PGHStore. (if (vector? m)
               (zipmap (range (count m)) m)
               (zipmap (map name (keys m)) (vals m)))))

(defn unhstore [h]
  (into {} (for [[k v] h]
             [k (if (instance? PGHStore v)
                  (into {} v) v)])))

(defn get-buildpack [buildpack-name]
  (sql/with-query-results [b] ["select * from buildpacks where name = ?"
                               buildpack-name]
    (unhstore b)))

(defn get-kit [name]
  (sql/with-query-results [kit] ["select * from kits where name = ?" name]
    (let [packs (unhstore (:buildpacks kit))]
      (for [x (range (count (:buildpacks kit)))]
        (get-buildpack (get (:buildpacks kit) (str x)))))))

(defn migrate []
  (sql/with-connection db
    (apply sql/do-commands (.split (slurp (io/resource "schema.sql")) ";"))))

(defn insert-dummy-data []
  (sql/with-connection db
    (doseq [[name attributes] (read-string
                               (slurp (io/resource "buildpacks.clj")))]
      (sql/insert-values :buildpacks [:name :attributes]
                         [name (hstore attributes)]))
    (sql/insert-values :kits [:name :buildpacks]
                       ["jvm-fancy" (hstore ["clojure-lein2" "java-openjdk7"
                                             "java"])])))
