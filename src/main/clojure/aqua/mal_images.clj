(ns aqua.mal-images
  (:require [clojure.java.io :as io])
  (:use aqua.db-utils))

(def ^:private insert-entry
  (str "INSERT OR REPLACE INTO image_cache"
       "        (url, expires, etag, cached_path, resized_path)"
       "    VALUES"
       "        (?, ?, ?, ?, ?)"))

(def ^:private update-entry
  (str "UPDATE image_cache"
       "    SET expires = ?"
       "    WHERE url = ?"))

(defn file-suffix [url content-type]
  (case content-type
    "image/jpeg" ".jpg"
    "image/png"  ".png"
    nil))

(defn- out-path [url content-type kind]
  (if-let [suffix (file-suffix url content-type)]
    (let [hash-string (.toString (.hashUnencodedChars (com.google.common.hash.Hashing/sha256) url))
          prefix1 (.substring hash-string 0 2)
          prefix2 (.substring hash-string 2 4)
          rest    (.substring hash-string 2)]
      [(io/file prefix1 prefix2 (str rest kind suffix))
       (io/file prefix1 prefix2 (str rest kind ".tmp"))])
    [nil nil]))

(defn- write-file [path tmp-path content]
  (.mkdirs (.getParentFile path))
  (with-open [out (io/output-stream tmp-path)]
    (.write out content))
  (.renameTo tmp-path path))

(defn store-cached-image [data-source-rw directory image-data]
  (let [changed (:changed image-data)
        [path tmp-path] (out-path (:url image-data) (:type image-data) "")
        [resized-path tmp-resized-path] (out-path (:url image-data) "image/jpeg" "-84x114")
        expires (if-let [expires-header (:expires image-data)]
                  (aqua.mal.Http/parseDate expires-header)
                  ; give it another day
                  (+ 86400 (/ 1000 (java.lang.System/currentTimeMillis))))]
    (when (and changed path)
      (write-file (io/file directory path) (io/file directory tmp-path) (:image image-data))
      (let [image (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (:image image-data)))
            target-size (com.mortennobel.imagescaling.DimensionConstrain/createMaxDimension 84 114)
            resample-op (doto (com.mortennobel.imagescaling.ResampleOp. target-size)
                          (.setFilter (com.mortennobel.imagescaling.ResampleFilters/getLanczos3Filter)))
            scaled (.filter resample-op image nil)]
        (javax.imageio.ImageIO/write scaled "jpeg" (io/file directory tmp-resized-path))
        (.renameTo (io/file directory tmp-resized-path) (io/file directory resized-path))))
    (with-connection data-source-rw conn
      (if-not path
        (execute conn update-entry [expires (:url image-data)])
        (execute conn insert-entry [(:url image-data) expires (:etag image-data) (str path) (str resized-path)])))))
