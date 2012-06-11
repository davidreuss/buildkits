(ns buildkits.buildpack
  (:require [buildkits.db :as db]
            [clojure.java.jdbc :as sql]))

;; for actions coming from the heroku-buildpacks plugin
(defn check-api-key [username key]
  (try (= username (.getEmail (.getUserInfo (com.heroku.api.HerokuAPI. key))))
       (catch com.heroku.api.exception.RequestFailedException _)))

(defn publish [username key buildpack-name buildpack]
  (if (check-api-key username key)
    (sql/with-connection db/db
      (if-let [pack (db/get-buildpack buildpack-name)]
        (if (= (:owner pack) username)
          (db/update name buildpack)
          {:status 403})
        (db/create username name buildpack))
      {:status 201})
    {:status 401}))