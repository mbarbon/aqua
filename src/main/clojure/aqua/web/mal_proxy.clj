(ns aqua.web.mal-proxy
  (:require aqua.mal-local
            aqua.slowpoke
            [aqua.web.globals :refer [*data-source-ro *data-source-rw *background]]))

(defn init []
  (.scheduleWithFixedDelay
    @*background
    (aqua.slowpoke/make-process-refresh-queue @*data-source-rw @*data-source-ro)
    1 5 java.util.concurrent.TimeUnit/SECONDS)
  (aqua.mal-local/setup-tables @*data-source-rw))

(defn fetch-user [username decompress]
  (let [last-user-update (aqua.mal-local/last-user-update @*data-source-ro username)]
    (println last-user-update (- (/ (System/currentTimeMillis) 1000) (* 3600 6)))
    (if (> last-user-update (- (/ (System/currentTimeMillis) 1000) (* 3600 6)))
      (let [bytes (aqua.mal-local/load-user-anime-list @*data-source-ro username)]
        (let [stream (java.io.ByteArrayInputStream. bytes)]
          (if decompress
            [(java.util.zip.GZIPInputStream. stream) -1]
            [stream -1])))
      [nil (aqua.slowpoke/enqueue-user-refresh @*data-source-rw username)])))
