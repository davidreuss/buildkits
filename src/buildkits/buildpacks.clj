(ns buildkits.buildpacks
  (:refer-clojure :exclude [delete remove])
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [buildkits.db :as db]
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

(defn tar-dir [path target]
  (with-open [out (-> (FileOutputStream. target)
                      (BufferedOutputStream.)
                      (GzipCompressorOutputStream.)
                      (TarArchiveOutputStream.))]
    (try ; with-open swallows exceptions here
      (doseq [f (file-seq (io/file path))]
        (when-not (.isDirectory f)
          (let [relative (.replace (str f) path "")
                entry (TarArchiveEntry. relative)]
            (.setSize entry (.length f))
            (.putArchiveEntry out entry)
            (io/copy (io/input-stream f) out)
            (.closeArchiveEntry out))))
      (catch Exception e
        (.printStackTrace e)
        (throw e))))
  (io/file target))

(defn compose [name kit]
  (prn :compose name)
  ;; TODO: race conditions here of course
  (let [path (str work-dir "/" name)
        target (str path ".tgz")]
    (doseq [buildpack kit]
        (check-out buildpack path))
    (.mkdirs (io/file path "bin"))
    (doseq [script ["bin/detect" "bin/compile" "bin/release"]]
      (io/copy (.openStream (io/resource script)) (io/file path script)))
    (tar-dir path target)))

;; crud stuff

(defn add [session buildpack-name position])

(defn remove [session buildpack-name])

(defn create [session params])

(defn delete [session buildpack-name])