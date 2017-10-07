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

(defn fetch-user [username decompress]
  (let [last-user-update (aqua.mal-local/last-user-update @*data-source-ro username)]
    (if (> last-user-update (- (/ (System/currentTimeMillis) 1000) (* 3600 6)))
      (let [bytes (aqua.mal-local/load-user-anime-list @*data-source-ro username)]
        (let [stream (java.io.ByteArrayInputStream. bytes)]
          (if decompress
            [(java.util.zip.GZIPInputStream. stream) -1]
            [stream -1])))
      [nil (aqua.slowpoke/enqueue-user-refresh @*data-source-rw username)])))
