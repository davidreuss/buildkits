(ns buildkits.html
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.form :refer [form-to submit-button]]))

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
         [:p "Logged in as " username ". "
          [:a {:href "/logout"} "Log out"] "."]
         [:p [:a {:href (str "https://api.heroku.com/oauth/authorize?"
                             "response_type=code&client_id="
                             (System/getenv "OAUTH_CLIENT_ID"))}
              "Log in"]])]]
     [:div.row
      [:div.span8.offset2 body]]]]))

(defn toggle [kit buildpack]
  [:div.toggle
   (if (some #(= (:name buildpack) (:name %)) kit)
     ;; TODO: support sorting
     (form-to [:delete (format "/kit/%s" (:name buildpack))]
              (submit-button "remove"))
     (form-to [:put (format "/kit/%s/%s" (:name buildpack) 0)]
              (submit-button "add")))])

(defn render [kit buildpack]
  [:div#buildpack
   (if kit (toggle kit buildpack))
   [:h4 [:a {:href (:url buildpack)} (:name buildpack)]]
   [:p#desc (:description buildpack)]
   [:p#author (str "By " (:author buildpack))]
   [:p#license (str "Licensed under: " (:license buildpack))]])

(defn dashboard [buildpacks username kit]
  (layout [:div
           [:div#buildpacks (map (partial render kit) buildpacks)]
           (if username
             [:p.download [:a {:href (str "/buildkit/" username ".tgz")}
                           "Your kit"]])]
          username))
