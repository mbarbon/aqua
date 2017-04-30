(ns aqua.recommend.collaborative-filter
  )

(defn similar-watched-count [^aqua.recommend.CFUser usera
                             ^aqua.recommend.CFUser userb]
  (let [completed-a (.completedCount usera)
        completed-b (.completedCount userb)
        completed-min (min completed-a completed-b)
        completed-max (max completed-a completed-b)]
    (cond
      (= completed-min 0) false
      (= completed-max 0) false
      (and (< completed-min 5) (< completed-max 10)) true
      (< completed-max (* 2 completed-min)) true
      :else false)))

(defn- mutating-merge-with [fun & rest]
  (let [result (java.util.HashMap.)]
    (doseq [merge-item rest]
      (doseq [^java.util.Map$Entry entry merge-item]
        (if-let [current-value (.get result (.getKey entry))]
          (.put result (.getKey entry) (fun current-value (.getValue entry)))
          (.put result (.getKey entry) (.getValue entry)))))
    result))

(defn- score-completed-anime [remove-known-anime
                              ^aqua.recommend.CFUser user
                              user-rank]
  (let [result (java.util.HashMap.)]
    (doseq [^aqua.recommend.CFRated item (remove-known-anime (.completed user))]
      ; [(.animedbId item) (- (* (.normalizedRating user item) (- user-rank)))])))
      (.put result (.animedbId item) (if (>= (.normalizedRating user item) 0)
                                       (- (* (.normalizedRating user item) (- user-rank)))
                                       0.5)))
    result))

(defn- hash-entry-to-pair [^java.util.Map$Entry entry]
  [(.getKey entry) (.getValue entry)])

(defn recommended-completed [similar-users remove-known-anime]
  (let [scored-anime (apply mutating-merge-with min
                       (for [[rank similar-user] similar-users]
                         (score-completed-anime remove-known-anime similar-user rank)))
        ranked-anime (sort-by #(% 1) (map hash-entry-to-pair (seq scored-anime)))]
    ranked-anime))

(defn- score-airing-anime [keep-airing-anime user user-rank]
  (let [result (java.util.HashMap.)]
    (doseq [^aqua.recommend.CFRated item (keep-airing-anime (.watching user))]
      (.put result (.animedbId item) user-rank))
    result))

(defn recommended-airing [similar-users keep-airing-anime]
  (let [scored-anime (apply mutating-merge-with +
                       (for [[rank similar-user] similar-users]
                         (score-airing-anime keep-airing-anime similar-user rank)))
        ranked-anime (sort-by #(% 1) (map hash-entry-to-pair (seq scored-anime)))]
    ranked-anime))
