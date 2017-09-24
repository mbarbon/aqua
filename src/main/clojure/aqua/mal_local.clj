(ns aqua.mal-local
  (:require [clojure.java.io :as io])
  (:import (aqua.mal Json)))

(defn open-sqlite-rw [directory file]
  (let [data-source (org.sqlite.SQLiteDataSource.)]
    (.setUrl data-source (str "jdbc:sqlite:" (io/file directory file)))
    (.setReadOnly data-source false)
    (.setJournalMode data-source "WAL")
    data-source))

(defn open-sqlite-ro [directory file]
  (let [data-source (org.sqlite.SQLiteDataSource.)]
    (.setUrl data-source (str "jdbc:sqlite:" (io/file directory file)))
    (.setReadOnly data-source true)
    data-source))

(def ^:private create-refresh-queue
  (str "CREATE TABLE IF NOT EXISTS user_refresh_queue ("
       "    username VARCHAR(20) PRIMARY KEY,"
       "    insert_time INTEGER NOT NULL,"
       "    status INTEGER NOT NULL,"
       "    attempts INTEGER NOT NULL,"
       "    status_change INTEGER NOT NULL"
       ")"))

(defn setup-tables [data-source]
  (with-open [connection (.getConnection data-source)]
    (let [run-create (fn [statement]
                       (with-open [statement (.prepareStatement connection statement)]
                         (.execute statement)))]
      (run-create create-refresh-queue))))

(defn- doall-rs [^java.sql.ResultSet rs func]
  (let [result (java.util.ArrayList.)]
    (while (.next rs)
      (.add result (func rs)))
    result))

(def ^:private select-users
  "SELECT u.user_id AS user_id, u.username AS username, al.anime_list AS anime_list FROM users AS u INNER JOIN anime_list AS al ON u.user_id = al.user_id WHERE u.user_id IN ")

(defn- select-test-user-ids [skip-ids]
  (str "SELECT u.user_id"
       "    FROM users AS u"
       "      INNER JOIN user_anime_stats AS uas"
       "        ON uas.user_id = u.user_id"
       "    WHERE completed > 10 AND"
       "          completed < 100 AND"
       "          username AND"
       "          u.user_id NOT IN ("
                  (clojure.string/join "," skip-ids)
       "          )"
       "    LIMIT ?"))

(defn- load-users-from-rs [^java.sql.ResultSet rs]
  (let [user (aqua.mal.data.User.)
        al-data (java.util.zip.GZIPInputStream.
                  (io/input-stream (.getBinaryStream rs 3)))]
    (set! (.userId user) (.getInt rs 1))
    (set! (.username user) (.getString rs 2))
    (.setAnimeList user (Json/readRatedList al-data))
    user))

(defn- load-cf-users-from-rs [cf-parameters ^java.sql.ResultSet rs]
  (let [user (aqua.recommend.CFUser.)
        al-data (java.util.zip.GZIPInputStream.
                  (io/input-stream (.getBinaryStream rs 3)))]
    (set! (.userId user) (.getInt rs 1))
    (set! (.username user) (.getString rs 2))
    (.setAnimeList user cf-parameters (Json/readCFRatedList al-data))
    user))

(defn- load-filtered-cf-users-from-rs [cf-parameters
                                       ^java.util.Map cache
                                       ^java.util.List target
                                       ^java.util.Map anime-map-to-filter-hentai
                                       ^java.sql.ResultSet rs]
  (let [user (aqua.recommend.CFUser.)
        al-data (java.util.zip.GZIPInputStream.
                  (io/input-stream (.getBinaryStream rs 3)))
        anime-list (Json/readCFRatedList al-data)]
    (dotimes [n (.size anime-list)]
      (let [^aqua.recommend.CFRated item (.get anime-list n)
            cached (if-let [existing (.get cache item)]
                     existing
                     (do
                       (if (and anime-map-to-filter-hentai
                                (.isHentai (anime-map-to-filter-hentai (.animedbId item))))
                         (.setHentai item))
                       (.put cache item item)
                       item))]
        (.set anime-list n cached)))
    (set! (.userId user) (.getInt rs 1))
    (set! (.username user) (.getString rs 2))
    (.setFilteredAnimeList user cf-parameters anime-list)
    (.set target (- (.getRow rs) 1) user)
    ; return nil to avoid retaining the previous value in the
    ; result list
    nil))

(defn- select-users-by-id [connection ids loader]
  (let [query (str select-users "(" (clojure.string/join "," ids) ")")]
    (with-open [statement (doto (.prepareStatement connection query
                                                   java.sql.ResultSet/TYPE_FORWARD_ONLY
                                                   java.sql.ResultSet/CONCUR_READ_ONLY)
                              (.setFetchSize 1000))
                rs (.executeQuery statement)]
      (doall-rs rs loader))))

(defn load-sampled-user-ids [directory size]
  (with-open [in (io/reader (io/file directory "user-sample"))]
    (doall (take size (for [line (line-seq in)]
                        (Integer/valueOf line))))))

(defn- selected-cf-user-ids [connection max-count query]
  (with-open [statement (doto (.prepareStatement connection query
                                                 java.sql.ResultSet/TYPE_FORWARD_ONLY
                                                 java.sql.ResultSet/CONCUR_READ_ONLY)
                              (.setInt 1 max-count)
                              (.setFetchSize 1000))
              rs (.executeQuery statement)]
    (let [ids (doall-rs rs (fn [^java.sql.ResultSet rs] (.getInt rs 1)))]
      ids)))

(defn load-cf-users-by-id [data-source cf-parameters ids]
  (with-open [connection (.getConnection data-source)]
    (select-users-by-id connection ids
                        (partial load-cf-users-from-rs cf-parameters))))

(defn load-test-cf-user-ids [directory data-source max-count]
  (with-open [connection (.getConnection data-source)]
    (let [query (select-test-user-ids (load-sampled-user-ids directory 1000000))]
      (selected-cf-user-ids connection max-count query))))

; the only purpose of this function is to avoid doubling memory usage
; while users are reloaded: old users become garbage while new users are loaded
(defn load-filtered-cf-users-into [directory data-source cf-parameters cache target anime-map-to-filter-hentai]
  (with-open [connection (.getConnection data-source)]
    ; this allocates and throws away an ArrayList, it's fine
    (select-users-by-id connection
                        (load-sampled-user-ids directory (count target))
                        (partial load-filtered-cf-users-from-rs cf-parameters cache target anime-map-to-filter-hentai))
    target))

(defn- load-filtered-cf-users-helper [directory data-source cf-parameters max-count anime-map-to-filter-hentai]
  (let [cache (java.util.HashMap.)
        target (java.util.ArrayList. (repeat max-count nil))]
    (load-filtered-cf-users-into directory data-source cf-parameters cache target anime-map-to-filter-hentai)))

(defn load-filtered-cf-users
  ([directory data-source cf-parameters max-count]
    (load-filtered-cf-users-helper directory data-source cf-parameters max-count nil))
  ([directory data-source cf-parameters max-count anime-map-to-filter-hentai]
    (load-filtered-cf-users-helper directory data-source cf-parameters max-count anime-map-to-filter-hentai)))

(def ^:private select-user
  "SELECT u.user_id AS user_id, u.username AS username, al.anime_list AS anime_list FROM users AS u INNER JOIN anime_list AS al ON u.user_id = al.user_id WHERE u.username = ?")

(defn- load-user-from-connection [connection username]
  (with-open [statement (doto (.prepareStatement connection select-user)
                              (.setString 1 username))
              rs (.executeQuery statement)]
    (first (doall-rs rs (partial load-users-from-rs)))))

(defn load-cf-user [data-source username cf-parameters]
  (with-open [connection (.getConnection data-source)
              statement (doto (.prepareStatement connection select-user)
                              (.setString 1 username))
              rs (.executeQuery statement)]
    (first (doall-rs rs (partial load-cf-users-from-rs cf-parameters)))))

(def ^:private select-user-blob
  "SELECT LENGTH(al.anime_list) AS blob_length, al.anime_list AS anime_list FROM users AS u INNER JOIN anime_list AS al ON u.user_id = al.user_id WHERE u.username = ?")

(defn load-user-anime-list [data-source username]
  (with-open [connection (.getConnection data-source)
              statement (doto (.prepareStatement connection select-user-blob)
                              (.setString 1 username))
              rs (.executeQuery statement)]
    (if (.next rs)
      (let [byte-size (.getInt rs 1)
            is (.getBinaryStream rs 2)
            bytes (make-array Byte/TYPE byte-size)]
        (.read is bytes)
        bytes))))

; relations should be reflexive, but not all of them are in the scraped DB
; (e.g. the page failed to parse)
(def ^:private select-relations
  "SELECT animedb_id, related_id FROM anime_relations UNION SELECT related_id, animedb_id FROM anime_relations")

(defn- load-relation-map [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-relations)]
    (into {}
      (for [item (resultset-seq rs)]
        (let [left-id (:animedb_id item)
              right-id (:related_id item)]
          (if (> left-id right-id)
            [left-id right-id]
            [right-id left-id]))))))

(defn- transitive-map-closure [relation-map]
  (let [additional-relations (into {}
                               (remove empty?
                                 (for [[left-id right-id] relation-map]
                                   (let [left-child (get relation-map left-id Long/MAX_VALUE)
                                         right-child (get relation-map right-id Long/MAX_VALUE)
                                         root-id (min left-id left-child right-child)]
                                     (if (< root-id right-id)
                                       [left-id root-id])))))]
    (if-not (empty? additional-relations)
      (transitive-map-closure (conj relation-map additional-relations))
      relation-map)))

(def ^:private select-genre-names
  "SELECT genre, description FROM anime_genre_names")

(defn- load-genre-names [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-genre-names)]
    (into {}
      (for [item (resultset-seq rs)]
        [(:genre item) (:description item)]))))

(def ^:private select-anime-genres
  "SELECT animedb_id, genre_id, sort_order FROM anime_genres ORDER BY animedb_id, sort_order")

(defn- load-genres-map [data-source]
  (let [genre-names (load-genre-names data-source)
        genres-map (java.util.HashMap.)]
    (with-open [connection (.getConnection data-source)
                statement (.createStatement connection)
                rs (.executeQuery statement select-anime-genres)]
      (doseq [item (resultset-seq rs)]
        (let [anime-id (:animedb_id item)
              is-first (= 0 (:sort_order item))
              genres-list (if is-first
                            (let [genres-list (java.util.ArrayList.)]
                              (.put genres-map anime-id genres-list)
                              genres-list)
                            (.get genres-map anime-id))]
          (.add genres-list (genre-names (:genre_id item)))))
      genres-map)))

(def ^:private select-non-hentai-anime-titles
  (str "SELECT a.animedb_id AS animedb_id, a.title AS title"
       "    FROM anime AS a"
       "      LEFT JOIN anime_genres AS ag"
       "        ON a.animedb_id = ag.animedb_id AND"
       "           genre_id = 12"
       "    WHERE sort_order IS NULL"
       " UNION "
       "SELECT at.animedb_id AS animedb_id, at.title AS title"
       "    FROM anime_titles AS at"
       "      LEFT JOIN anime_genres AS ag"
       "        ON at.animedb_id = ag.animedb_id AND"
       "           genre_id = 12"
       "    WHERE sort_order IS NULL"))

(defn load-non-hentai-anime-titles [data-source]
    (with-open [connection (.getConnection data-source)
                statement (.createStatement connection)
                rs (.executeQuery statement select-non-hentai-anime-titles)]
      (doall
        (for [item (resultset-seq rs)]
          [(:animedb_id item) (:title item)]))))

(def ^:private select-anime-rank
  "SELECT animedb_id, rank FROM anime_details")

(defn load-anime-rank [data-source]
    (with-open [connection (.getConnection data-source)
                statement (.createStatement connection)
                rs (.executeQuery statement select-anime-rank)]
      (into {}
        (doall
          (for [item (resultset-seq rs)]
            [(:animedb_id item) (:rank item)])))))

(def ^:private select-hentai-anime-ids
  (str "SELECT a.animedb_id AS animedb_id"
       "    FROM anime AS a"
       "      INNER JOIN anime_genres AS ag"
       "        ON a.animedb_id = ag.animedb_id AND"
       "           genre_id = 12"))

(defn- load-hentai-anime-ids [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-hentai-anime-ids)]
    (doall
      (for [item (resultset-seq rs)]
        (:animedb_id item)))))

(def ^:private select-anime
  "SELECT animedb_id, title, status, episodes, start, end, image FROM anime")

(defn- load-anime-list [data-source]
  (let [hentai-id-set (set (load-hentai-anime-ids data-source))
        relation-map (transitive-map-closure (load-relation-map data-source))
        genres-map (load-genres-map data-source)
        franchise-map (java.util.HashMap.)]
    (doseq [franchise-id (.values relation-map)]
      (if-let [franchise (.get franchise-map franchise-id)]
        nil
        (.put franchise-map franchise-id (aqua.mal.data.Franchise. franchise-id))))
    (with-open [connection (.getConnection data-source)
                statement (.createStatement connection)
                rs (.executeQuery statement select-anime)]
      (doall
        (for [item (resultset-seq rs)]
          (let [animedb-id (:animedb_id item)
                anime (aqua.mal.data.Anime.)]
            (set! (.animedbId anime) animedb-id)
            (set! (.status anime) (:status item))
            (set! (.title anime) (:title item))
            (set! (.image anime) (:image item))
            (set! (.episodes anime) (:episodes item))
            (set! (.startedAiring anime) (if-let [start (:start item)] start 0))
            (set! (.endedAiring anime) (if-let [end (:end item)] end 0))
            (set! (.genres anime) (.get genres-map animedb-id))
            (set! (.isHentai anime) (.contains hentai-id-set animedb-id))
            (if-let [franchise (.get franchise-map animedb-id)]
              (do
                (.add (.anime franchise) anime)
                (set! (.franchise anime) franchise)))
            (if-let [franchise-id (relation-map animedb-id)]
              (let [franchise (.get franchise-map franchise-id)]
                (set! (.franchise anime) franchise)
                (.add (.anime franchise) anime)))
            anime))))))

(defn load-anime [data-source]
  (into {}
    (for [anime (load-anime-list data-source)]
      [(.animedbId anime) anime])))

(def ^:private select-last-update
  (str "SELECT last_update FROM users where username = ?"))

(defn last-user-update [data-source username]
  (with-open [connection (.getConnection data-source)
              statement (doto (.prepareStatement connection select-last-update)
                              (.setString 1 username))
              rs (.executeQuery statement)]
    (if (.next rs)
      (.getInt rs 1)
      0)))

;
; writing
;

(def ^:private update-user-timestamp
  (str "UPDATE users"
       "    SET last_update = STRFTIME('%s', 'now'),"
       "        last_change = 1"
       "    WHERE username = ?"))

(defn- mark-user-updated [connection username]
  (with-open [statement (doto (.prepareStatement connection update-user-timestamp)
                              (.setString 1 username))]
    (.execute statement)))

(def ^:private update-changed-user
  (str "INSERT OR REPLACE INTO users"
       "        (user_id, username, last_update, last_change)"
       "    VALUES (?, ?, STRFTIME('%s', 'now'), STRFTIME('%s', 'now'))"))

(def ^:private update-unchanged-user
  (str "INSERT OR REPLACE INTO users"
       "        (user_id, username, last_update, last_change)"
       "    VALUES (?, ?, STRFTIME('%s', 'now'),"
       "            (SELECT last_change FROM users WHERE user_id = ?))"))

(def ^:private select-anime-prefix
  (str "SELECT animedb_id, title, type, episodes, status, STRFTIME('%Y-%m-%d', start, 'unixepoch'), STRFTIME('%Y-%m-%d', end, 'unixepoch'), image"
       "    FROM anime"
       "    WHERE animedb_id IN"))

(def ^:private insert-anime
  (str "INSERT OR REPLACE INTO anime"
       "        (animedb_id, title, type, episodes, status, start, end, image)"
       "    VALUES (?, ?, ?, ?, ?, STRFTIME('%s', ?), STRFTIME('%s', ?), ?)"))

(defn- adjust-date [date-string]
  (cond
    (= "0000-00-00" date-string) nil
    (.endsWith date-string "-00") (str (subs date-string 0 7) "-01")
    :else date-string))

(defn- anime-equals [anime rs]
  (and (= (.getString rs 2) (.title anime))
       (= (.getInt rs 3) (.seriesType anime))
       (= (.getInt rs 4) (.episodes anime))
       (= (.getInt rs 5) (.seriesStatus anime))
       (= (.getString rs 6) (adjust-date (.start anime)))
       (= (.getString rs 7) (adjust-date (.end anime)))
       (= (.getString rs 8) (.image anime))))

(defn- insert-or-update-anime [connection mal-app-info-anime]
  (let [anime-map (java.util.HashMap. (into {} (for [anime mal-app-info-anime]
                                        [(.animedbId anime) anime])))
        id-csv (clojure.string/join "," (keys anime-map))
        select-anime (str select-anime-prefix " (" id-csv ")")]
    (with-open [statement (.createStatement connection)
                rs (.executeQuery statement select-anime)]
      (while (.next rs)
        (let [animedb-id (.getInt rs 1)
              anime (.get anime-map animedb-id)]
          (if (anime-equals anime rs)
            (.remove anime-map animedb-id)))))

    (with-open [statement (.prepareStatement connection insert-anime)]
      (doseq [anime (vals anime-map)]
        (doto statement
          (.setInt 1 (.animedbId anime))
          (.setString 2 (.title anime))
          (.setInt 3 (.seriesType anime))
          (.setInt 4 (.episodes anime))
          (.setInt 5 (.seriesStatus anime))
          (.setString 6 (adjust-date (.start anime)))
          (.setString 7 (adjust-date (.end anime)))
          (.setString 8 (.image anime))
          (.addBatch)))
      (.executeBatch statement))))

(defn- maybe-copy-completed [new-rated old-rated]
  (let [status-new (.status new-rated)]
    (if (and (= status-new (.status old-rated))
             (or (= status-new aqua.mal.data.Rated/COMPLETED)
                 (= status-new aqua.mal.data.Rated/DROPPED)))
      (set! (.status new-rated) (.status old-rated)))))

(defn- merge-anime-list [connection user new-rated-list]
  (if-let [current-user (load-user-from-connection connection (.username user))]
    (let [old-rated-map (into {} (for [rated (.animeList current-user)]
                                   [(.animedbId rated) rated]))
          process-item (fn [changed new-rated]
                         (if-let [old-rated (old-rated-map (.animedbId new-rated))]
                           (do
                             (maybe-copy-completed new-rated old-rated)
                             (cond
                               (not= (.status old-rated) (.status new-rated)) true
                               (not= (.rating old-rated) (.rating new-rated)) true
                               :else changed))
                           ; no old rated: definitely a change
                           true))]
      ; not a purely-functional reduce
      (reduce process-item false new-rated-list))
    ; no old user: definitely a change
    true))

(def ^:private insert-anime-list
  (str "INSERT OR REPLACE INTO anime_list"
       "        (user_id, anime_list)"
       "    VALUES"
       "        (?, ?)"))

(defn- today-epoch-day []
  (.toEpochDay (java.time.LocalDate/now)))

(defn- insert-or-update-user-anime-list [connection user anime-list]
  (let [today (today-epoch-day)
        new-rated-list (for [anime anime-list]
                         (aqua.mal.data.Rated. (.animedbId anime)
                                               (.userStatus anime)
                                               (.score anime)
                                               today))
        changed (merge-anime-list connection user new-rated-list)]
    (when changed
      (let [sink (java.io.ByteArrayOutputStream.)
            compress (java.util.zip.GZIPOutputStream. sink)]
        (Json/writeRatedList compress new-rated-list)
        (.finish compress)
        (with-open [statement (doto (.prepareStatement connection insert-anime-list)
                                    (.setInt 1 (.userId user))
                                    (.setBytes 2 (.toByteArray sink)))]
          (.execute statement))))
    changed))

(def ^:private update-user-stats
  (str "INSERT OR REPLACE INTO user_anime_stats"
       "        (user_id, planned, watching, completed, onhold, dropped)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?)"))

(defn- insert-or-update-user [connection user anime-list-changed]
  (if anime-list-changed
    (with-open [statement (doto (.prepareStatement connection update-changed-user)
                                (.setInt 1 (.userId user))
                                (.setString 2 (.username user)))]
      (.execute statement))
    (with-open [statement (doto (.prepareStatement connection update-unchanged-user)
                                (.setInt 1 (.userId user))
                                (.setString 2 (.username user))
                                (.setInt 3 (.userId user)))]
      (.execute statement)))
  ; 'changed' only tracks changes to completed/dropped
  (with-open [statement (doto (.prepareStatement connection update-user-stats)
                              (.setInt 1 (.userId user))
                              (.setInt 2 (.plantowatch user))
                              (.setInt 3 (.watching user))
                              (.setInt 4 (.completed user))
                              (.setInt 5 (.onhold user))
                              (.setInt 6 (.dropped user)))]
    (.execute statement)))

(defn store-user-anime-list [data-source username mal-app-info]
  (let [user (.user mal-app-info)
        anime (.anime mal-app-info)]
    (with-open [connection (.getConnection data-source)]
      (insert-or-update-anime connection anime)

      (when (or (= nil user) (= nil (.username user)))
        (mark-user-updated connection username))

      (let [anime-list-changed (insert-or-update-user-anime-list connection user anime)]
        (insert-or-update-user connection user anime-list-changed)))))
