(ns aqua.web.globals-init
  ; no dependencies on aqua.* here
  (:require aqua.mal-local
            aqua.misc
            [aqua.web.globals :refer [*background
                                      *maldump-directory
                                      *state-directory
                                      *data-source-rw
                                      *data-source-ro
                                      *anime
                                      *cf-parameters]]
            [clojure.tools.logging :as log]))

(defn- reload-anime []
  (log/info "Start loading anime")
  (let [data-source @*data-source-ro
        anime (aqua.mal-local/load-anime data-source)]
    (reset! *anime anime))
  (log/info "Done loading anime"))

(defn init [directory state-directory]
  (reset! *background (java.util.concurrent.Executors/newScheduledThreadPool 7))
  (reset! *maldump-directory directory)
  (reset! *state-directory state-directory)
  (reset! *cf-parameters (aqua.misc/make-cf-parameters 0.5 -1))
  (let [data-source (aqua.mal-local/open-sqlite-rw directory "maldump.sqlite")]
    (reset! *data-source-rw data-source))
  (let [data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")]
    (reset! *data-source-ro data-source))
  (aqua.mal-local/setup-tables @*data-source-rw)
  (reload-anime))

(defn reload []
  (reload-anime))
