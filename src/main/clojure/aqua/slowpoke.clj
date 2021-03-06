(ns aqua.slowpoke
  (:require [clojure.tools.logging :as log]
            clojure.set
            aqua.scrape.pause-scrape-exception
            aqua.mal-local
            aqua.mal-images
            aqua.mal-web)
  (:use aqua.db-utils))

(def ^:private refresh-anime-interval 8700)
(def ^:private refresh-manga-interval 8600)
(def ^:private refresh-user-interval 23300)
(def ^:private fetch-new-user-interval 5900)
(def ^:private check-image-interval 5800)
(def ^:private throttled-interval 45250)
(def ^:private forbidden-interval 1230300)

(defmacro doseq-slowly [interval bindings & body]
  `(let [interval# ~interval]
     (doseq ~bindings
       (let [start# (System/currentTimeMillis)]
         ~@body
         (let [remaining# (- (+ start# interval#) (System/currentTimeMillis))]
           (if (> remaining# 0)
             (Thread/sleep remaining#)))))))

(defmacro with-web-result [args & body]
  `(try
     (when-let ~args
       ~@body)
     (catch java.util.concurrent.ExecutionException e#
       ; should probably just ignore all ExecutionExceptions
       (if-not (instance? java.util.concurrent.TimeoutException (.getCause e#))
         (throw e#)))))

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

(def ^:private get-queue-refresh
  (str "SELECT status_change"
       "    FROM user_refresh_queue"
       "    WHERE username = ? AND status IN (2, 3)"))

(defn last-user-refresh [data-source username]
  (with-query data-source rs get-queue-refresh [username]
    (if (.next rs)
      (.getInt rs 1)
      0)))

(defn- set-user-refresh-status [data-source username queue-status inc-attempts]
  (with-connection data-source connection
    (execute connection update-user-refresh [queue-status inc-attempts username])))

(defn- update-user-item-list-cb [kind data-source username store-items]
  (let [response-callback (fn [mal-app-info error status]
                            (try
                              (cond
                                error (do
                                        (log/info error "Error while downloading " kind " list for " username)
                                        queue-status-failed)
                                status (do
                                        (log/info "HTTP error " status " while downloading " kind " list for " username)
                                        queue-status-failed)
                                :else (do
                                        (store-items data-source username mal-app-info)
                                        queue-status-complete))
                              (catch Exception e (do
                                                   (log/info e "Error while downloading " kind " list for " username)
                                                   queue-status-failed))))]
    response-callback))

(defn- fetch-and-update-user-anime-list [data-source username]
  (let [response-callback (update-user-item-list-cb "anime" data-source username aqua.mal-local/store-user-anime-list)]
    (aqua.mal-web/fetch-anime-list-cb username response-callback)))

(defn- fetch-and-update-user-manga-list [data-source username]
  (let [response-callback (update-user-item-list-cb "manga" data-source username aqua.mal-local/store-user-manga-list)]
    (aqua.mal-web/fetch-manga-list-cb username response-callback)))

(defn- throttled []
  (log/warn "Throttled")
  (Thread/sleep throttled-interval))

(defn- forbidden []
  (log/warn "Forbidden")
  (Thread/sleep forbidden-interval))

(def ^:private anime-needing-update
  (str "SELECT a.animedb_id, a.title"
       "    FROM anime AS a"
       "      LEFT JOIN anime_details_update au"
       "        ON a.animedb_id = au.animedb_id"
       "    WHERE last_update IS NULL OR"
       "          last_update < strftime('%s', 'now') - ?"
       "    ORDER BY last_update"
       "    LIMIT 30"))

(defn- refresh-anime [data-source-rw data-source-ro]
  (let [anime-to-refresh (with-query data-source-ro rs
                                     anime-needing-update
                                     [(* 86400 15)]
                           (doall (resultset-seq rs)))]
    (if (seq anime-to-refresh)
      (doseq-slowly refresh-anime-interval [{:keys [animedb_id title]} anime-to-refresh]
        (log/info "Fetching new anime details for" title)
        (with-web-result [details @(aqua.mal-web/fetch-anime-details animedb_id title)]
          (cond
            (:snooze details) (aqua.scrape.pause-scrape-exception/pause-scrape "Throtting anime scrape after MAL error")
            (:throttle details) (throttled)
            (:forbid details) (forbidden)
            :del (aqua.mal-local/store-anime-details data-source-rw animedb_id title details)))))))

(def ^:private manga-needing-update
  (str "SELECT m.mangadb_id, m.title"
       "    FROM manga AS m"
       "      LEFT JOIN manga_details_update mu"
       "        ON m.mangadb_id = mu.mangadb_id"
       "    WHERE last_update IS NULL OR"
       "          last_update < strftime('%s', 'now') - ?"
       "    ORDER BY last_update"
       "    LIMIT 30"))

(defn- refresh-manga [data-source-rw data-source-ro]
  (let [manga-to-refresh (with-query data-source-ro rs
                                     manga-needing-update
                                     [(* 86400 15)]
                           (doall (resultset-seq rs)))]
    (if (seq manga-to-refresh)
      (doseq-slowly refresh-manga-interval [{:keys [mangadb_id title]} manga-to-refresh]
        (log/info "Fetching new manga details for" title)
        (with-web-result [details @(aqua.mal-web/fetch-manga-details mangadb_id title)]
          (cond
            (:snooze details) (aqua.scrape.pause-scrape-exception/pause-scrape "Throtting manga scrape after MAL error")
            (:throttle details) (throttled)
            (:forbidden details) (forbidden)
            :else (aqua.mal-local/store-manga-details data-source-rw mangadb_id title details)))))))

(defn ^:private already-existing-users [users]
  (str "SELECT username"
       "    FROM users"
       "    WHERE username IN ("
              (placeholders users)
       "    )"))

(defn- refresh-user [data-source-rw username]
  (with-web-result [{:keys [mal-app-info snooze throttle forbid]} @(aqua.mal-web/fetch-anime-list username)]
    (cond
      snooze (aqua.scrape.pause-scrape-exception/pause-scrape "Throtting user scrape after MAL error")
      throttle (throttled)
      forbid (forbidden)
      :else (aqua.mal-local/store-user-anime-list data-source-rw username mal-app-info)))
  (with-web-result [{:keys [mal-app-info snooze throttle forbid]} @(aqua.mal-web/fetch-manga-list username)]
    (cond
      snooze (aqua.scrape.pause-scrape-exception/pause-scrape "Throtting user scrape after MAL error")
      throttle (throttled)
      forbid (forbidden)
      :else (aqua.mal-local/store-user-manga-list data-source-rw username mal-app-info))))

(defn- fetch-new-users [data-source-rw data-source-ro]
  (log/info "Fetching new user sample")
  (with-web-result [{:keys [user-sample snooze throttle forbid]} @(aqua.mal-web/fetch-active-users)]
    (cond
      snooze (aqua.scrape.pause-scrape-exception/pause-scrape "Throtting user scrape after MAL error")
      throttle (throttled)
      forbid (forbidden)
      :else (let [existing-users (with-query data-source-ro rs
                                            (already-existing-users user-sample)
                                            user-sample
                                  (doall (map :username (resultset-seq rs))))
                  new-users (clojure.set/difference (set user-sample) (set existing-users))]
              (if (empty? new-users)
                (log/info "No new users found")
                (doseq-slowly fetch-new-user-interval [username new-users]
                  (log/info "Fetching user data for" username)
                  (refresh-user data-source-rw username)))))))

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
                        [(max old-inactive-budget-min (int (* users-count old-inactive-budget-fraction)))]
              (doall (map :username (resultset-seq rs)))))]
    (let [min-last-change (with-query data-source-ro rs query-min-last-change []
                            (or (:min_change (first (resultset-seq rs)))
                                0))
          update-buckets (user-update-buckets min-last-change)
          users (mapcat fetch-user-bucket (rest update-buckets) update-buckets)
          old-users (fetch-old-users (count users))]
        (concat old-users users))))

(defn refresh-users [data-source-rw data-source-ro]
  (log/info "Fetching users needing refresh")
  (let [users (users-needing-update data-source-ro)]
    (if (empty? users)
      (log/info "No users to refresh")
      (doseq-slowly refresh-user-interval [user users]
        (log/info "Refreshing user data for" user)
        (refresh-user data-source-rw user)))))

(def ^:private select-images-needing-update
  (str "SELECT a.image AS url, ic.etag AS etag, ic.expires AS expires FROM anime AS a"
       "    LEFT JOIN image_cache AS ic"
       "      ON ic.url = a.image"
       "    WHERE (ic.expires < strftime('%s', 'now') OR ic.expires IS NULL) AND ic.url != ''"
       " UNION ALL "
       "SELECT m.image AS url, ic.etag AS etag, ic.expires AS expires FROM manga AS m"
       "    LEFT JOIN image_cache AS ic"
       "      ON ic.url = m.image"
       "    WHERE (ic.expires < strftime('%s', 'now') OR ic.expires IS NULL) AND ic.url != ''"
       " ORDER BY expires"
       " LIMIT 30"))

(defn- images-needing-update [data-source-ro]
  (with-query data-source-ro rs select-images-needing-update []
    (doall (resultset-seq rs))))

(defn- fetch-cdn-image [url etag]
  (aqua.mal.Http/getCDN url etag 5000
    (fn [error status body response]
      (case status
        200 {:url     url
             :changed true
             :image   (com.google.common.io.ByteStreams/toByteArray body)
             :type    (.getHeader response "Content-Type")
             :etag    (.getHeader response "ETag")
             :expires (.getHeader response "Expires")}
        304 {:url     url
             :etag    etag
             :expires (.getHeader response "Expires")}
        404 {:url     url
             :missing true}
        (do
          (if error
            (log/warn (str "Error '" (.getMessage error) "' while fetching " url))
            (log/warn (str "HTTP error " status " while fetching " url)))
          {:url     url})))))

(defn refresh-images [data-source-rw data-source-ro directory]
  (log/info "Fetching images needing refresh")
  (let [images (images-needing-update data-source-ro)]
    (if (empty? images)
      (log/info "No images to refresh")
      (doseq-slowly check-image-interval [{:keys [url etag]} images]
        (log/info "Refreshing image data for" url)
        (with-web-result [image-data @(fetch-cdn-image url etag)]
          (aqua.mal-images/store-cached-image data-source-rw directory image-data))))))

(defn- clean-refresh-queue [data-source-rw]
  (with-connection data-source-rw connection
    (execute connection clean-expired-user-refresh [])))

(defn- merge-status [a b]
  (if (or (= a queue-status-failed) (= b queue-status-failed))
    queue-status-failed
    queue-status-complete))

(defn- process-refresh-queue [data-source-rw data-source-ro]
  (with-query data-source-ro rs select-next-user-refresh [queue-status-new queue-status-failed 120]
    (when (.next rs)
      (let [username (.getString rs 1)]
        (log/info "Interactively fetching user data for" username)
        (set-user-refresh-status data-source-rw username queue-status-processing 1)
        (let [wait-completion-anime (fetch-and-update-user-anime-list data-source-rw
                                                                      username)
              wait-completion-manga (fetch-and-update-user-manga-list data-source-rw
                                                                      username)
              status (merge-status @wait-completion-anime @wait-completion-manga)]
          (set-user-refresh-status data-source-rw username status 0))))))

(defn- wrap-background-task [name task]
  (fn []
    (try
      (task)
      (catch aqua.scrape.pause_scrape_exception e
        (log/warn (.getMessage e)))
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

(defn make-refresh-manga [data-source-rw data-source-ro]
  (wrap-background-task
    "refresh-manga"
    (fn []
      (refresh-manga data-source-rw data-source-ro))))

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

(defn make-refresh-images [data-source-rw data-source-ro directory]
  (wrap-background-task
    "refresh-images"
    (fn []
      (refresh-images data-source-rw data-source-ro (.getAbsoluteFile directory)))))
