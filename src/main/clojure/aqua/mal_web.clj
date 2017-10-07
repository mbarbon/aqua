(ns aqua.mal-web
  (:require org.httpkit.client
            clojure.set
            [clojure.tools.logging :as log]
            aqua.mal-scrape))

(defn- mal-fetch [path query-params callback]
  (let [http-options {:timeout 10000
                      :as :stream
                      :query-params query-params
                      :headers {"Accept-Encoding" "gzip"}}]
    (org.httpkit.client/get (str "http://myanimelist.net" path)
                            http-options
                            callback)))

(defn- log-error [error status activity]
  (if error
    (log/warn (str "Error '" (.getMessage error) "' while " activity))
    (log/warn (str "HTTP error " status " while " activity)))
  nil)

(defn fetch-anime-list-cb [username callback]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" "anime"
                                "status" "all"}
    (fn [{:keys [status headers body error]}]
      (try
        (if error
          (callback nil error)
          (callback (aqua.mal.Json/readMalAppInfo body) nil))
        (catch Exception e (callback nil e))))))

(defn fetch-anime-details [animedb-id title]
  (mal-fetch (str "/anime/" animedb-id) {}
    (fn [{:keys [status body error]}]
      (case status
        200 (let [details (aqua.mal-scrape/parse-anime-page body)
                  titles (:titles details)
                  alternative-titles (clojure.set/difference titles
                                                             (set [title]))]
              (assoc details :titles alternative-titles))
        404 nil
        (log-error error status (str "fetching anime " title))))))

(defn fetch-active-users []
  (mal-fetch "/users.php" {}
    (fn [{:keys [status body error]}]
      (case status
        200 (aqua.mal-scrape/parse-users-page body)
        (log-error error status "fetching user sample")))))

(defn fetch-anime-list [username]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" "anime"
                                "status" "all"}
    (fn [{:keys [status body error]}]
      (case status
        200 (aqua.mal.Json/readMalAppInfo body)
        (log-error error status (str "fetching anime list for " username))))))
