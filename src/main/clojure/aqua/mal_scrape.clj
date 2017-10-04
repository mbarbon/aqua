(ns aqua.mal-scrape
  (:require clojure.set))

(defmacro for-soup [[item items] form]
  `(for [~(vary-meta item assoc :tag `org.jsoup.nodes.Element) ~items]
     ~form))

(def ^:private users-base "https://myanimelist.net/users.php")
(def ^:private anime-base "https://myanimelist.net/anime/318/Hand_Maid_May")

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

(defn- parse-relation-links [links relation]
  (for-soup [link links]
    (let [[_ ^String anime-id] (re-find #"^/anime/(\d+)/" (.attr link "href"))]
      [(Integer/valueOf anime-id) relation])))

(defn- fold-relations [{:keys [relations relation]}
                       ^org.jsoup.nodes.Element cell]
  (let [new-relation-id (relation-names (.text cell))
        links (.select cell "a[href~=^/anime/\\d+/]")]
  (cond
    new-relation-id
      {:relations relations :relation new-relation-id}
    (and relation (seq links))
      {:relation nil
       :relations (reduce conj relations (parse-relation-links links relation))}
    :else
      {:relations relations :relation relation})))

(defn- parse-anime-relations [^org.jsoup.nodes.Document doc]
  (let [relations (.select doc "table.anime_detail_related_anime td")]
    (:relations (reduce fold-relations {:relations {}} relations))))

(defn- parse-anime-genres [^org.jsoup.nodes.Document doc]
  (for-soup [genre (.select doc "span:containsOwn(Genres:) ~ a[href~=/anime/genre/\\d+/]")]
    (let [[_ ^String genre-id] (re-find #"/anime/genre/(\d+)/" (.attr genre "href"))]
      [(Integer/valueOf genre-id) (.text genre)])))

(defn- parse-anime-titles [^org.jsoup.nodes.Document doc]
  (reduce clojure.set/union
    (for-soup [span (.select doc "h2:containsOwn(Alternative Titles) ~ div > span:matchesOwn(English:|Synonyms:)")]
      (if-let [^org.jsoup.nodes.TextNode text-node (.nextSibling span)]
        (if (instance? org.jsoup.nodes.TextNode text-node)
          (set (map #(.trim ^String %) (.split (.text text-node) ","))))))))

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

(defn- parse-anime-stats [^org.jsoup.nodes.Document doc]
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
        titles (parse-anime-titles doc)
        scores (parse-anime-stats doc)]
    {:relations relations
     :genres genres
     :titles titles
     :scores scores}))
