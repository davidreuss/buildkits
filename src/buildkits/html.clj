(ns buildkits.html
  (:require [hiccup.page :refer [html5 include-css include-js]]))

(defn layout [body username]
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
     [:div.row
      [:div.offset3.span4
       [:h1
        [:a {:href "/"} "Build Kits"]]]
      [:div.span3
       (if username
         [:p "Logged in as " username "."]
         [:p [:a {:href (str "https://github.com/login/oauth/authorize?"
                             "client_id=4e138466e2422c3e0524")}
              "Log in"]])]]
     [:div.row
      [:div.span8.offset2 body]]]]))

(defn render [buildpack]
  [:div#buildpack
   [:h4 [:a {:href (:url buildpack)} (:name buildpack)]]
   [:p#desc (:description buildpack)]
   [:p#author (str "By " (:author buildpack))]
   [:p#license (str "Licensed under: " (:license buildpack))]])

(defn dashboard [buildpacks username]
  (layout [:div
           [:div#buildpacks (map render buildpacks)]
           (if username
             [:p.download [:a {:href (str "/buildkit/" username ".tgz")}
                           "Your kit"]])]
          username))
