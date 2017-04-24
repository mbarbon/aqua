(ns aqua.web.globals
  (:require aqua.mal-local))

(def *data-source-rw (atom nil))
(def *data-source-ro (atom nil))
(def *users (atom nil))
(def *anime (atom nil))
(def *suggest (atom nil))
(def *background (atom nil))

(defn- reload-anime []
  (let [data-source @*data-source-ro
        anime (aqua.mal-local/load-anime data-source)]
    (reset! *anime anime)))

(defn init [directory]
  (reset! *background (java.util.concurrent.Executors/newScheduledThreadPool 5))
  (let [data-source (aqua.mal-local/open-sqlite-rw directory "maldump.sqlite")]
    (reset! *data-source-rw data-source))
  (let [data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")]
    (reset! *data-source-ro data-source))
  (reload-anime))
