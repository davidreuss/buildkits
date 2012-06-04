(ns buildkits.buildpacks
  (:refer-clojure :exclude [delete remove])
  (:require [hiccup.page :refer [html5 include-css]]
            [buildkits.db :as db]))

(defn layout [body]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:title "Build Kits"]
    ;; include-css
    ]
   [:body
    [:div#header
     [:h1
      [:a {:href "/"} "Build Kits"]]]
    [:div#content body]]))

(defn rating [ratings]
  (float (/ (apply + ratings) (count ratings))))

(defn render [kit [buildpack-name buildpack]]
  ;; dummy stuff for now
  (let [buildpack {:url "https://github.com/heroku/heroku-buildpack-clojure"
                   :description "This buildpack is the greatest thing."
                   :license "MIT/X11"
                   :author "Phil Hagelberg"
                   :ratings [5 4 5]}]
    [:div#buildpack
     [:h5 [:a {:href (:url buildpack)} buildpack-name]]
     [:p#desc (:description buildpack)]
     [:p#author (str "By " (:author buildpack))]
     [:p#license (str "Licensed under: " (:license buildpack))]
     [:p#rating (format "Rating: %.2f" (rating (:ratings buildpack)))]]))

(defn dashboard [session]
  (let [kit (db/get-kit (:user session))]
    (layout [:div#buildpacks (map (partial render kit) (:buildpacks @db/db))])))

(defn add [session buildpack-name position]
  (dosync
   ))

(defn remove [session buildpack-name])

(defn create [session params])

(defn delete [session buildpack-name])