(ns buildkits.html
  (:require [hiccup.page :refer [html5 include-css include-js]]))

(defn layout [body]
  (html5 {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:title "Build Kits"]
    (include-css "css/bootstrap-responsive.min.css")
    (include-css "css/bootstrap.min.css")
    (include-css "css/buildkits.css")
    (include-js "js/bootstrap.min.js")]
   [:body
    [:div.container
     [:div.offset3
      [:h1
       [:a {:href "/"} "Build Kits"]]]
     [:div.span8.offset2 body]]]))

(defn render [buildpack]
  [:div#buildpack
   [:h4 [:a {:href (:url buildpack)} (:name buildpack)]]
   [:p#desc (:description buildpack)]
   [:p#author (str "By " (:author buildpack))]
   [:p#license (str "Licensed under: " (:license buildpack))]])

(defn dashboard [buildpacks kit-name]
  (layout [:div
           [:div#buildpacks (map render buildpacks)]
           [:p.download [:a {:href (str "/buildkit/" kit-name ".tgz")}
                         "Your kit"]]]))
