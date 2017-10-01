(ns aqua.web.dump
  (:require aqua.mal-local
            [clojure.java.io :as io]
            [aqua.web.globals :refer [*maldump-directory
                                      *data-source-ro
                                      *data-source-rw]]))

(def ^:private model-names
  ["lfd-model" "lfd-model-airing" "lfd-user-model" "user-sample"])

(defn- to-page [key id-map]
  {key id-map
   "last_page" (if (seq id-map)
                 (apply max (keys id-map))
                 -1)})

(defn all-anime-ids [{after_id "after_id" limit "count"}]
  (to-page "anime" (aqua.mal-local/all-anime-ids @*data-source-ro after_id limit)))

(defn all-user-ids [{after_id "after_id" limit "count"}]
  (to-page "users" (aqua.mal-local/all-user-ids @*data-source-ro after_id limit)))

(defn changed-users [{users "users"}]
  (aqua.mal-local/select-changed-users @*data-source-ro users))

(defn changed-anime [{anime "anime"}]
  (aqua.mal-local/select-changed-anime @*data-source-ro anime))

(defn store-users [{users "users"}]
  (aqua.mal-local/store-users @*data-source-rw users))

(defn store-anime [{anime "anime"}]
  (aqua.mal-local/store-anime @*data-source-rw anime))

(defn upload-model [model-name body]
  (let [destdir (io/as-file @*maldump-directory)
        target-file (io/file @*maldump-directory (str model-name ".new"))
        temp-file (java.io.File/createTempFile "model-name" "temp" destdir)]
    (.deleteOnExit temp-file)
    (try
      (with-open [out (io/output-stream temp-file)]
        (com.google.common.io.ByteStreams/copy body out))
      (if-not (.renameTo temp-file target-file)
        (throw (Exception. (str "Error renaming " temp-file " to " target-file))))
      nil ; result
      (catch Exception e
        (.delete temp-file)))))

(defn commit-models []
  (doseq [model-name model-names]
    (let [temp-file (io/file @*maldump-directory (str model-name ".new"))
          target-file (io/file @*maldump-directory model-name)]
      (if-not (.renameTo temp-file target-file)
        (throw (Exception. (str "Error renaming " temp-file " to " target-file))))))
  nil) ; result
