(ns aqua.web.background
  (:require [aqua.web.globals :refer [*background]]
            [clojure.tools.logging :as log]))

(defn schedule [function description delay interval]
  (let [runnable (reify Runnable
                   (run [this]
                     (try
                       (log/info "Starting background task" description)
                       (function)
                       (log/info "Completed background task" description)
                       (catch Throwable t
                         (log/warn t "Error in background task" description)))))]
    (.scheduleAtFixedRate @*background
                          runnable
                          delay
                          interval
                          java.util.concurrent.TimeUnit/SECONDS)))
