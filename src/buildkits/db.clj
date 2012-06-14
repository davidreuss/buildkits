(ns buildkits.db
  (:refer-clojure :exclude [flatten])
  (:require [clojure.java.jdbc :as sql]
            [clojure.java.io :as io]
            [clojure.walk :as walk])
  (:import (org.openstreetmap.osmosis.hstore PGHStore)))

(def db (or (System/getenv "DATABASE_URL") "postgres://localhost:5432/buildkits"))

(defn hstore [m]
  (PGHStore. (zipmap (map name (keys m)) (vals m))))

(defn unhstore [h]
  (into {} (for [[k v] h]
             [k (if (instance? PGHStore v)
                  (walk/keywordize-keys (into {} v)) v)])))

(defn flatten [buildpack]
  (merge (dissoc buildpack :attributes)
         (:attributes buildpack)))

(defn get-buildpack [buildpack-name]
  (sql/with-query-results [b] ["select * from buildpacks where name = ?"
                               buildpack-name]
    (if b
      (flatten (unhstore b)))))

(defn get-buildpacks []
  (sql/with-query-results buildpacks ["select name, attributes from buildpacks"]
    (mapv (comp flatten unhstore) buildpacks)))

(defn get-kit [name]
  (if name
    (sql/with-query-results buildpacks
      [(str "select buildpacks.* from buildpacks, kits"
            " where kits.kit = ? AND "
            "buildpacks.name = kits.buildpack_name ORDER BY kits.position") name]
      (if (seq buildpacks)
        (mapv (comp flatten unhstore) buildpacks)))))

(defn add-to-kit [username buildpack position]
  (sql/insert-record :kits {:kit username
                            :buildpack_name buildpack
                            :position position}))

(def defaults ["clojure" "gradle" "grails" "java" "logo" "nodejs" "php"
               "play" "python" "ruby" "ruby_bamboo" "scala"])

(defn create-kit [name]
  (doseq [buildpack defaults]
    (add-to-kit name buildpack 0))
  (get-kit name))

(defn remove-from-kit [name buildpack]
  (sql/delete-rows :kits ["kit = ? and buildpack_name = ?" name buildpack]))

(defn create [username buildpack-name content]
  (sql/insert-record :buildpacks {:name buildpack-name
                                  :tarball content
                                  ;; TODO: design buildpack manifest
                                  :attributes (hstore {:owner username})}))

(defn update [buildpack-name content]
  (sql/update-values :buildpacks ["name = ?" buildpack-name]
                     {:tarball content}))

(defn migrate []
  (sql/with-connection db
    (apply sql/do-commands (.split (slurp (io/resource "schema.sql")) ";"))))
