(ns aqua.mal-scrape
  (:require [clojure.string]))

(defmacro for-soup [[item items] form]
  `(for [~(vary-meta item assoc :tag `org.jsoup.nodes.Element) ~items]
     ~form))

(def ^:private users-base "https://myanimelist.net/users.php")
(def ^:private anime-base "https://myanimelist.net/anime/318/Hand_Maid_May")
(def ^:private manga-base "https://myanimelist.net/manga/318/Futari_Ecchi")
(def ^:private profile-base "https://myanimelist.net/profile/mattia_y")

(def ^:private pst-tz  (java.time.ZoneId/of "America/Los_Angeles"))

(def ^:private relation-names
  {"Side story:" 1
   "Alternative version:" 2
   "Sequel:" 3
   ; way too random (e.g. might include commercials)
   ; "Other:" 4
   "Prequel:" 5
   "Parent story:" 6
   "Full story:" 7})

(defn parse-users-page [stream]
  (let [doc (org.jsoup.Jsoup/parse stream "utf-8" users-base)
        users (.select doc "a:has(img)[href^=/profile/]")]
    (for-soup [user users]
      (.substring (.attr user "href") 9))))

(defn- parse-relation-links [link-regexp links relation]
  (for-soup [link links]
    (let [[_ ^String anime-id] (re-find link-regexp (.attr link "href"))]
      [(Integer/valueOf anime-id) relation])))

(defn- fold-relations [link-selector
                       link-regexp
                       {:keys [relations relation]}
                       ^org.jsoup.nodes.Element cell]
  (let [new-relation-id (relation-names (.text cell))
        links (.select cell link-selector)]
  (cond
    new-relation-id
      {:relations relations :relation new-relation-id}
    (and relation (seq links))
      {:relation nil
       :relations (reduce conj relations (parse-relation-links link-regexp
                                                               links
                                                               relation))}
    :else
      {:relations relations :relation relation})))

(defn- parse-anime-relations [^org.jsoup.nodes.Document doc]
  (let [relations (.select doc "table.anime_detail_related_anime td")]
    (:relations (reduce (partial fold-relations "a[href~=^/anime/\\d+/]"
                                                #"^/anime/(\d+)/")
                        {:relations {}} relations))))

(defn- parse-manga-relations [^org.jsoup.nodes.Document doc]
  (let [relations (.select doc "table.anime_detail_related_anime td")]
    (:relations (reduce (partial fold-relations "a[href~=^/manga/\\d+/]"
                                                #"^/manga/(\d+)/")
                        {:relations {}} relations))))

(defn- parse-anime-genres [^org.jsoup.nodes.Document doc]
  (for-soup [genre (.select doc "span:containsOwn(Genres:) ~ a[href~=/anime/genre/\\d+/]")]
    (let [[_ ^String genre-id] (re-find #"/anime/genre/(\d+)/" (.attr genre "href"))]
      [(Integer/valueOf genre-id) (.text genre)])))

(defn- parse-manga-genres [^org.jsoup.nodes.Document doc]
  (for-soup [genre (.select doc "span:containsOwn(Genres:) ~ a[href~=/manga/genre/\\d+/]")]
    (let [[_ ^String genre-id] (re-find #"/manga/genre/(\d+)/" (.attr genre "href"))]
      [(Integer/valueOf genre-id) (.text genre)])))

(defn- parse-titles [^org.jsoup.nodes.Document doc]
  (apply merge
    (for-soup [span (.select doc "h2:containsOwn(Alternative Titles) ~ div > span:matchesOwn(English:|Synonyms:)")]
      (if-let [^org.jsoup.nodes.TextNode text-node (.nextSibling span)]
        (if (instance? org.jsoup.nodes.TextNode text-node)
          (let [titles (map #(.trim ^String %) (.split (.text text-node) ","))
                kind-text (.trim (.text span))
                title-kind (case kind-text
                             "English:" 1
                             "Synonyms:" 2)]
              (into {} (map vector titles (repeat title-kind)))))))))

(defn- parse-score [scores stats]
  (if-let [score (first (.select stats "div.score:matchesOwn(\\d+\\.?\\d+)"))]
    (assoc scores :score (int (* 100 (Double/valueOf (.text score)))))
    scores))

(defn- parse-rank [scores stats]
  (if-let [rank (first (.select stats "span.ranked:matches(#\\d+)"))]
    (let [[_ value] (re-find #"#(\d+)" (.text rank))]
      (assoc scores :rank (Integer/valueOf value)))
    scores))

(defn- parse-popularity [scores stats]
  (if-let [popularity (first (.select stats "span.popularity:matches(#\\d+)"))]
    (let [[_ value] (re-find #"#(\d+)" (.text popularity))]
      (assoc scores :popularity (Integer/valueOf value)))
    scores))

(defn- parse-stats [^org.jsoup.nodes.Document doc]
  (let [scores {:score 0 :rank 0 :popularity 0}]
    (if-let [stats (first (.select doc "div.stats-block"))]
       (-> scores
           (parse-rank stats)
           (parse-score stats)
           (parse-popularity stats))
       scores)))

(defn parse-anime-page [stream]
  (let [doc (org.jsoup.Jsoup/parse stream "utf-8" anime-base)
        relations (parse-anime-relations doc)
        genres (parse-anime-genres doc)
        titles (parse-titles doc)
        scores (parse-stats doc)]
    {:relations relations
     :genres genres
     :titles titles
     :scores scores}))

(defn parse-manga-page [stream]
  (let [doc (org.jsoup.Jsoup/parse stream "utf-8" manga-base)
        relations (parse-manga-relations doc)
        genres (parse-manga-genres doc)
        titles (parse-titles doc)
        scores (parse-stats doc)]
    {:relations relations
     :genres genres
     :titles titles
     :scores scores}))

(defn- parse-date-parser-for-formatter [builder]
  (let [formatter (.toFormatter builder java.util.Locale/US)]
    (fn [lower]
      (try
        (.atZone (java.time.LocalDateTime/parse lower formatter) pst-tz)
        (catch java.time.format.DateTimeParseException e
          nil)))))

(defn- parse-yesterday [builder]
  (let [formatter (.toFormatter builder java.util.Locale/US)]
    (fn [lower]
      (try
        (if (clojure.string/starts-with? lower "yesterday, ")
          (.atZone (java.time.LocalDateTime/parse (subs lower 11) formatter) pst-tz))
        (catch java.time.format.DateTimeParseException e
          nil)))))

(defn- parse-hour-ago [now]
  (fn [lower]
    (let [[_ delta] (re-find #"^([0-9]+) hours? ago$" lower)]
      (if delta
        (.withSecond (.withMinute (.minusHours now (Long/valueOf delta)) 0) 0)))))

(defn- parse-minute-ago [now]
  (fn [lower]
    (let [[_ delta] (re-find #"^([0-9]+) minutes? ago$" lower)]
      (if delta
        (.withSecond (.minusMinutes now (Long/valueOf delta)) 0)))))

(defn- parse-second-ago [now]
  (fn [lower]
    (let [[_ delta] (re-find #"^([0-9]+) seconds? ago$" lower)]
      (if delta
        (.minusSeconds now (Long/valueOf delta))))))

(defn- formatter-builder []
  (-> (java.time.format.DateTimeFormatterBuilder.)
      (.parseCaseInsensitive)))

(defn- date-parsers []
  (let [now (java.time.ZonedDateTime/now pst-tz)
        yesterday (.minusDays now 1)
        full-date (-> (formatter-builder)
                      (.appendPattern "MMM d, yyyy h:m a"))
        missing-year (-> (formatter-builder)
                         (.appendPattern "MMM d, h:m a")
                         (.parseDefaulting java.time.temporal.ChronoField/YEAR (.getYear now)))
        time-yesterday (-> (formatter-builder)
                           (.appendPattern "h:m a")
                           (.parseDefaulting java.time.temporal.ChronoField/YEAR (.getYear yesterday))
                           (.parseDefaulting java.time.temporal.ChronoField/MONTH_OF_YEAR (.getMonthValue yesterday))
                           (.parseDefaulting java.time.temporal.ChronoField/DAY_OF_MONTH (.getDayOfMonth yesterday)))]
  [(parse-date-parser-for-formatter full-date)
   (parse-date-parser-for-formatter missing-year)
   (parse-yesterday time-yesterday)
   (parse-hour-ago now)
   (parse-minute-ago now)
   (parse-second-ago now)]))

(defn parse-fuzzy-date [str]
  (let [lower (clojure.string/lower-case str)
        parsed (some identity
                 (for [parser (date-parsers)]
                   (parser lower)))]
    (if parsed
      (.toEpochSecond parsed))))

(defn- parse-update-time [spans]
  (if-let [date-span (.first spans)]
    (parse-fuzzy-date (.text date-span))
    0))

(defn- parse-comments-link [link-list]
  ; https://myanimelist.net/comments.php?id=5621220
  (if-let [link (first link-list)]
    (let [[_ user-id] (re-find #"/comments\.php\?id=(\d+)" (.attr link "href"))]
      (Long/valueOf user-id))))

(defn- parse-sns-link [link-list]
  ; https://myanimelist.net/rss.php?type=blog&id=5661231
  (if-let [link (first link-list)]
    (let [[_ user-id] (re-find #"/rss\.php\?type=\w+&id=(\d+)" (.attr link "href"))]
      (Long/valueOf user-id))))

(defn- parse-list-link [link-list]
  ; https://myanimelist.net/animelist/mattia_y
  (if-let [link (first link-list)]
    (let [[_ username] (re-find #"/animelist/(.+)" (.attr link "href"))]
      username)))

(defn- parse-count [anime-stats]
  (if-let [total (first anime-stats)]
    (Long/valueOf (clojure.string/replace (.text total) "," ""))))

(defn parse-profile-page [stream]
  (let [doc (org.jsoup.Jsoup/parse stream "utf-8" profile-base)
        anime-stats (.select doc "div.anime.stats ul.stats-data li span:eq(1)")
        manga-stats (.select doc "div.manga.stats ul.stats-data li span:eq(1)")
        anime-updates (.select doc "div.anime.updates div.data div span")
        manga-updates (.select doc "div.manga.updates div.data div span")
        user-id-comments (parse-comments-link (.select doc "div.user-comments h2 a[href*=comments.php]"))
        user-id-sns (parse-sns-link (.select doc "div.user-profile-sns a[href~=rss\\.php.*\\bid=]"))
        user-id (or user-id-comments user-id-sns)
        username (parse-list-link (.select doc "div.user-profile div.user-button a[href*=/animelist/]"))]
    (if (and user-id username)
      {:anime-update (parse-update-time anime-updates)
       :manga-update (parse-update-time manga-updates)
       :anime-count (parse-count anime-stats)
       :manga-count (parse-count manga-stats)
       :user-id user-id
       :username username})))
