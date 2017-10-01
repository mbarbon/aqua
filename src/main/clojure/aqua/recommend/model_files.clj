(ns aqua.recommend.model-files
  (:require clojure.java.io))

(defn open-model-write [path version]
  (let [tmp-path (str path ".tmp")
        tmp-handle (clojure.java.io/writer tmp-path)]
    (.write tmp-handle (str "aqua-" version "\n"))
    (aqua.recommend.TmpFile. path tmp-path tmp-handle)))

(defn commit-model-write [tmp-file]
  (.commit tmp-file))

(defn- read-version [in]
  (.mark in 256)
  (let [line (.readLine in)]
    (if (.startsWith line "aqua-")
      (Integer/valueOf (.substring line 5))
      (do (.reset in)
          1))))

(defn open-model-read [path max-version]
  (let [in (clojure.java.io/reader path)
        version (read-version in)]
    (if (> version max-version)
      (throw (Exception. (str "Model version " version
                              " greater than " max-version))))
    {:handle in :version version}))

(defn cast-symbol [ty sym]
  (vary-meta sym assoc :tag ty))

(defmacro with-open-model [path max-version handle version & body]
  `(let [model# (open-model-read ~path ~max-version)
         ~(cast-symbol 'java.io.BufferedReader handle) (:handle model#)
         ~version (:version model#)]
     (try
       ~@body
       (finally
         (.close ~(cast-symbol 'java.lang.AutoCloseable handle))))))

(defn check-model-version [path max-version]
  (with-open-model path max-version _ _))
