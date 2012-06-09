(ns buildkits.kit
  (:refer-clojure :exclude [delete remove])
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [compojure.core :refer [defroutes GET PUT POST DELETE]]
            [buildkits.db :as db]
            [cheshire.core :as json]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io])
  (:import (java.io BufferedOutputStream BufferedInputStream
                    File FileOutputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveOutputStream
                                                      TarArchiveInputStream
                                                      TarArchiveEntry)
           (org.apache.commons.compress.compressors.gzip
            GzipCompressorOutputStream GzipCompressorInputStream)))

(def work-dir (or (System/getenv "WORK_DIR") (str (io/file "work"))))

(defn extract [{:keys [tarball name] :as buildpack} base-path]
  (let [path (str base-path "/buildpacks/" name)
        tar (-> tarball
                (BufferedInputStream.)
                (GzipCompressorInputStream.)
                (TarArchiveInputStream.))]
    (loop [entry (.getNextTarEntry tar)]
      (when entry
        (let [file (io/file base-path (.getName entry))]
          (.mkdirs (.getParentFile file))
          (when (.isFile entry)
            (io/copy tar file)
            (when (pos? (mod (.getMode entry) 0100))
              (.setExecutable file true))))
        (recur (.getNextTarEntry tar))))
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

(defn compose [name kit]
  (let [base-path (str work-dir "/" name)]
    (doseq [buildpack kit]
      (extract buildpack base-path))
    (.mkdirs (io/file base-path "bin"))
    (doseq [script ["bin/detect" "bin/compile" "bin/release"]]
      (io/copy (.openStream (io/resource script)) (io/file base-path script))
      (.setExecutable (io/file base-path script) true))
    (tgz-dir base-path (File/createTempFile name ".tgz"))))
