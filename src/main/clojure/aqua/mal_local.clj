(ns aqua.mal-local
  (:require [clojure.java.io :as io]
             clojure.string
             aqua.db-schema)
  (:use aqua.db-utils)
  (:import (aqua.mal Serialize)))

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

(defn setup-tables [^javax.sql.DataSource data-source]
  (with-open [connection (.getConnection data-source)]
    (doseq [statement aqua.db-schema/statements]
      (execute connection statement []))))

(defn- doall-rs [^java.sql.ResultSet rs func]
  (let [result (java.util.ArrayList.)]
    (while (.next rs)
      (.add result (func rs)))
    result))

(def ^:private select-users
  (str "SELECT u.user_id AS user_id, u.username AS username,"
       "       al.anime_list AS anime_list,"
       "       al.anime_list_format AS anime_list_format"
       "    FROM users AS u"
       "      INNER JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "    WHERE u.user_id IN "))

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
    (.setAnimeList user (if (= 1 (.getInt rs 4))
                          (Serialize/readRatedProtobuf al-data)
                          (Serialize/readRatedList al-data)))
    user))

(defn- load-cf-users-from-rs [cf-parameters ^java.sql.ResultSet rs]
  (let [user (aqua.recommend.CFUser.)
        al-data (java.util.zip.GZIPInputStream.
                  (io/input-stream (.getBinaryStream rs 3)))]
    (set! (.userId user) (.getInt rs 1))
    (set! (.username user) (.getString rs 2))
    (.setAnimeList user cf-parameters (if (= 1 (.getInt rs 4))
                                        (Serialize/readCFRatedProtobuf al-data)
                                        (Serialize/readCFRatedList al-data)))
    user))

(defn- load-filtered-cf-users-from-rs [cf-parameters
                                       ^java.util.List target
                                       ^java.util.Map anime-map-to-filter-hentai
                                       ^java.sql.ResultSet rs]
  (let [user (aqua.recommend.CFUser.)
        al-data (java.util.zip.GZIPInputStream.
                  (io/input-stream (.getBinaryStream rs 3)))
        anime-list (if (= 1 (.getInt rs 4))
                     (Serialize/readCFRatedProtobuf al-data)
                     (Serialize/readCFRatedList al-data))]
    (if anime-map-to-filter-hentai
      (doseq [^aqua.recommend.CFRated item anime-list]
        (let [item-id (.animedbId item)
              is-hentai ; when we have an user referring to non-existing anime
                        (if-let [^aqua.mal.data.Anime in-map
                                  (anime-map-to-filter-hentai item-id)]
                          (.isHentai in-map)
                          false)]
          (if is-hentai
            (.setHentai item)))))
    (set! (.userId user) (.getInt rs 1))
    (set! (.username user) (.getString rs 2))
    (.setFilteredAnimeList user cf-parameters anime-list)
    (.set target (- (.getRow rs) 1) user)
    ; return nil to avoid retaining the previous value in the
    ; result list
    nil))

(defn- select-users-by-id [data-source ids loader]
  (let [query (str select-users "(" (clojure.string/join "," ids) ")")]
    (with-query data-source rs query []
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
  (select-users-by-id data-source ids
                      (partial load-cf-users-from-rs cf-parameters)))

(defn load-test-cf-user-ids [data-source user-ids max-count]
  (with-open [connection (.getConnection data-source)]
    (let [query (select-test-user-ids user-ids)]
      (selected-cf-user-ids connection max-count query))))

(defn load-filtered-cf-users-into [data-source user-ids cf-parameters target anime-map-to-filter-hentai]
  ; this allocates and throws away an ArrayList, it's fine
  (select-users-by-id data-source
                      user-ids
                      (partial load-filtered-cf-users-from-rs cf-parameters target anime-map-to-filter-hentai))
  target)

(def ^:private select-user
  (str "SELECT u.user_id AS user_id, u.username AS username,"
       "       al.anime_list AS anime_list,"
       "       al.anime_list_format AS anime_list_format"
       "    FROM users AS u"
       "      INNER JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "    WHERE u.username = ?"))

(defn load-cf-user [data-source username cf-parameters]
  (with-open [connection (.getConnection data-source)
              statement (doto (.prepareStatement connection select-user)
                              (.setString 1 username))
              rs (.executeQuery statement)]
    (first (doall-rs rs (partial load-cf-users-from-rs cf-parameters)))))

(def ^:private select-user-blob
  (str "SELECT LENGTH(al.anime_list) AS blob_length,"
       "       al.anime_list AS anime_list,"
       "       al.anime_list_format AS anime_list_format"
       "    FROM users AS u"
       "      INNER JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "    WHERE u.username = ?"))

(defn load-user-anime-list [data-source username]
  (with-query data-source rs select-user-blob [username]
    (if (.next rs)
      (let [byte-size (.getInt rs 1)
            is (.getBinaryStream rs 2)
            bytes (make-array Byte/TYPE byte-size)]
        (.read is bytes)
        [bytes (.getInt rs 3)]))))

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
  "SELECT animedb_id, title, status, episodes, start, end, image, type FROM anime")

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
            (set! (.seriesType anime) (:type item))
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

(def ^:private select-case-correct-username
  (str "SELECT username FROM users where username = ? COLLATE nocase"))

(defn case-correct-username [data-source username-nocase]
  (with-query data-source rs select-case-correct-username [username-nocase]
    (if (.next rs)
      (.getString rs 1)
      username-nocase)))

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

(def ^:private select-manga-ids
  (str "SELECT m.mangadb_id, COALESCE(mdu.last_update, 1) AS last_update"
       "    FROM manga AS m"
       "      LEFT JOIN manga_details_update AS mdu"
       "        ON m.mangadb_id = mdu.mangadb_id"
       "    WHERE m.mangadb_id > ?"
       "    LIMIT ?"))

(defn all-manga-ids [data-source after-id limit]
  (with-query data-source rs select-manga-ids [after-id limit]
    (doall (into {} (map #(vector (:mangadb_id %) (:last_update %))
                         (resultset-seq rs))))))

(def ^:private select-user-ids
  (str "SELECT u.user_id AS user_id, u.last_change AS last_change"
       "    FROM users AS u"
       "      INNER JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "    WHERE u.user_id > ? AND"
       "          al.anime_list_format = 1"
       "    LIMIT ?"))

(defn all-user-ids [data-source after-id limit]
  (with-query data-source rs select-user-ids [after-id limit]
    (doall (into {} (map #(vector (:user_id %) (:last_change %))
                         (resultset-seq rs))))))

(defn- select-changed-user-ids [items]
  (str "SELECT user_id, last_change"
       "    FROM users"
       "    WHERE user_id IN (" (placeholders items) ")"))

(defn- select-user-data [items]
  (str "SELECT u.user_id, u.username, u.last_update, u.last_change, u.last_anime_change, u.last_manga_change,"
       "       uas.planned AS anime_planned, uas.watching AS anime_watching, uas.completed AS anime_completed,"
       "           uas.onhold AS anime_onhold, uas.dropped AS anime_dropped,"
       "       mas.planned AS manga_planned, mas.reading AS manga_reading, mas.completed AS manga_completed,"
       "           mas.onhold AS manga_onhold, mas.dropped AS manga_dropped,"
       "       al.anime_list, al.anime_list_format,"
       "       ml.manga_list"
       "    FROM users AS u"
       "      LEFT JOIN user_anime_stats AS uas"
       "        ON u.user_id = uas.user_id"
       "      LEFT JOIN user_manga_stats AS mas"
       "        ON u.user_id = mas.user_id"
       "      LEFT JOIN anime_list AS al"
       "        ON u.user_id = al.user_id"
       "      LEFT JOIN manga_list AS ml"
       "        ON u.user_id = ml.user_id"
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
                                                  :last_change
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

(defn- select-changed-manga-ids [items]
  (str "SELECT m.mangadb_id, COALESCE(mdu.last_update, 0) AS last_update"
       "    FROM manga AS m"
       "      LEFT JOIN manga_details_update AS mdu"
       "        ON m.mangadb_id = mdu.mangadb_id"
       "    WHERE m.mangadb_id IN (" (placeholders items) ")"))

(defn- select-manga-data [items]
  (str "SELECT m.mangadb_id, m.title, m.type, m.chapters, m.volumes, m.status,"
       "       m.start, m.end, m.image,"
       "       md.rank, md.popularity, md.score,"
       "       mdu.last_update"
       "    FROM manga AS m"
       "      INNER JOIN manga_details AS md"
       "        ON m.mangadb_id = md.mangadb_id"
       "      INNER JOIN manga_details_update AS mdu"
       "        ON m.mangadb_id = mdu.mangadb_id"
       "    WHERE m.mangadb_id IN (" (placeholders items) ")"))

(defn- select-manga-titles [items]
  (str "SELECT mangadb_id, title"
       "    FROM manga_titles"
       "    WHERE mangadb_id IN (" (placeholders items) ")"))

(defn- select-manga-relations [items]
  (str "SELECT mangadb_id, related_id, relation"
       "    FROM manga_relations"
       "    WHERE mangadb_id IN (" (placeholders items) ")"))

(defn- select-manga-genres [items]
  (str "SELECT mg.mangadb_id, mg.genre_id, mgn.description, mg.sort_order"
       "    FROM manga_genres AS mg"
       "      INNER JOIN manga_genre_names mgn"
       "        ON mg.genre_id = mgn.genre"
       "    WHERE mangadb_id IN (" (placeholders items) ")"))

(defn select-changed-manga [data-source manga-id-to-time]
  (letfn [(fetch-manga-map [manga-ids]
            (with-query data-source
                        rs
                        (select-manga-data manga-ids)
                        manga-ids
              (doall (into {} (for [manga (resultset-seq rs)]
                                [(:mangadb_id manga) manga])))))
          (update-manga [fields into-field]
            (fn [manga-map item]
              (let [manga-id (:mangadb_id item)
                    manga (manga-map manga-id)
                    value (if (keyword? fields)
                            (item fields)
                            (select-keys item fields))
                    current (get manga into-field [])
                    updated-manga (assoc manga
                                    into-field (conj current value))]
                (assoc manga-map manga-id updated-manga))))
          (join-fields [manga-map query-builder fields into-field]
            (let [query (query-builder manga-map)
                  items (with-query data-source rs query (keys manga-map)
                          (doall (resultset-seq rs)))]
              (reduce (update-manga fields into-field) manga-map items)))]
  (let [; anime ids updated after specified time
        manga-ids (with-query data-source
                              rs
                              (select-changed-manga-ids manga-id-to-time)
                              (keys manga-id-to-time)
                    (doall (filter-unchanged-items rs
                                                   :mangadb_id
                                                   :last_update
                                                   manga-id-to-time)))
        manga (fetch-manga-map manga-ids)]
    (-> manga
        (join-fields select-manga-titles :title :titles)
        (join-fields select-manga-relations [:related_id :relation] :relations)
        (join-fields select-manga-genres [:genre_id :description :sort_order] :genres)
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

(def ^:private update-changed-anime-user
  (str "INSERT OR REPLACE INTO users"
       "        (user_id, username, last_update, last_change, last_anime_change, last_manga_change)"
       "    VALUES (?, ?, STRFTIME('%s', 'now'), MAX(?, (SELECT last_manga_change FROM users WHERE user_id = ?)), ?,"
       "            (SELECT last_manga_change FROM users WHERE user_id = ?))"))

(def ^:private update-changed-manga-user
  (str "INSERT OR REPLACE INTO users"
       "        (user_id, username, last_update, last_change, last_anime_change, last_manga_change)"
       "    VALUES (?, ?, STRFTIME('%s', 'now'), MAX(?, (SELECT last_anime_change FROM users WHERE user_id = ?)),"
       "            (SELECT last_anime_change FROM users WHERE user_id = ?),"
       "            ?)"))

(def ^:private update-unchanged-user
  (str "INSERT OR REPLACE INTO users"
       "        (user_id, username, last_update, last_change, last_anime_change, last_manga_change)"
       "    VALUES (?, ?, STRFTIME('%s', 'now'),"
       "            (SELECT last_change FROM users WHERE user_id = ?),"
       "            (SELECT last_anime_change FROM users WHERE user_id = ?),"
       "            (SELECT last_manga_change FROM users WHERE user_id = ?))"))

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

(def ^:private select-manga-prefix
  (str "SELECT mangadb_id, title, type, chapters, volumes, status, STRFTIME('%Y-%m-%d', start, 'unixepoch'), STRFTIME('%Y-%m-%d', end, 'unixepoch'), image"
       "    FROM manga"
       "    WHERE mangadb_id IN"))

(def ^:private insert-anime
  (str "INSERT OR REPLACE INTO anime"
       "        (animedb_id, title, type, episodes, status, start, end, image)"
       "    VALUES (?, ?, ?, ?, ?, STRFTIME('%s', ?), STRFTIME('%s', ?), ?)"))

(def ^:private insert-manga
  (str "INSERT OR REPLACE INTO manga"
       "        (mangadb_id, title, type, chapters, volumes, status, start, end, image)"
       "    VALUES (?, ?, ?, ?, ?, ?, STRFTIME('%s', ?), STRFTIME('%s', ?), ?)"))

(defn- adjust-date [^String date-string]
  (cond
    (= "0000-00-00" date-string) nil
    (.endsWith date-string "-00") (str (subs date-string 0 7) "-01")
    :else date-string))

(defn- anime-equals [^aqua.mal.data.MalAppInfo$RatedAnime anime
                     ^java.sql.ResultSet rs]
  (and (= (.getString rs 2) (.title anime))
       (= (.getInt rs 3) (.seriesType anime))
       (= (.getInt rs 4) (.episodes anime))
       (= (.getInt rs 5) (.seriesStatus anime))
       (= (.getString rs 6) (adjust-date (.start anime)))
       (= (.getString rs 7) (adjust-date (.end anime)))
       (= (.getString rs 8) (.image anime))))

(defn- manga-equals [^aqua.mal.data.MalAppInfo$RatedManga manga
                     ^java.sql.ResultSet rs]
  (and (= (.getString rs 2) (.title manga))
       (= (.getInt rs 3) (.seriesType manga))
       (= (.getInt rs 4) (.chapters manga))
       (= (.getInt rs 5) (.volumes manga))
       (= (.getInt rs 6) (.seriesStatus manga))
       (= (.getString rs 7) (adjust-date (.start manga)))
       (= (.getString rs 8) (adjust-date (.end manga)))
       (= (.getString rs 9) (.image manga))))

(defn- make-malappinfo-anime-hash ^java.util.HashMap [rated-list]
  (let [hash (java.util.HashMap.)]
    (doseq [^aqua.mal.data.MalAppInfo$RatedAnime rated rated-list]
      (.put hash (.animedbId rated) rated))
    hash))

(defn- make-malappinfo-manga-hash ^java.util.HashMap [rated-list]
  (let [hash (java.util.HashMap.)]
    (doseq [^aqua.mal.data.MalAppInfo$RatedManga rated rated-list]
      (.put hash (.mangadbId rated) rated))
    hash))

(defn- insert-or-update-anime [connection mal-app-info-anime]
  (let [anime-map (make-malappinfo-anime-hash mal-app-info-anime)
        id-csv (clojure.string/join "," (keys anime-map))
        select-anime (str select-anime-prefix " (" id-csv ")")]
    (with-open [statement (.createStatement connection)
                ^java.sql.ResultSet rs (.executeQuery statement select-anime)]
      (while (.next rs)
        (let [animedb-id (.getInt rs 1)
              anime (.get anime-map animedb-id)]
          (if (anime-equals anime rs)
            (.remove anime-map animedb-id)))))

    (with-open [statement (.prepareStatement connection insert-anime)]
      (doseq [^aqua.mal.data.MalAppInfo$RatedAnime anime (vals anime-map)]
        (doto ^java.sql.PreparedStatement statement
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

(defn- insert-or-update-manga [connection mal-app-info-manga]
  (let [manga-map (make-malappinfo-manga-hash mal-app-info-manga)
        id-csv (clojure.string/join "," (keys manga-map))
        select-manga (str select-manga-prefix " (" id-csv ")")]
    (with-open [statement (.createStatement connection)
                ^java.sql.ResultSet rs (.executeQuery statement select-manga)]
      (while (.next rs)
        (let [mangadb-id (.getInt rs 1)
              manga (.get manga-map mangadb-id)]
          (if (manga-equals manga rs)
            (.remove manga-map mangadb-id)))))

    (with-open [statement (.prepareStatement connection insert-manga)]
      (doseq [^aqua.mal.data.MalAppInfo$RatedManga manga (vals manga-map)]
        (doto ^java.sql.PreparedStatement statement
          (.setInt 1 (.mangadbId manga))
          (.setString 2 (.title manga))
          (.setInt 3 (.seriesType manga))
          (.setInt 4 (.chapters manga))
          (.setInt 5 (.volumes manga))
          (.setInt 6 (.seriesStatus manga))
          (.setString 7 (adjust-date (.start manga)))
          (.setString 8 (adjust-date (.end manga)))
          (.setString 9 (.image manga))
          (.addBatch)))
      (.executeBatch statement))))

(def ^:private insert-anime-list
  (str "INSERT OR REPLACE INTO anime_list"
       "        (user_id, anime_list, anime_list_format)"
       "    VALUES"
       "        (?, ?, ?)"))

(defn- insert-or-update-user-anime-list [connection user anime-list]
  (let [new-rated-list (for [^aqua.mal.data.MalAppInfo$RatedAnime anime anime-list]
                         (aqua.mal.data.Rated. (.animedbId anime)
                                               (.userStatus anime)
                                               (.score anime)
                                               (int (/ (.lastUpdated anime) 86400))))]
    (let [sink (java.io.ByteArrayOutputStream.)
          compress (java.util.zip.GZIPOutputStream. sink)]
      (Serialize/writeRatedProtobuf compress new-rated-list)
      (.finish compress)
      (execute connection insert-anime-list
               [(.userId user) (.toByteArray sink) 1]))
    nil))

(def ^:private insert-manga-list
  (str "INSERT OR REPLACE INTO manga_list"
       "        (user_id, manga_list)"
       "    VALUES"
       "        (?, ?)"))

(defn- insert-or-update-user-manga-list [connection user manga-list]
  (let [new-rated-list (for [^aqua.mal.data.MalAppInfo$RatedManga manga manga-list]
                         (aqua.mal.data.Rated. (.mangadbId manga)
                                               (.userStatus manga)
                                               (.score manga)
                                               (int (/ (.lastUpdated manga) 86400))))]
    (let [sink (java.io.ByteArrayOutputStream.)
          compress (java.util.zip.GZIPOutputStream. sink)]
      (Serialize/writeRatedProtobuf compress new-rated-list)
      (.finish compress)
      (execute connection insert-manga-list
               [(.userId user) (.toByteArray sink)]))
    nil))

(def ^:private update-user-anime-stats
  (str "INSERT OR REPLACE INTO user_anime_stats"
       "        (user_id, planned, watching, completed, onhold, dropped)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?)"))

(defn- insert-or-update-anime-user [connection request-username
                                    ^aqua.mal.data.MalAppInfo$UserInfo user
                                    change-time]
  (if (not= change-time 0)
    (execute connection update-changed-anime-user
             [(.userId user) (.username user) change-time (.userId user) change-time (.userId user)])
    (execute connection update-unchanged-user
             [(.userId user) (.username user) (.userId user) (.userId user) (.userId user)]))

  ; It looks like that when usernames change case, a new user id is
  ; created, or something like that, so when the requested and
  ; received usernames
  (when (not= (.username user) request-username)
    (execute connection fix-changed-case-usernames [request-username]))

  ; This is to clear bed data caused by me not knowing about the above
  (execute connection fix-duplicated-usernames
           [(.username user) (.userId user)])

  (execute connection update-user-anime-stats
           [(.userId user) (.plantowatch user) (.watching user)
            (.completed user) (.onhold user) (.dropped user)]))

(def ^:private update-user-manga-stats
  (str "INSERT OR REPLACE INTO user_manga_stats"
       "        (user_id, planned, reading, completed, onhold, dropped)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?)"))

(defn- insert-or-update-manga-user [connection request-username
                                    ^aqua.mal.data.MalAppInfo$UserInfo user
                                    change-time]
  (if (not= change-time 0)
    (execute connection update-changed-manga-user
             [(.userId user) (.username user) change-time (.userId user) (.userId user) change-time])
    (execute connection update-unchanged-user
             [(.userId user) (.username user) (.userId user) (.userId user) (.userId user)]))

  ; It looks like that when usernames change case, a new user id is
  ; created, or something like that, so when the requested and
  ; received usernames
  (when (not= (.username user) request-username)
    (execute connection fix-changed-case-usernames [request-username]))

  ; This is to clear bed data caused by me not knowing about the above
  (execute connection fix-duplicated-usernames
           [(.username user) (.userId user)])

  (execute connection update-user-manga-stats
           [(.userId user) (.plantoread user) (.reading user)
            (.completed user) (.onhold user) (.dropped user)]))

(defn store-user-anime-list [data-source request-username mal-app-info]
  (let [user (.user mal-app-info)
        anime-list (.anime mal-app-info)]
    (with-open [connection (.getConnection data-source)]
      (if (or (= nil user) (= nil (.username user)))
        (mark-user-updated connection request-username)
        (do
          (insert-or-update-anime connection anime-list)
          (insert-or-update-user-anime-list connection user anime-list)
          (let [change-time (reduce max 0 (for [anime anime-list]
                                            (.lastUpdated anime)))]
            (insert-or-update-anime-user connection request-username user change-time)))))))

(defn store-user-manga-list [data-source request-username mal-app-info]
  (let [user (.user mal-app-info)
        manga-list (.manga mal-app-info)]
    (with-open [connection (.getConnection data-source)]
      (if (or (= nil user) (= nil (.username user)))
        (mark-user-updated connection request-username)
        (do
          (insert-or-update-manga connection manga-list)
          (insert-or-update-user-manga-list connection user manga-list)
          (let [change-time (reduce max 0 (for [manga manga-list]
                                            (.lastUpdated manga)))]
            (insert-or-update-manga-user connection request-username user change-time)))))))

(def ^:private sync-update-user
  (str "INSERT OR REPLACE INTO users"
       "       (user_id, username, last_update, last_change, last_anime_change, last_manga_change)"
       "    VALUES"
       "       (?,"
       "        COALESCE((SELECT username FROM users WHERE user_id = ?), ?),"
       "        ?,"
       "        ?,"
       "        ?,"
       "        ?)"))

(def ^:private guava-base64 (com.google.common.io.BaseEncoding/base64))

(defn- update-user [connection
                    {:strs [user_id username last_update last_change last_anime_change last_manga_change
                            anime_list anime_list_format manga_list
                            anime_planned anime_watching anime_completed anime_onhold anime_dropped
                            manga_planned manga_reading manga_completed manga_onhold manga_dropped]}]
  (execute connection
           sync-update-user
           [user_id user_id username last_update last_change last_anime_change last_manga_change])
  (if anime_planned
    (execute connection
             update-user-anime-stats
             [user_id anime_planned anime_watching anime_completed anime_onhold anime_dropped]))
  (if manga_planned
    (execute connection
             update-user-manga-stats
             [user_id manga_planned manga_reading manga_completed manga_onhold manga_dropped]))
  (if manga_list
    (execute connection
             insert-manga-list
             [user_id (.decode guava-base64 manga_list)]))
  (if anime_list
    (execute connection
             insert-anime-list
             [user_id (.decode guava-base64 anime_list) anime_list_format])))

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

(defn- store-anime-side-tables-sync [connection animedb_id genres titles relations]
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
    (store-anime-side-tables-sync connection animedb_id genres titles relations)
    (execute connection
             sync-update-anime-details-update
             [animedb_id last_update])))

(defn store-anime [data-source anime-list]
  (with-transaction data-source connection
    (doseq [anime anime-list]
      (update-anime connection anime))))

(def ^:private sync-update-manga
  (str "INSERT OR REPLACE INTO manga"
       "        (mangadb_id, title, type, chapters, volumes, status, start, end, image)"
       "    VALUES"
       "        (?, ?, ?, ?, ?, ?, ?, ?, ?)"))

(def ^:private update-manga-details
  (str "INSERT OR REPLACE INTO manga_details"
       "        (mangadb_id, rank, popularity, score)"
       "    VALUES"
       "        (?, ?, ?, ?)"))

(def ^:private sync-update-manga-details-update
  (str "INSERT OR REPLACE INTO manga_details_update"
       "        (mangadb_id, last_update)"
       "    VALUES"
       "        (?, ?)"))

(def ^:private delete-manga-side-tables
  ["DELETE FROM manga_relations WHERE mangadb_id = ?"
   "DELETE FROM manga_genres WHERE mangadb_id = ?"
   "DELETE FROM manga_titles WHERE mangadb_id = ?"])

(def ^:private update-manga-relations
  (str "INSERT OR REPLACE INTO manga_relations"
       "        (mangadb_id, related_id, relation)"
       "    VALUES"
       "        (?, ?, ?)"))

(def ^:private update-manga-titles
  (str "INSERT OR REPLACE INTO manga_titles"
       "        (mangadb_id, title)"
       "    VALUES"
       "        (?, ?)"))

(def ^:private update-manga-genres
  (str "INSERT OR REPLACE INTO manga_genres"
       "        (mangadb_id, genre_id, sort_order)"
       "    VALUES"
       "        (?, ?, ?)"))

(def ^:private update-manga-genre-names
  (str "INSERT OR REPLACE INTO manga_genre_names"
       "        (genre, description)"
       "    VALUES"
       "        (?, ?)"))

(defn- store-anime-side-tables-scraped [connection animedb_id genres titles relations]
  (doseq [query delete-anime-side-tables]
    (execute connection query [animedb_id]))
  (doseq [[genre_id description sort_order] (map conj genres (range))]
    (execute connection update-anime-genre-names [genre_id description])
    (execute connection update-anime-genres [animedb_id genre_id sort_order]))
  (doseq [title titles]
    (execute connection update-anime-titles [animedb_id title]))
  (doseq [[related_id relation] relations]
    (execute connection update-anime-relations [animedb_id related_id relation])))

(def ^:private update-anime-details-update
  (str "INSERT OR REPLACE INTO anime_details_update"
       "    (animedb_id, last_update)"
       "        VALUES"
       "    (?, strftime('%s', 'now'))"))

(defn store-anime-details [data-source animedb-id title
                           {:keys [relations genres titles scores]}]
  (with-transaction data-source connection
    (store-anime-side-tables-scraped connection animedb-id genres titles relations)
    (execute connection
             update-anime-details
             [animedb-id (:rank scores) (:popularity scores) (:score scores)])
    (execute connection update-anime-details-update [animedb-id])))

(defn- store-manga-side-tables-scraped [connection mangadb_id genres titles relations]
  (doseq [query delete-manga-side-tables]
    (execute connection query [mangadb_id]))
  (doseq [[genre_id description sort_order] (map conj genres (range))]
    (execute connection update-manga-genre-names [genre_id description])
    (execute connection update-manga-genres [mangadb_id genre_id sort_order]))
  (doseq [title titles]
    (execute connection update-manga-titles [mangadb_id title]))
  (doseq [[related_id relation] relations]
    (execute connection update-manga-relations [mangadb_id related_id relation])))

(def ^:private update-manga-details-update
  (str "INSERT OR REPLACE INTO manga_details_update"
       "    (mangadb_id, last_update)"
       "        VALUES"
       "    (?, strftime('%s', 'now'))"))

(defn store-manga-details [data-source mangadb-id title
                           {:keys [relations genres titles scores]}]
  (with-transaction data-source connection
    (store-manga-side-tables-scraped connection mangadb-id genres titles relations)
    (execute connection
             update-manga-details
             [mangadb-id (:rank scores) (:popularity scores) (:score scores)])
    (execute connection update-manga-details-update [mangadb-id])))

(defn- store-manga-side-tables-sync [connection mangadb_id genres titles relations]
  (doseq [query delete-manga-side-tables]
    (execute connection query [mangadb_id]))
  (doseq [{:strs [genre_id description sort_order]} genres]
    (execute connection update-manga-genre-names [genre_id description])
    (execute connection update-manga-genres [mangadb_id genre_id sort_order]))
  (doseq [title titles]
    (execute connection update-manga-titles [mangadb_id title]))
  (doseq [{:strs [related_id relation]} relations]
    (execute connection update-manga-relations [mangadb_id related_id relation])))

(defn- update-manga [connection
                    {:strs [mangadb_id title type chapters volumes status start end image
                            rank popularity score
                            last_update genres titles relations]}]
  (execute connection
           sync-update-manga
           [mangadb_id title type chapters volumes status start end image])
  (when-not (= last_update 1)
    (execute connection
             update-manga-details
             [mangadb_id rank popularity score])
    (store-manga-side-tables-sync connection mangadb_id genres titles relations)
    (execute connection
             sync-update-manga-details-update
             [mangadb_id last_update])))

(defn store-manga [data-source manga-list]
  (with-transaction data-source connection
    (doseq [manga manga-list]
      (update-manga connection manga))))
