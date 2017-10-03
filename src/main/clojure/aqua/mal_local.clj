(ns aqua.mal-local
  (:require [clojure.java.io :as io]
            clojure.string)
  (:import (aqua.mal Json)))

(def ^:private byte-array-class  (Class/forName "[B"))

(defn- set-arg [^java.sql.PreparedStatement statement index arg]
  (cond
    (nil? arg) (.setNull statement index java.sql.Types/NULL)
    (instance? Long arg) (.setLong statement index arg)
    (instance? Integer arg) (.setInt statement index arg)
    (instance? String arg) (.setString statement index arg)
    (instance? byte-array-class arg) (.setBytes statement index arg)
    :else (throw (Exception. (str "Can't handle class " (type arg) " " arg)))))

(defn- make-set-args [args]
  (map-indexed
    #(list `set-arg (+ %1 1) %2)
    args))

(defn- runtime-set-args [rs args]
  (doseq [[index arg] (map list (range) args)]
    (set-arg rs (+ index 1) arg)))

(defn cast-symbol [ty sym]
  (vary-meta sym assoc :tag ty))

(defmacro get-int [rs index]
  `(.getInt ~(vary-meta rs assoc :tag `java.sql.ResultSet) ~index))

(defmacro get-string [rs index]
  `(.getString ~(vary-meta rs assoc :tag `java.sql.ResultSet) ~index))

(defn- get-connection-helper [data-source]
  `(.getConnection ~(vary-meta data-source assoc :tag `javax.sql.DataSource)))

(defmacro with-query
  [data-source rs-name query args & body]
    (let [statement-sym (gensym 'statement_)
          connection (get-connection-helper data-source)
          set-args-list (if (= (type args) clojure.lang.PersistentVector)
                          (make-set-args args)
                          `[(runtime-set-args ~args)])]
      `(with-open [connection# ~connection
                   ~statement-sym (doto (.prepareStatement connection# ~query)
                                        ~@set-args-list)
                   ~rs-name (.executeQuery ~statement-sym)]
        ~@body)))

(defmacro placeholders [items]
  `(clojure.string/join "," (repeat (count ~items) "?")))

(defmacro execute [connection query args]
  (let [typed-connection (cast-symbol 'java.sql.Connection connection)
        set-args-list (if (= (type args) clojure.lang.PersistentVector)
                        (make-set-args args)
                        `[(runtime-set-args ~args)])]
    `(with-open [statement# (doto (.prepareStatement ~typed-connection ~query)
                                  ~@set-args-list)]
       (.execute statement#))))

(defmacro with-transaction [data-source connection-name & body]
  (let [connection (get-connection-helper data-source)]
    `(with-open [~connection-name ~connection]
       (.setAutoCommit ~connection-name false)
       ~@body
       (.commit ~connection-name))))

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
       "          username <> '' AND"
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
            item-id (.animedbId item)
            cached (if-let [existing (.get cache item)]
                     existing
                     (do
                       (if (and anime-map-to-filter-hentai
                                ; when we have an user referring to non-existing anime
                                (if-let [in-map (anime-map-to-filter-hentai item-id)]
                                  (.isHentai in-map)
                                  false))
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

(defn load-test-cf-user-ids [data-source user-ids max-count]
  (with-open [connection (.getConnection data-source)]
    (let [query (select-test-user-ids user-ids)]
      (selected-cf-user-ids connection max-count query))))

(defn load-filtered-cf-users-into [data-source user-ids cf-parameters cache target anime-map-to-filter-hentai]
  (with-open [connection (.getConnection data-source)]
    ; this allocates and throws away an ArrayList, it's fine
    (select-users-by-id connection
                        user-ids
                        (partial load-filtered-cf-users-from-rs cf-parameters cache target anime-map-to-filter-hentai))
    target))

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

(def ^:private select-anime-genres-map
  "SELECT animedb_id, genre_id, sort_order FROM anime_genres ORDER BY animedb_id, sort_order")

(defn- load-genres-map [data-source]
  (let [genre-names (load-genre-names data-source)
        genres-map (java.util.HashMap.)]
    (with-open [connection (.getConnection data-source)
                statement (.createStatement connection)
                rs (.executeQuery statement select-anime-genres-map)]
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

(def ^:private select-anime-ids
  (str "SELECT a.animedb_id, COALESCE(adu.last_update, 1) AS last_update"
       "    FROM anime AS a"
       "      LEFT JOIN anime_details_update AS adu"
       "        ON a.animedb_id = adu.animedb_id"
       "    WHERE a.animedb_id > ?"
       "    LIMIT ?"))

(defn all-anime-ids [data-source after-id limit]
  (with-query data-source rs select-anime-ids [after-id limit]
    (doall (into {} (map #(vector (:animedb_id %) (:last_update %))
                         (resultset-seq rs))))))

(def ^:private select-user-ids
  (str "SELECT user_id, last_update FROM users WHERE user_id > ? LIMIT ?"))

(defn all-user-ids [data-source after-id limit]
  (with-query data-source rs select-user-ids [after-id limit]
    (doall (into {} (map #(vector (:user_id %) (:last_update %))
                         (resultset-seq rs))))))

(defn- select-changed-user-ids [items]
  (str "SELECT user_id, last_update"
       "    FROM users"
       "    WHERE user_id IN (" (placeholders items) ")"))

(defn- select-user-data [items]
  (str "SELECT u.user_id, u.username, u.last_update, u.last_change,"
       "       uas.planned, uas.watching, uas.completed, uas.onhold, uas.dropped,"
       "       al.anime_list"
       "    FROM users AS u"
       "      INNER JOIN user_anime_stats AS uas"
       "        ON u.user_id = uas.user_id"
       "      INNER JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "    WHERE u.user_id IN (" (placeholders items) ")"))

(defn- filter-unchanged-items [rs id-key update-key id-to-time]
  (->> (resultset-seq rs)
       (filter #(< (id-to-time (str (id-key %)))
                   (update-key %)))
       (map id-key)))

(defn select-changed-users [data-source user-id-to-time]
  (let [; user ids updated after specified time
        user-ids (with-query data-source
                             rs
                             (select-changed-user-ids user-id-to-time)
                             (keys user-id-to-time)
                   (doall (filter-unchanged-items rs
                                                  :user_id
                                                  :last_update
                                                  user-id-to-time)))]
    (with-query data-source rs (select-user-data user-ids) user-ids
      (doall (resultset-seq rs)))))

(defn- select-changed-anime-ids [items]
  (str "SELECT a.animedb_id, COALESCE(adu.last_update, 0) AS last_update"
       "    FROM anime AS a"
       "      LEFT JOIN anime_details_update AS adu"
       "        ON a.animedb_id = adu.animedb_id"
       "    WHERE a.animedb_id IN (" (placeholders items) ")"))

(defn- select-anime-data [items]
  (str "SELECT a.animedb_id, a.title, a.type, a.episodes, a.status,"
       "       a.start, a.end, a.image,"
       "       ad.rank, ad.popularity, ad.score,"
       "       adu.last_update"
       "    FROM anime AS a"
       "      INNER JOIN anime_details AS ad"
       "        ON a.animedb_id = ad.animedb_id"
       "      INNER JOIN anime_details_update AS adu"
       "        ON a.animedb_id = adu.animedb_id"
       "    WHERE a.animedb_id IN (" (placeholders items) ")"))

(defn- select-anime-titles [items]
  (str "SELECT animedb_id, title"
       "    FROM anime_titles"
       "    WHERE animedb_id IN (" (placeholders items) ")"))

(defn- select-anime-relations [items]
  (str "SELECT animedb_id, related_id, relation"
       "    FROM anime_relations"
       "    WHERE animedb_id IN (" (placeholders items) ")"))

(defn- select-anime-genres [items]
  (str "SELECT ag.animedb_id, ag.genre_id, agn.description, ag.sort_order"
       "    FROM anime_genres AS ag"
       "      INNER JOIN anime_genre_names agn"
       "        ON ag.genre_id = agn.genre"
       "    WHERE animedb_id IN (" (placeholders items) ")"))

(defn select-changed-anime [data-source anime-id-to-time]
  (letfn [(fetch-anime-map [anime-ids]
            (with-query data-source
                        rs
                        (select-anime-data anime-ids)
                        anime-ids
              (doall (into {} (for [anime (resultset-seq rs)]
                                [(:animedb_id anime) anime])))))
          (update-anime [fields into-field]
            (fn [anime-map item]
              (let [anime-id (:animedb_id item)
                    anime (anime-map anime-id)
                    value (if (keyword? fields)
                            (item fields)
                            (select-keys item fields))
                    current (get anime into-field [])
                    updated-anime (assoc anime
                                    into-field (conj current value))]
                (assoc anime-map anime-id updated-anime))))
          (join-fields [anime-map query-builder fields into-field]
            (let [query (query-builder anime-map)
                  items (with-query data-source rs query (keys anime-map)
                          (doall (resultset-seq rs)))]
              (reduce (update-anime fields into-field) anime-map items)))]
  (let [; anime ids updated after specified time
        anime-ids (with-query data-source
                              rs
                              (select-changed-anime-ids anime-id-to-time)
                              (keys anime-id-to-time)
                    (doall (filter-unchanged-items rs
                                                   :animedb_id
                                                   :last_update
                                                   anime-id-to-time)))
        anime (fetch-anime-map anime-ids)]
    (-> anime
        (join-fields select-anime-titles :title :titles)
        (join-fields select-anime-relations [:related_id :relation] :relations)
        (join-fields select-anime-genres [:genre_id :description :sort_order] :genres)
        vals))))

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

; here we set username to '' because it's hard to make models cope
; with users being deleted
(def ^:private fix-changed-case-usernames
  (str "UPDATE users SET username = '' WHERE username = ?"))
(def ^:private fix-duplicated-usernames
  (str "UPDATE users SET username = '' WHERE username = ? AND user_id <> ?"))

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

(defn- insert-or-update-user [connection request-username
                              ^aqua.mal.data.MalAppInfo$UserInfo user
                              anime-list-changed]
  (if anime-list-changed
    (execute connection update-changed-user
             [(.userId user) (.username user)])
    (execute connection update-unchanged-user
             [(.userId user) (.username user) (.userId user)]))

  ; It looks like that when usernames change case, a new user id is
  ; created, or something like that, so when the requested and
  ; received usernames
  (when (not= (.username user) request-username)
    (execute connection fix-changed-case-usernames [request-username]))

  ; This is to clear bed data caused by me not knowing about the above
  (execute connection fix-duplicated-usernames
           [(.username user) (.userId user)])

  ; 'changed' only tracks changes to completed/dropped
  (execute connection update-user-stats
           [(.userId user) (.plantowatch user) (.watching user)
            (.completed user) (.onhold user) (.dropped user)]))

(defn store-user-anime-list [data-source request-username mal-app-info]
  (let [user (.user mal-app-info)
        anime (.anime mal-app-info)]
    (with-open [connection (.getConnection data-source)]
      (insert-or-update-anime connection anime)

      (when (or (= nil user) (= nil (.username user)))
        (mark-user-updated connection request-username))

      (let [anime-list-changed (insert-or-update-user-anime-list connection user anime)]
        (insert-or-update-user connection request-username user anime-list-changed)))))

(def ^:private sync-update-user
  (str "INSERT OR REPLACE INTO users"
       "       (user_id, username, last_update, last_change)"
       "    VALUES"
       "       (?,"
       "        COALESCE((SELECT username FROM users WHERE user_id = ?), ?),"
       "        ?,"
       "        ?)"))

(defn- update-user [connection
                    {:strs [user_id username last_update last_change
                            anime_list
                            planned watching completed onhold dropped]}]
  (execute connection
           sync-update-user
           [user_id user_id username last_update last_change])
  (execute connection
           update-user-stats
           [user_id planned watching completed onhold dropped])
  (execute connection
           insert-anime-list
           [user_id (.decode (com.google.common.io.BaseEncoding/base64) anime_list)]))

(defn store-users [data-source users]
  (with-transaction data-source connection
    (doseq [user users]
      (update-user connection user))))

(def ^:private sync-update-anime
  (str "INSERT OR REPLACE INTO anime"
       "        (animedb_id, title, type, episodes, status, start, end, image)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?, ?, ?)"))

(def ^:private update-anime-details
  (str "INSERT OR REPLACE INTO anime_details"
       "        (animedb_id, rank, popularity, score)"
       "    VALUES"
       "        (?, ?, ?, ?)"))

(def ^:private sync-update-anime-details-update
  (str "INSERT OR REPLACE INTO anime_details_update"
       "        (animedb_id, last_update)"
       "    VALUES"
       "        (?, ?)"))

(def ^:private delete-anime-side-tables
  ["DELETE FROM anime_relations WHERE animedb_id = ?"
   "DELETE FROM anime_genres WHERE animedb_id = ?"
   "DELETE FROM anime_titles WHERE animedb_id = ?"])

(def ^:private update-anime-relations
  (str "INSERT OR REPLACE INTO anime_relations"
       "        (animedb_id, related_id, relation)"
       "    VALUES"
       "        (?, ?, ?)"))

(def ^:private update-anime-titles
  (str "INSERT OR REPLACE INTO anime_titles"
       "        (animedb_id, title)"
       "    VALUES"
       "        (?, ?)"))

(def ^:private update-anime-genres
  (str "INSERT OR REPLACE INTO anime_genres"
       "        (animedb_id, genre_id, sort_order)"
       "    VALUES"
       "        (?, ?, ?)"))

(def ^:private update-anime-genre-names
  (str "INSERT OR REPLACE INTO anime_genre_names"
       "        (genre, description)"
       "    VALUES"
       "        (?, ?)"))

(defn- store-anime-side-tables [connection animedb_id genres titles relations]
  (doseq [query delete-anime-side-tables]
    (execute connection query [animedb_id]))
  (doseq [{:strs [genre_id description sort_order]} genres]
    (execute connection update-anime-genre-names [genre_id description])
    (execute connection update-anime-genres [animedb_id genre_id sort_order]))
  (doseq [title titles]
    (execute connection update-anime-titles [animedb_id title]))
  (doseq [{:strs [related_id relation]} relations]
    (execute connection update-anime-relations [animedb_id related_id relation])))

(defn- update-anime [connection
                    {:strs [animedb_id title type episodes status start end image
                            rank popularity score
                            last_update genres titles relations]}]
  (execute connection
           sync-update-anime
           [animedb_id title type episodes status start end image])
  (when-not (= last_update 1)
    (execute connection
             update-anime-details
             [animedb_id rank popularity score])
    (store-anime-side-tables connection animedb_id genres titles relations)
    (execute connection
             sync-update-anime-details-update
             [animedb_id last_update])))

(defn store-anime [data-source anime-list]
  (with-transaction data-source connection
    (doseq [anime anime-list]
      (update-anime connection anime))))
