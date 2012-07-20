(ns buildkits.html
  (:require [net.cgrand.enlive-html :as h]
            [environ.core :as env]))

(defn login-href []
  (str "https://api.heroku.com/oauth/authorize?"
       "response_type=code&client_id=" (env/env :oauth-client-id)))

(defn login-link [username]
  (if username
    (h/do-> (h/content "Log out")
            (h/set-attr "href" "/logout"))
    (h/set-attr "href" (login-href))))

(defn toggle-form [buildpack kit]
  (fn [form]
    (let [enabled? (some #(= (:name buildpack) (:name %)) kit)
          method (if enabled? "DELETE" "PUT")
          label (if enabled? "remove" "add")]
      (h/at form
            [[:input (h/attr= :name "_method")]] (h/set-attr "value" method)
            [[:input (h/attr= :type "submit")]] (h/set-attr "value" label)))))

(defn buildpack-list [buildpacks kit]
  (h/clone-for [buildpack buildpacks]
               (fn [li]
                 (h/at li
                       [:h4] (h/content (str (:org buildpack) "/" (:name buildpack)))
                       [:form] (if kit
                                 (h/do->
                                  (h/add-class "logged-in")
                                  (h/set-attr "action" (format "/buildkit/%s/%s/0"
                                                               (:org buildpack)
                                                               (:name buildpack)))
                                  (toggle-form buildpack kit)))
                       [:p.owner] (h/content (str "By " (:owner buildpack)))))))

(h/deftemplate dashboard "index.html" [buildpacks username kit]
  [:p#login :a] (login-link username)
  [:ul#buildpacks :li] (buildpack-list buildpacks kit)
  [:ul#buildpacks] (if username
                     (h/add-class "logged-in")
                     identity)
  [:li#yours] (if username
                identity
                (h/html-content (str "<a href=\"" (login-href) "\">Log in<a/> "
                                     "to customize your Build Kit.")))
  [:a#kit] (h/set-attr "href" (format "/buildkit/%s.tgz" username)))
