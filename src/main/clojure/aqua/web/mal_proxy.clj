(ns aqua.web.mal-proxy
  (:require aqua.mal-local
            aqua.slowpoke
            [aqua.web.globals :refer [*data-source-ro *data-source-rw *background]]))

(defn- init-slowpoke []
  (letfn [(schedule [make-function interval]
            (let [start (int (+ 3 (* (Math/random) 5)))]
              (.scheduleWithFixedDelay @*background
                                       (make-function @*data-source-rw
                                                      @*data-source-ro)
                                       start
                                       interval
                                       java.util.concurrent.TimeUnit/SECONDS)))]
    (schedule aqua.slowpoke/make-refresh-anime 300)
    (schedule aqua.slowpoke/make-refresh-users 300)
    (schedule aqua.slowpoke/make-fetch-new-users 30)))

(defn init []
  (aqua.mal-local/setup-tables @*data-source-rw)
  (init-slowpoke)
  (.scheduleWithFixedDelay
    @*background
    (aqua.slowpoke/make-process-refresh-queue @*data-source-rw @*data-source-ro)
    1 5 java.util.concurrent.TimeUnit/SECONDS))

(def ^:private ct-pb "application/x-protobuf")
(def ^:private ct-json "application/json")
(def ^:private ce-gzip "gzip")
(def ^:private ce-id "identity")

(defn- pb-to-json [stream]
  (let [decompressed (java.util.zip.GZIPInputStream. stream)
        rated-list (aqua.mal.Serialize/readRatedProtobuf decompressed)
        byte-out (java.io.ByteArrayOutputStream.)]
    (aqua.mal.Serialize/writeRatedList byte-out rated-list)
    (java.io.ByteArrayInputStream. (.toByteArray byte-out))))

(defn fetch-user [username accepts-gzip accepts-protobuf]
  (let [last-user-update (aqua.mal-local/last-user-update @*data-source-ro username)]
    (if (> last-user-update (- (/ (System/currentTimeMillis) 1000) (* 3600 6)))
      (let [[bytes blob-type] (aqua.mal-local/load-user-anime-list @*data-source-ro username)
            stream (java.io.ByteArrayInputStream. bytes)]
        (cond
          (= 0 blob-type)
            (throw (Exception. "Due to refresh rules, the blob here must be Protobuf"))
          (and accepts-protobuf accepts-gzip)
            [stream ct-pb ce-gzip -1]
          accepts-protobuf
            [(java.util.zip.GZIPInputStream. stream) ct-pb ce-id -1]
          :else
            [(pb-to-json stream) ct-json ce-id -1]))
      [nil nil nil (aqua.slowpoke/enqueue-user-refresh @*data-source-rw username)])))
