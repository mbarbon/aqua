(ns aqua.web.render
  (:require clojure.string))

(def ^:private jst-tz (java.time.ZoneId/of "Asia/Tokyo"))

(defn- format-season [timestamp]
  (let [millis (* 1000 timestamp)
        date (.toLocalDate (.atZone (java.time.Instant/ofEpochMilli millis) jst-tz))
        month (.getMonthValue date)
        season (cond
                 (<= month 3) "Winter"
                 (<= month 6) "Spring"
                 (<= month 9) "Fall"
                 :else        "Winter")]
    (str season " " (.getYear date))))

(defn render-anime [^aqua.mal.data.Anime anime tags]
  {:animedbId (.animedbId anime)
   :title (.title anime)
   :image (.image anime)
   :episodes (.episodes anime)
   :franchiseEpisodes (if-let [franchise (.franchise anime)]
                        (.episodes franchise)
                        0)
   :genres (clojure.string/join ", " (.genres anime))
   :season (if-not (zero? (.startedAiring anime))
             (format-season (.startedAiring anime))
             "Unknown season")
   :tags tags
   :seriesType (.seriesType anime)
   :status (.status anime)})

(defn rewrite-resources [response]
  (let [body-string (slurp (:body response))
        rewritten-body (clojure.string/replace body-string
                                               "//cdnjs.cloudflare.com/ajax/libs/"
                                               "/cdn-libs/")]
   (assoc response
     :headers (assoc (:headers response)
                     "Content-Length"
                     (str (count rewritten-body)))
     :body rewritten-body)))
