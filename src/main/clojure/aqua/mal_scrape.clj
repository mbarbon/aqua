(ns aqua.mal-scrape
  )

(defmacro for-soup [[item items] form]
  `(for [~(vary-meta item assoc :tag `org.jsoup.nodes.Element) ~items]
     ~form))

(def ^:private users-base "https://myanimelist.net/users.php")

(defn parse-users-page [stream]
  (let [doc (org.jsoup.Jsoup/parse stream "utf-8" users-base)
        users (.select doc "a:has(img)[href^=/profile/]")]
    (for-soup [user users]
      (.substring (.attr user "href") 9))))
