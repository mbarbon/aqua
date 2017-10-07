(ns aqua.cli.slowpoke
  (:require [clojure.tools.logging :as log]
            aqua.mal-local
            aqua.slowpoke))

(defn- schedule [executor function interval]
  (.scheduleWithFixedDelay executor function 1 interval java.util.concurrent.TimeUnit/SECONDS))

(defn -main []
  (let [data-source-rw (aqua.mal-local/open-sqlite-rw "maldump" "maldump.sqlite")
        data-source-ro (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        scheduler (java.util.concurrent.Executors/newScheduledThreadPool 3)]
    (aqua.mal-local/setup-tables data-source-rw)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-anime data-source-rw data-source-ro)
              300)
    (schedule scheduler
              (aqua.slowpoke/make-refresh-users data-source-rw data-source-ro)
              300)
    (schedule scheduler
              (aqua.slowpoke/make-fetch-new-users data-source-rw data-source-ro)
              30)))
