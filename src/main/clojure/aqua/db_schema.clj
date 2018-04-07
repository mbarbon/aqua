(ns aqua.db-schema
  (:require clojure.string))

(def ^:private create-users
  (str "CREATE TABLE IF NOT EXISTS users ("
       "    user_id INTEGER PRIMARY KEY,"
       "    username VARCHAR(20) NOT NULL,"
       "    last_update INTEGER NOT NULL,"
       "    last_change INTEGER NOT NULL,",
       "    last_anime_change INTEGER NOT NULL DEFAULT 0,",
       "    last_manga_change INTEGER NOT NULL DEFAULT 0",
       ")"))

(def ^:private create-users-username-index
  (str "CREATE INDEX IF NOT EXISTS username_index"
       "    ON users (username)"))

(def ^:private create-users-last-change-index
  (str "CREATE INDEX IF NOT EXISTS users_last_change_index"
       "    ON users (last_change)"))

(def ^:private create-users-last-anime-change-index
  (str "CREATE INDEX IF NOT EXISTS users_last_anime_change_index"
       "    ON users (last_anime_change)"))

(def ^:private create-users-last-manga-change-index
  (str "CREATE INDEX IF NOT EXISTS users_last_manga_change_index"
       "    ON users (last_manga_change)"))

(def ^:private create-user-anime-stats
  (str "CREATE TABLE IF NOT EXISTS user_anime_stats ("
       "    user_id INTEGER PRIMARY KEY,"
       "    planned INTEGER NOT NULL,"
       "    watching INTEGER NOT NULL,"
       "    completed INTEGER NOT NULL,"
       "    onhold INTEGER NOT NULL,"
       "    dropped INTEGER NOT NULL"
       ")"))

(def ^:private create-user-manga-stats
  (str "CREATE TABLE IF NOT EXISTS user_manga_stats ("
       "    user_id INTEGER PRIMARY KEY,"
       "    planned INTEGER NOT NULL,"
       "    reading INTEGER NOT NULL,"
       "    completed INTEGER NOT NULL,"
       "    onhold INTEGER NOT NULL,"
       "    dropped INTEGER NOT NULL"
       ")"))

(def ^:private create-anime
  (str "CREATE TABLE IF NOT EXISTS anime ("
       "    animedb_id INTEGER PRIMARY KEY,"
       "    title VARCHAR(255) NOT NULL,"
       "    type INTEGER NOT NULL,"
       "    episodes INTEGER NOT NULL,"
       "    status INTEGER NOT NULL,"
       "    start INTEGER, end INTEGER,"
       "    image VARCHAR(255) NOT NULL"
       ")"))

(def ^:private create-anime-details
  (str "CREATE TABLE IF NOT EXISTS anime_details ("
       "    animedb_id INTEGER PRIMARY KEY,"
       "    rank INTEGER NOT NULL,"
       "    popularity INTEGER NOT NULL,"
       "    score INTEGER NOT NULL"
       ")"))

(def ^:private create-anime-relations
  (str "CREATE TABLE IF NOT EXISTS anime_relations ("
       "    animedb_id INTEGER NOT NULL,"
       "    related_id INTEGER NOT NULL,"
       "    relation INTEGER NOT NULL"
       ")"))

(def ^:private create-anime-relations-index
  (str "CREATE UNIQUE INDEX IF NOT EXISTS anime_relations_index"
       "    ON anime_relations (animedb_id, related_id)"))

(def ^:private create-anime-genres
  (str "CREATE TABLE IF NOT EXISTS anime_genres ("
       "    animedb_id INTEGER NOT NULL,"
       "    genre_id INTEGER NOT NULL,"
       "    sort_order INTEGER NOT NULL"
       ")"))

(def ^:private create-anime-titles
  (str "CREATE TABLE IF NOT EXISTS anime_titles ("
       "    animedb_id INTEGER NOT NULL,"
       "    title_type INTEGER NOT NULL,"
       "    title VARCHAR(255) NOT NULL"
       ")"))

(def ^:private create-anime-genres-index
  (str "CREATE UNIQUE INDEX IF NOT EXISTS anime_genres_index"
       "    ON anime_genres (animedb_id, genre_id)"))

(def ^:private create-anime-details-update
  (str "CREATE TABLE IF NOT EXISTS anime_details_update ("
       "    animedb_id INTEGER PRIMARY KEY,"
       "    last_update INTEGER NOT NULL"
       ")"))

(def ^:private create-anime-list
  (str "CREATE TABLE IF NOT EXISTS anime_list ("
       "    user_id INTEGER NOT NULL PRIMARY KEY,"
       "    anime_list BLOB NOT NULL,"
       "    anime_list_format INTEGER NOT NULL DEFAULT 0"
       ")"))

(def ^:private create-manga
  (str "CREATE TABLE IF NOT EXISTS manga ("
       "    mangadb_id INTEGER PRIMARY KEY,"
       "    title VARCHAR(255) NOT NULL,"
       "    type INTEGER NOT NULL,"
       "    chapters INTEGER NOT NULL,"
       "    volumes INTEGER NOT NULL,"
       "    status INTEGER NOT NULL,"
       "    start INTEGER, end INTEGER,"
       "    image VARCHAR(255) NOT NULL"
       ")"))

(def ^:private create-manga-details
  (str "CREATE TABLE IF NOT EXISTS manga_details ("
       "    mangadb_id INTEGER PRIMARY KEY,"
       "    rank INTEGER NOT NULL,"
       "    popularity INTEGER NOT NULL,"
       "    score INTEGER NOT NULL"
       ")"))

(def ^:private create-manga-relations
  (str "CREATE TABLE IF NOT EXISTS manga_relations ("
       "    mangadb_id INTEGER NOT NULL,"
       "    related_id INTEGER NOT NULL,"
       "    relation INTEGER NOT NULL"
       ")"))

(def ^:private create-manga-relations-index
  (str "CREATE UNIQUE INDEX IF NOT EXISTS manga_relations_index"
       "    ON manga_relations (mangadb_id, related_id)"))

(def ^:private create-manga-genres
  (str "CREATE TABLE IF NOT EXISTS manga_genres ("
       "    mangadb_id INTEGER NOT NULL,"
       "    genre_id INTEGER NOT NULL,"
       "    sort_order INTEGER NOT NULL"
       ")"))

(def ^:private create-manga-titles
  (str "CREATE TABLE IF NOT EXISTS manga_titles ("
       "    mangadb_id INTEGER NOT NULL,"
       "    title_type INTEGER NOT NULL,"
       "    title VARCHAR(255) NOT NULL"
       ")"))

(def ^:private create-manga-genres-index
  (str "CREATE UNIQUE INDEX IF NOT EXISTS manga_genres_index"
       "    ON manga_genres (mangadb_id, genre_id)"))

(def ^:private create-manga-details-update
  (str "CREATE TABLE IF NOT EXISTS manga_details_update ("
       "    mangadb_id INTEGER PRIMARY KEY,"
       "    last_update INTEGER NOT NULL"
       ")"))

(def ^:private create-manga-list
  (str "CREATE TABLE IF NOT EXISTS manga_list ("
       "    user_id INTEGER NOT NULL PRIMARY KEY,"
       "    manga_list BLOB NOT NULL"
       ")"))

(def ^:private create-relation-names
  (str "CREATE TABLE IF NOT EXISTS relation_names ("
       "    relation INTEGER PRIMARY KEY,"
       "    description VARCHAR(30)"
       ")"))

(def ^:private insert-relation-name-1
  (str "INSERT OR REPLACE INTO relation_names VALUES (1, 'side_story')"))
(def ^:private insert-relation-name-2
  (str "INSERT OR REPLACE INTO relation_names VALUES (2, 'alternative_version')"))
(def ^:private insert-relation-name-3
  (str "INSERT OR REPLACE INTO relation_names VALUES (3, 'sequel')"))
(def ^:private insert-relation-name-4
  (str "INSERT OR REPLACE INTO relation_names VALUES (4, 'other')"))
(def ^:private insert-relation-name-5
  (str "INSERT OR REPLACE INTO relation_names VALUES (5, 'prequel')"))
(def ^:private insert-relation-name-6
  (str "INSERT OR REPLACE INTO relation_names VALUES (6, 'parent_story')"))
(def ^:private insert-relation-name-7
  (str "INSERT OR REPLACE INTO relation_names VALUES (7, 'full_story')"))

(def ^:private create-anime-genre-names
  (str "CREATE TABLE IF NOT EXISTS anime_genre_names ("
       "    genre INTEGER PRIMARY KEY,"
       "    description VARCHAR(30)"
       ")"))

(def ^:private create-manga-genre-names
  (str "CREATE TABLE IF NOT EXISTS manga_genre_names ("
       "    genre INTEGER PRIMARY KEY,"
       "    description VARCHAR(30)"
       ")"))

(def ^:private create-refresh-queue
  (str "CREATE TABLE IF NOT EXISTS user_refresh_queue ("
       "    username VARCHAR(20) PRIMARY KEY,"
       "    insert_time INTEGER NOT NULL,"
       "    status INTEGER NOT NULL,"
       "    attempts INTEGER NOT NULL,"
       "    status_change INTEGER NOT NULL"
       ")"))

(def statements (map #(clojure.string/replace % #"\s+" " ") [
  create-users
  create-users-username-index
  create-users-last-change-index
  create-user-anime-stats
  create-user-manga-stats
  create-anime
  create-anime-details
  create-anime-relations
  create-anime-relations-index
  create-anime-genres
  create-anime-titles
  create-anime-genres-index
  create-anime-details-update
  create-anime-list
  create-manga
  create-manga-details
  create-manga-relations
  create-manga-relations-index
  create-manga-genres
  create-manga-titles
  create-manga-genres-index
  create-manga-details-update
  create-manga-list
  create-relation-names
  insert-relation-name-1
  insert-relation-name-2
  insert-relation-name-3
  insert-relation-name-4
  insert-relation-name-5
  insert-relation-name-6
  insert-relation-name-7
  create-anime-genre-names
  create-manga-genre-names
  create-refresh-queue]))
