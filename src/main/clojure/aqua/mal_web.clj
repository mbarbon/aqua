(ns aqua.mal-web
  (:require [clojure.tools.logging :as log]
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

(defn- fetch-item-list-cb [kind username callback]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" kind
                                "status" "all"}
    (fn [error status body _]
      (try
        (cond
          error (callback nil error nil)
          (= status 200) (callback (aqua.mal.Serialize/readMalAppInfo body) nil nil)
          :else (callback nil nil status))
        (catch Exception e (callback nil e))))))

(defn fetch-anime-list-cb [username callback]
  (fetch-item-list-cb "anime" username callback))

(defn fetch-manga-list-cb [username callback]
  (fetch-item-list-cb "manga" username callback))

(defn fetch-anime-details [animedb-id title]
  (mal-fetch (str "/anime/" animedb-id) {}
    (fn [error status body _]
      (case status
        200 (let [details (aqua.mal-scrape/parse-anime-page body)
                  titles (:titles details)
                  alternative-titles (dissoc titles title)]
              (assoc details :titles alternative-titles))
        404 {:missing true}
        503 {:snooze true}
        (log-error error status (str "fetching anime " title))))))

(defn fetch-manga-details [mangadb-id title]
  (mal-fetch (str "/manga/" mangadb-id) {}
    (fn [error status body _]
      (case status
        200 (let [details (aqua.mal-scrape/parse-manga-page body)
                  titles (:titles details)
                  alternative-titles (dissoc titles title)]
              (assoc details :titles alternative-titles))
        404 {:missing true}
        503 {:snooze true}
        (log-error error status (str "fetching manga " title))))))

(defn fetch-active-users []
  (mal-fetch "/users.php" {}
    (fn [error status body _]
      (case status
        200 {:user-sample (aqua.mal-scrape/parse-users-page body)}
        503 {:snooze true}
        (log-error error status "fetching user sample")))))

(defn- fetch-item-list [kind username]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" kind
                                "status" "all"}
    (fn [error status body _]
      (case status
        200 {:mal-app-info (aqua.mal.Serialize/readMalAppInfo body)}
        404 {:snooze true}
        (log-error error status (str "fetching anime list for " username))))))

(defn fetch-anime-list [username]
  (fetch-item-list "anime" username))

(defn fetch-manga-list [username]
  (fetch-item-list "manga" username))