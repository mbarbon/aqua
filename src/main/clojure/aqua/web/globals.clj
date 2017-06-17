(ns aqua.web.globals
  (:require aqua.mal-local
            aqua.misc
            [clojure.tools.logging :as log]))

; this is in a separate, rarely modified file to avoid the globals
; being empty on reload

(def *maldump-directory (atom nil))
(def *data-source-rw (atom nil))
(def *data-source-ro (atom nil))
(def *users (atom nil))
(def *anime (atom nil))
(def *lfd-anime (atom nil))
(def *lfd-anime-airing (atom nil))
(def *lfd-users (atom nil))
(def *suggest (atom nil))
(def *background (atom nil))
(def *state-directory (atom nil))

(def cf-parameters (aqua.misc/make-cf-parameters 0.5 -1))

(defn- reload-anime []
  (log/info "Start loading anime")
  (let [data-source @*data-source-ro
        anime (aqua.mal-local/load-anime data-source)]
    (reset! *anime anime))
  (log/info "Done loading anime"))

(defn init [directory state-directory]
  (reset! *background (java.util.concurrent.Executors/newScheduledThreadPool 5))
  (reset! *maldump-directory directory)
  (reset! *state-directory state-directory)
  (let [data-source (aqua.mal-local/open-sqlite-rw directory "maldump.sqlite")]
    (reset! *data-source-rw data-source))
  (let [data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")]
    (reset! *data-source-ro data-source))
  (reload-anime))

(defn reload []
  (reload-anime))
