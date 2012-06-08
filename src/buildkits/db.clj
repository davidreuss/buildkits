(ns buildkits.db
  (:refer-clojure :exclude [flatten])
  (:require [cemerick.friend.credentials :as creds]
            [clojure.java.jdbc :as sql]
            [clojure.java.io :as io]
            [clojure.walk :as walk])
  (:import (org.openstreetmap.osmosis.hstore PGHStore)))

(def db (or (System/getenv "DATABASE_URL") "postgres://localhost:5432/buildkits"))

(defn hstore [m]
  (PGHStore. (if (vector? m)
               (zipmap (range (count m)) m)
               (zipmap (map name (keys m)) (vals m)))))

(defn unhstore [h]
  (into {} (for [[k v] h]
             [k (if (instance? PGHStore v)
                  (walk/keywordize-keys (into {} v)) v)])))

(defn flatten [buildpack]
  (assoc (:attributes buildpack) :name (:name buildpack)))

(defn get-buildpack [buildpack-name]
  (sql/with-query-results [b] ["select * from buildpacks where name = ?"
                               buildpack-name]
    (flatten (unhstore b))))

(defn get-buildpacks []
  (sql/with-query-results buildpacks ["select * from buildpacks"]
    (mapv (comp flatten unhstore) buildpacks)))

(defn get-kit [name]
  (if name
    (sql/with-query-results buildpacks
      [(str "select buildpacks.* from buildpacks, kits"
            " where kits.kit = ? AND "
            "buildpacks.name = kits.buildpack_name ORDER BY kits.position") name]
      (mapv (comp flatten unhstore) buildpacks))))

(defn add-to-kit [name buildpack position]
  (sql/insert-record :kits {:kit name :buildpack_name buildpack}))

(defn remove-from-kit [name buildpack]
  (sql/delete-rows :kits ["kit = ? and buildpack_name = ?" name buildpack]))

(defn migrate []
  (sql/with-connection db
    (apply sql/do-commands (.split (slurp (io/resource "schema.sql")) ";"))))

(defn insert-dummy-data [filename]
  (sql/with-connection db
    (doseq [[name attributes] (read-string (slurp filename))]
      (sql/insert-values :buildpacks [:name :attributes]
                         [name (hstore attributes)]))
    (doall (map-indexed #(sql/insert-values :kits [:kit :buildpack_name :position]
                                            ["jvm-fancy" %2 %1])
                        ["clojure-lein2" "java-openjdk7" "java"]))))
