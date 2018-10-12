(ns aqua.cli.slowpoke
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            aqua.mal-local
            aqua.paths
            aqua.slowpoke))

(defn- schedule [executor function interval]
  (.scheduleWithFixedDelay executor function 1 interval java.util.concurrent.TimeUnit/SECONDS))

(defn -main []
  (aqua.mal.Http/init)
  (let [data-source-rw (aqua.mal-local/open-sqlite-rw (aqua.paths/mal-db))
        data-source-ro (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        scheduler (java.util.concurrent.Executors/newScheduledThreadPool 5)]
    (aqua.mal-local/setup-tables data-source-rw)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-anime data-source-rw data-source-ro)
              300)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-manga data-source-rw data-source-ro)
              300)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-users data-source-rw data-source-ro)
              300)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-images data-source-rw data-source-ro (io/file (aqua.paths/images)))
              45)
    (schedule scheduler
              (aqua.slowpoke/make-fetch-new-users data-source-rw data-source-ro)
              30)))
