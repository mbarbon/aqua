(ns aqua.mal-web
  (:require [clojure.tools.logging :as log]
            aqua.mal-scrape))

(def ^:private missing-user (aqua.mal.data.MalAppInfo.))

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

(defn- fetch-malappinfo-list-cb [kind username callback]
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

(defn- fetch-profile-page [username]
  (mal-fetch (str "/profile/" username) {}
    (fn [error status body _]
      (case status
        200 {:profile (aqua.mal-scrape/parse-profile-page body)}
        404 {:missing true}
        429 {:throttle true}
        503 {:snooze true}
        (log-error error status "fetching profile page")))))

(defn- fetch-item-list-cb [kind username partial callback]
  (letfn [(handle-response [body]
            (let [item-list (if (= kind "anime")
                              (aqua.mal.Serialize/readAnimeList body)
                              (aqua.mal.Serialize/readMangaList body))]
              (when (seq item-list)
                (.addAll partial item-list)
                (Thread/sleep 750)
                (fetch-step))
              (when-not (seq item-list)
                (callback partial nil nil))))
          (fetch-step []
            (mal-fetch (format "/%slist/%s/load.json" kind username)
                       {"status" "7"
                        "offset" (str (count partial))}
              (fn [error status body _]
                (try
                  (cond
                    error (callback nil error nil)
                    (= status 200) (handle-response body)
                    :else (callback nil nil status))
                  (catch Exception e (callback nil e nil))))))]
    (fetch-step)))

(defn- fetch-item-list [kind username partial]
  (let [answer (promise)]
    (fetch-item-list-cb kind username partial
      (fn [items error status]
        (try
          (cond
            (= status 404) (deliver answer {:snooze true})
            (= status 429) (deliver answer {:throttle true})
            (= status 400) (deliver answer {:items []}) ; Private list
            (or status error) (do
                                (log-error error status (str "fetching " kind " list for " username))
                                (deliver answer nil))
            :else (deliver answer {:items items}))
          (catch Exception e (deliver answer nil)))))
    answer))

(defn fetch-anime-list-sync [username]
  (let [items-res @(fetch-item-list "anime" username (java.util.ArrayList.))
        profile-res @(fetch-profile-page username)
        profile (:profile profile-res)]
    (cond
      (or (nil? items-res) (nil? profile-res)) nil
      (:missing profile-res) {:mal-app-info missing-user}
      (or (:snooze items-res) (:snooze profile-res)) {:snooze true}
      (or (:throttle items-res) (:throttle profile-res)) {:throttle true}
      :else {:mal-app-info
              (aqua.mal.data.ListPageItem/makeAnimeList
                (:user-id profile)
                (:username profile)
                (if (zero? (:anime-count profile))
                  0
                  (:anime-update profile))
                (:items items-res))})))

(defn fetch-manga-list-sync [username]
  (let [items-res @(fetch-item-list "manga" username (java.util.ArrayList.))
        profile-res @(fetch-profile-page username)
        profile (:profile profile-res)]
    (cond
      (or (nil? items-res) (nil? profile-res)) nil
      (:missing profile-res) {:mal-app-info missing-user}
      (or (:snooze items-res) (:snooze profile-res)) {:snooze true}
      (or (:throttle items-res) (:throttle profile-res)) {:throttle true}
      :else {:mal-app-info
              (aqua.mal.data.ListPageItem/makeMangaList
                (:user-id profile)
                (:username profile)
                (if (zero? (:manga-count profile))
                  0
                  (:manga-update profile))
                (:items items-res))})))

(defn fetch-anime-list-cb [username callback]
  (future
    (try
      (let [res (fetch-anime-list-sync username)]
        (cond
          (nil? res) (callback nil "Unknown" nil)
          (:snooze res) (callback nil "Snooze" nil)
          (:throttle res) (callback nil nil 429)
          (:mal-app-info res) (callback (:mal-app-info res) nil nil)))
      (catch Exception e (callback nil e nil)))))

(defn fetch-manga-list-cb [username callback]
  (future
    (try
      (let [res (fetch-manga-list-sync username)]
        (cond
          (nil? res) (callback nil "Unknown" nil)
          (:snooze res) (callback nil "Snooze" nil)
          (:throttle res) (callback nil nil 429)
          (:mal-app-info res) (callback (:mal-app-info res) nil nil)))
      (catch Exception e (callback nil e nil)))))

(defn fetch-anime-details [animedb-id title]
  (mal-fetch (str "/anime/" animedb-id) {}
    (fn [error status body _]
      (case status
        200 (let [details (aqua.mal-scrape/parse-anime-page body)
                  titles (:titles details)
                  alternative-titles (dissoc titles title)]
              (assoc details :titles alternative-titles))
        404 {:missing true}
        429 {:throttle true}
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
        429 {:throttle true}
        503 {:snooze true}
        (log-error error status (str "fetching manga " title))))))

(defn fetch-active-users []
  (mal-fetch "/users.php" {}
    (fn [error status body _]
      (case status
        200 {:user-sample (aqua.mal-scrape/parse-users-page body)}
        429 {:throttle true}
        503 {:snooze true}
        (log-error error status "fetching user sample")))))

(defn- fetch-malappinfo-list [kind username]
  (mal-fetch "/malappinfo.php" {"u" username
                                "type" kind
                                "status" "all"}
    (fn [error status body _]
      (case status
        200 {:mal-app-info (aqua.mal.Serialize/readMalAppInfo body)}
        404 {:snooze true}
        429 {:throttle true}
        (log-error error status (str "fetching anime list for " username))))))

(defn fetch-anime-list [username]
  (future (fetch-anime-list-sync username)))

(defn fetch-manga-list [username]
  (future (fetch-manga-list-sync username)))
