(ns aqua.slowpoke
  (:require [clojure.tools.logging :as log]
            clojure.set
            aqua.mal-local
            aqua.mal-web)
  (:use aqua.db-utils))

(defmacro doseq-slowly [interval bindings & body]
  `(let [interval# ~interval]
     (doseq ~bindings
       (let [start# (System/currentTimeMillis)]
         ~@body
         (let [remaining# (- (+ start# interval#) (System/currentTimeMillis))]
           (if (> remaining# 0)
             (Thread/sleep remaining#)))))))

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
    (aqua.mal-web/fetch-anime-list-cb username response-callback)
    completion-promise))

(def ^:private anime-needing-update
  (str "SELECT a.animedb_id, a.title"
       "    FROM anime AS a"
       "      LEFT JOIN anime_details_update au"
       "        ON a.animedb_id = au.animedb_id AND"
       "           last_update > strftime('%s', 'now') - ?"
       "    WHERE last_update IS NULL"
       "    LIMIT 30"))

(defn- refresh-anime [data-source-rw data-source-ro]
  (let [anime-to-refresh (with-query data-source-ro rs
                                     anime-needing-update
                                     [(* 86400 15)]
                           (doall (resultset-seq rs)))]
    (if (seq anime-to-refresh)
      (doseq-slowly 8700 [{:keys [animedb_id title]} anime-to-refresh]
        (log/info "Fetching new anime details for" title)
        (if-let [details @(aqua.mal-web/fetch-anime-details animedb_id title)]
          (aqua.mal-local/store-anime-details data-source-rw animedb_id title details))))))

(defn ^:private already-existing-users [users]
  (str "SELECT username"
       "    FROM users"
       "    WHERE username IN ("
              (placeholders users)
       "    )"))

(defn- fetch-new-users [data-source-rw data-source-ro]
  (log/info "Fetching new user sample")
  (if-let [user-sample @(aqua.mal-web/fetch-active-users)]
    (let [existing-users (with-query data-source-ro rs
                                     (already-existing-users user-sample)
                                     user-sample
                           (doall (map :username (resultset-seq rs))))
          new-users (clojure.set/difference (set user-sample) (set existing-users))]
      (if (empty? new-users)
        (log/info "No new users found")
        (doseq-slowly 1200 [username new-users]
          (log/info "Fetching user data for" username)
          (if-let [mal-app-info @(aqua.mal-web/fetch-anime-list username)]
            (aqua.mal-local/store-user-anime-list data-source-rw username mal-app-info)))))))

(def ^:private old-inactive-budget-fraction 0.1)
(def ^:private old-inactive-budget-min 20)
(def ^:private users-max-age 10)
(def ^:private users-bucket-start 2)
(def ^:private users-bucket-exponent 1.1)
(def ^:private users-bucket-budget 10)
(def ^:private old-inactive-threshold (* 6 30 86400))

(def ^:private query-min-last-change
  (str "SELECT (strftime('%s', 'now') - MIN(last_change)) / 86400 AS min_change"
       "    FROM users"
       "    WHERE last_change > strftime('%s', 'now') - " old-inactive-threshold))

(def ^:private query-old-failed-update-bucket
  (str "SELECT username"
       "    FROM users"
       "    WHERE last_change < strftime('%s', 'now') - " old-inactive-threshold " AND"
       "          username <> ''"
       "    ORDER BY last_update ASC"
       "    LIMIT ?"))

(def ^:private query-user-update-bucket
  (str "SELECT username"
       "    FROM users"
       "    WHERE last_change >= strftime('%s', 'now') - 86400 * ? AND"
       "          last_change < strftime('%s', 'now') - 86400 * ? AND"
       "          last_update < strftime('%s', 'now') - 86400 * ? AND"
       "          username <> ''"
       "    ORDER BY last_update ASC"
       "    LIMIT ?"))

(defn- user-update-buckets [min-last-change]
  (take-while #(< % min-last-change)
    (drop 1 (map #(+ users-max-age
                 (Math/pow users-bucket-exponent %))
              (range)))))

(defn- users-needing-update [data-source-ro]
  (letfn [(fetch-user-bucket [bucket-start bucket-end]
            (with-query data-source-ro rs query-user-update-bucket
                        [bucket-start bucket-end users-max-age users-bucket-budget]
              (doall (map :username (resultset-seq rs)))))
          (fetch-old-users [users-count]
            (with-query data-source-ro rs query-old-failed-update-bucket
                        [(max old-inactive-budget-min (* users-count old-inactive-budget-fraction))]
              (doall (map :username (resultset-seq rs)))))]
    (let [min-last-change (with-query data-source-ro rs query-min-last-change []
                            (:min_change (first (resultset-seq rs))))
          update-buckets (user-update-buckets min-last-change)
          users (mapcat fetch-user-bucket (rest update-buckets) update-buckets)
          old-users (fetch-old-users (count users))]
        (concat old-users users))))

(defn refresh-users [data-source-rw data-source-ro]
  (log/info "Fetching users needing refresh")
  (let [users (users-needing-update data-source-ro)]
    (if (empty? users)
      (log/info "No users to refresh")
      (doseq-slowly 2300 [user users]
        (log/info "Refreshing user data for" user)
        (if-let [mal-app-info @(aqua.mal-web/fetch-anime-list user)]
          (aqua.mal-local/store-user-anime-list data-source-rw user mal-app-info))))))

(defn- clean-refresh-queue [data-source-rw]
  (with-connection data-source-rw connection
    (execute connection clean-expired-user-refresh [])))

(defn- process-refresh-queue [data-source-rw data-source-ro]
  (with-query data-source-ro rs select-next-user-refresh [queue-status-new queue-status-failed 120]
    (when (.next rs)
      (let [username (.getString rs 1)]
        (log/info "Interactively fetching user data for" username)
        (let [wait-completion (fetch-and-update-user-anime-list data-source-rw
                                                                username)]
          @wait-completion)))))

(defn- wrap-background-task [name task]
  (fn []
    (try
      (task)
      (catch Throwable t
        (log/warn t "Error in background task " name)))))

(defn make-process-refresh-queue [data-source-rw data-source-ro]
  (wrap-background-task
    "process-refresh-queue"
    (fn []
      (clean-refresh-queue data-source-rw)
      (process-refresh-queue data-source-rw data-source-ro))))

(defn make-refresh-anime [data-source-rw data-source-ro]
  (wrap-background-task
    "refresh-anime"
    (fn []
      (refresh-anime data-source-rw data-source-ro))))

(defn make-fetch-new-users [data-source-rw data-source-ro]
  (wrap-background-task
    "fetch-new-users"
    (fn []
      (fetch-new-users data-source-rw data-source-ro))))

(defn make-refresh-users [data-source-rw data-source-ro]
  (wrap-background-task
    "refresh-users"
    (fn []
      (refresh-users data-source-rw data-source-ro))))
