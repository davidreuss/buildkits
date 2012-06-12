(ns buildkits.html
  (:require [net.cgrand.enlive-html :as h]))

(defn login-link [username]
  (if username
    (h/content "<a href='/logout'>Log out</a>")
    identity))

(defn buildpack-list [buildpacks kit]
  (h/clone-for [buildpack buildpacks]
               (h/content (:name buildpack))))

(h/deftemplate dashboard "index.html" [buildpacks username kit]
  [:p#login] (login-link username)
  [:ul#buildpacks :li] (buildpack-list buildpacks kit))
