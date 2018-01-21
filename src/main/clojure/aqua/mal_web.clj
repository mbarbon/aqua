(ns aqua.mal-web
  (:require clojure.set
            [clojure.tools.logging :as log]
            aqua.mal-scrape))

(defn mal-fetch [path query-params callback]
  (aqua.mal.Http/get (str "https://myanimelist.net" path)
                     query-params
                     5000
                     callback))

(defn- log-error [error status activity]
  (if error
    (log/warn (str "Error '" (.getMessage error) "' while " activity))
    (log/warn (str "HTTP error " status " while " activity)))
  nil)

(defn fetch-anime-list-cb [username callback]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" "anime"
                                "status" "all"}
    (fn [error status body]
      (try
        (if error
          (callback nil error)
          (callback (aqua.mal.Serialize/readMalAppInfo body) nil))
        (catch Exception e (callback nil e))))))

(defn fetch-anime-details [animedb-id title]
  (mal-fetch (str "/anime/" animedb-id) {}
    (fn [error status body]
      (case status
        200 (let [details (aqua.mal-scrape/parse-anime-page body)
                  titles (:titles details)
                  alternative-titles (clojure.set/difference titles
                                                             (set [title]))]
              (assoc details :titles alternative-titles))
        404 nil
        (log-error error status (str "fetching anime " title))))))

(defn fetch-manga-details [mangadb-id title]
  (mal-fetch (str "/manga/" mangadb-id) {}
    (fn [error status body]
      (case status
        200 (let [details (aqua.mal-scrape/parse-manga-page body)
                  titles (:titles details)
                  alternative-titles (clojure.set/difference titles
                                                             (set [title]))]
              (assoc details :titles alternative-titles))
        404 nil
        (log-error error status (str "fetching manga " title))))))

(defn fetch-active-users []
  (mal-fetch "/users.php" {}
    (fn [error status body]
      (case status
        200 (aqua.mal-scrape/parse-users-page body)
        (log-error error status "fetching user sample")))))

(defn fetch-item-list [type username]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" type
                                "status" "all"}
    (fn [error status body]
      (case status
        200 (aqua.mal.Serialize/readMalAppInfo body)
        (log-error error status (str "fetching anime list for " username))))))

(defn fetch-anime-list [username]
  (fetch-item-list "anime" username))

(defn fetch-manga-list [username]
  (fetch-item-list "manga" username))