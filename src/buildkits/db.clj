(ns buildkits.db
  (:require [cemerick.friend.credentials :as creds]))

;; keep it in-memory for now
(def db (ref {:users {"technomancy" {:username "technomancy"
                                     :password (creds/hash-bcrypt "pass")}}
              :kits {"technomancy" ["clojure-lein2"
                                    "clojure"
                                    "java-openjdk7"]}
              :buildpacks {"ruby-rack" {}
                           "clojure" {}
                           "clojure-lein2" {}
                           "python" {}
                           "java" {}
                           "java-openjdk7" {}
                           "scala" {}
                           "play" {}
                           "nodejs" {}
                           "logo" {}}
              :ratings {"clojure" [5]}}))

(defn get-users []
  (:users @db))

(defn get-user [username]
  (get-in @db [:users username]))

(defn get-kit [username]
  (get-in @db [:kits username]))

(defn get-ratings [buildpack-name]
  (get-in @db [:ratings buildpack-name]))

(defn get-buildpack [buildpack-name]
  (-> (get-in @db [:buildpacks buildpack-name])
      (assoc :ratings (get-ratings buildpack-name))))
