(ns aqua.web.render
  )

(def ^:private jst-tz (java.time.ZoneId/of "Asia/Tokyo"))

(defn- to-date ^java.time.LocalDate [timestamp]
  (.toLocalDate (.atZone (java.time.Instant/ofEpochMilli (* 1000 timestamp)) jst-tz)))

(defn- format-season [timestamp]
  (let [date (to-date timestamp)
        month (.getMonthValue date)
        season (cond
                 (<= month 3) "Winter"
                 (<= month 6) "Spring"
                 (<= month 9) "Fall"
                 :else        "Winter")]
    (str season " " (.getYear date))))

(defn- format-published [timestamp-from timestamp-until]
  (let [year-from (.getYear (to-date timestamp-from))
        year-until (if-not (zero? timestamp-until)
                     (.getYear (to-date timestamp-until)))]
    (cond 
      (= year-from year-until) (str year-from)
      year-until (str year-from " - " year-until)
      :else (str year-from " - in progress"))))

(defn render-anime [^aqua.mal.data.Anime anime tags]
  {:animedbId (.animedbId anime)
   :itemdbId (.animedbId anime)
   :title (.title anime)
   :image (.localImage anime "/images/cover/")
   :smallImage (.smallLocalImage anime "/images/cover/")
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

(defn render-manga [^aqua.mal.data.Manga manga tags]
  {:mangadbId (.mangadbId manga)
   :itemdbId (.mangadbId manga)
   :title (.title manga)
   :image (.localImage manga "/images/cover/")
   :smallImage (.smallLocalImage manga "/images/cover/")
   :chapters (.chapters manga)
   :franchiseChapters (if-let [franchise (.franchise manga)]
                        (.chapters franchise)
                        0)
   :genres (clojure.string/join ", " (.genres manga))
   :published (if-not (zero? (.startedPublishing manga))
                (format-published (.startedPublishing manga) (.endedPublishing manga))
                "Unknown publishing date")
   :tags tags
   :seriesType (.seriesType manga)
   :status (.status manga)})

(defn add-medium-cover [^aqua.mal.data.Anime anime rendered-anime]
  (assoc rendered-anime
         :mediumImage (.mediumLocalImage anime "/images/cover/")))
