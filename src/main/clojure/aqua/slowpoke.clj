(ns aqua.slowpoke
  (:require [clojure.tools.logging :as log]
            aqua.mal-local
            aqua.mal-web)
  (:use aqua.db-utils))

(def ^:private queue-status-new 0)
(def ^:private queue-status-processing 1)
(def ^:private queue-status-failed 2)
(def ^:private queue-status-complete 3)

(def ^:private insert-user-refresh
  (str "INSERT OR IGNORE INTO user_refresh_queue"
       "        (username, insert_time, status, status_change, attempts)"
       "    VALUES ("
       "        ?, strftime('%s', 'now'), ?, strftime('%s', 'now'), 0"
       ")"))

(def ^:private update-user-refresh
  (str "UPDATE user_refresh_queue"
       "    SET status = ?,"
       "        status_change = strftime('%s', 'now'),"
       "        attempts = attempts + ?"
       "    WHERE username = ?"))

(def ^:private select-next-user-refresh
  (str "SELECT username"
       "    FROM user_refresh_queue"
       "    WHERE (status = ? OR"
       "           (status = ? AND status_change < strftime('%s', 'now') - ?)) AND"
       "          attempts < 3"
       "    ORDER BY insert_time ASC"
       "    LIMIT 1"))

(def ^:private clean-expired-user-refresh
  (str "DELETE"
       "    FROM user_refresh_queue"
       "    WHERE (status = 2 AND attempts = 3) OR"
       "          status = 3"))

(def ^:private get-queue-position
  (str "SELECT 1 + COUNT(*)"
       "    FROM user_refresh_queue"
       "    WHERE insert_time < ("
       "        SELECT insert_time FROM user_refresh_queue WHERE username = ?"
       "    )"))
  
(defn enqueue-user-refresh [data-source username]
  (with-connection data-source connection
    (execute connection insert-user-refresh [username queue-status-new]))
  (with-query data-source rs get-queue-position [username]
    (if (.next rs)
      (.getInt rs 1)
      1)))

(defn- set-user-refresh-status [data-source username queue-status inc-attempts]
  (with-connection data-source connection
    (execute connection update-user-refresh [queue-status inc-attempts username])))

(defn- fetch-and-update-user-anime-list [data-source username]
  (let [update-user (fn [queue-status inc-attempts]
                      (set-user-refresh-status data-source username queue-status inc-attempts))
        completion-promise (promise)
        resolve-promise (fn [] (deliver completion-promise nil))
        response-callback (fn [mal-app-info error]
                            (try
                              (if error
                                (do
                                  (log/info error "Error while downloading anime list for " username)
                                  (update-user queue-status-failed 0)
                                  (resolve-promise))
                                (do
                                  (aqua.mal-local/store-user-anime-list data-source username mal-app-info)
                                  (update-user queue-status-complete 0)
                                  (resolve-promise)))
                              (catch Exception e (do
                                                   (log/info e "Error while downloading anime list for " username)
                                                   (update-user queue-status-failed 0)
                                                   (resolve-promise)))))]
    (update-user queue-status-processing 1)
    (aqua.mal-web/fetch-anime-list username response-callback)
    completion-promise))

(defn- wrap-background-task [name task]
  (fn []
    (try
      (task)
      (catch Throwable t
        (log/warn t "Error in background task " name)))))

(defn- clean-refresh-queue [data-source-rw]
  (with-connection data-source-rw connection
    (execute connection clean-expired-user-refresh [])))

(defn- process-refresh-queue [data-source-rw data-source-ro]
  (with-query data-source-ro rs select-next-user-refresh [queue-status-new queue-status-failed 120]
    (when (.next rs)
      (let [username (.getString rs 1)]
        (log/info "Refreshing user data for" username)
        (let [wait-completion (fetch-and-update-user-anime-list data-source-rw
                                                                username)]
          @wait-completion)))))

(defn make-process-refresh-queue [data-source-rw data-source-ro]
  (wrap-background-task
    "process-refresh-queue"
    (fn []
      (clean-refresh-queue data-source-rw)
      (process-refresh-queue data-source-rw data-source-ro))))
