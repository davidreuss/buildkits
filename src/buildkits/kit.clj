(ns buildkits.kit
  (:refer-clojure :exclude [delete remove])
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [compojure.core :refer [defroutes GET PUT POST DELETE]]
            [buildkits.db :as db]
            [cheshire.core :as json]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io])
  (:import (java.io File BufferedOutputStream FileOutputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveOutputStream
                                                      TarArchiveEntry)
           (org.apache.commons.compress.compressors.gzip GzipCompressorOutputStream)))

(def work-dir (or (System/getenv "WORK_DIR") (str (io/file "work"))))

(defn check-out [{:keys [git name] :as buildpack} base-path]
  (.mkdirs (io/file base-path))
  (let [path (str base-path "/buildpacks/" name)
        [git-url branch] (.split git "#")
        res (sh/sh "git" "clone" git-url path)]
    (when branch
      (sh/sh "git" "checkout" branch :dir path))
    (when (pos? (:exit res))
      (throw (Exception. (:err res))))
    path))

(defn tgz-dir [path target]
  (with-open [out (-> (FileOutputStream. target)
                      (BufferedOutputStream.)
                      (GzipCompressorOutputStream.)
                      (TarArchiveOutputStream.))]
    (try ; with-open swallows exceptions here
      (doseq [f (file-seq (io/file path))]
        (when-not (.isDirectory f)
          (let [relative (.replace (str f) (str path) "")
                entry (TarArchiveEntry. relative)]
            (.setSize entry (.length f))
            (when (.canExecute f)
              (.setMode entry 0755))
            (.putArchiveEntry out entry)
            (io/copy (io/input-stream f) out)
            (.closeArchiveEntry out))))
      (catch Exception e
        (.printStackTrace e)
        (throw e))))
  (io/file target))

(defn compose [name kit target]
  (let [path (str work-dir "/" name)]
    (doseq [buildpack kit]
      (check-out buildpack path))
    (.mkdirs (io/file path "bin"))
    (doseq [script ["bin/detect" "bin/compile" "bin/release"]]
      (io/copy (.openStream (io/resource script)) (io/file path script))
      (.setExecutable (io/file path script) true))
    (tgz-dir path target)))

(defn composed-kit [name kit]
  ;; TODO: Still a race condition here
  (let [file (io/file work-dir (str name ".tgz"))]
    (if (.exists file)
      file
      (compose name kit file))))
