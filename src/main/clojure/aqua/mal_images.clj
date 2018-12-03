(ns aqua.mal-images
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:use aqua.db-utils))

(def ^:private insert-entry
  (str "INSERT OR REPLACE INTO image_cache"
       "        (url, expires, etag, cached_path, resized_path, sizes)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?)"))

(def ^:private update-entry
  (str "UPDATE image_cache"
       "    SET expires = ?"
       "    WHERE url = ?"))

(defn file-suffix [url content-type]
  (case content-type
    "image/jpeg" ".jpg"
    "image/png"  ".png"
    "image/webp" ".webp"
    nil))

(defn- out-path [url content-type kind]
  (if-let [suffix (file-suffix url content-type)]
    (let [hash-string (.toString (.hashUnencodedChars (com.google.common.hash.Hashing/sha256) url))
          prefix1 (.substring hash-string 0 2)
          prefix2 (.substring hash-string 2 4)
          rest    (.substring hash-string 4)]
      [(io/file prefix1 prefix2 (str rest kind suffix))
       (io/file prefix1 prefix2 (str rest kind ".tmp"))])
    [nil nil]))

(defn- write-file [path tmp-path content]
  (.mkdirs (.getParentFile path))
  (with-open [out (io/output-stream tmp-path)]
    (.write out content))
  (.renameTo tmp-path path))

(defn- resize-image-to [image-data width height directory]
  (let [image (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (:image image-data)))
        [resized-path tmp-resized-path] (out-path (:url image-data) "image/jpeg" (format "-%dx%d" width height))
        target-size (com.mortennobel.imagescaling.DimensionConstrain/createMaxDimension width height)
        resample-op (doto (com.mortennobel.imagescaling.ResampleOp. target-size)
                      (.setFilter (com.mortennobel.imagescaling.ResampleFilters/getLanczos3Filter)))
        scaled (.filter resample-op image nil)]
    (javax.imageio.ImageIO/write scaled "jpeg" (io/file directory tmp-resized-path))
    (.renameTo (io/file directory tmp-resized-path) (io/file directory resized-path))))

(defn store-cached-image [data-source-rw directory image-data]
  (let [changed (:changed image-data)
        missing (:missing image-data)
        [path tmp-path] (out-path (:url image-data) (:type image-data) "")
        expires (if-let [expires-header (:expires image-data)]
                  (aqua.mal.Http/parseDate expires-header)
                  ; give it another day
                  (+ 86400 (long (/ (java.lang.System/currentTimeMillis) 1000))))]
    (when (and changed path)
      (write-file (io/file directory path) (io/file directory tmp-path) (:image image-data))
      (resize-image-to image-data 84 114 directory)
      (resize-image-to image-data 168 228 directory))
    (with-connection data-source-rw conn
      (cond
        missing (execute conn insert-entry [(:url image-data) expires "" "" "" ""])
        (not path) (execute conn update-entry [expires (:url image-data)])
        :else (execute conn insert-entry [(:url image-data) expires (:etag image-data) (str path) "" "84x114,168x228"])))))

(defn cover-size [image-record size]
  (if (string/includes? (:sizes image-record) size)
    (let [local-path (:cached_path image-record)
          dot-index (string/last-index-of local-path \.)]
      (str (subs local-path 0 dot-index) "-" size (subs local-path dot-index)))))
