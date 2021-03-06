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
      (doseq [^aqua.recommend.ScoredRated entry merge-item]
        ; ScoredRated objects can be used as keys (on the anime id)
        (if-let [^aqua.recommend.ScoredRated current-entry (.get result entry)]
          (set! (.score current-entry) (fun (.score current-entry) (.score entry)))
          (.put result entry entry))))
    result))

(defn- score-completed-anime [remove-known-anime
                              ^aqua.recommend.CFUser user
                              user-rank]
  (let [result (java.util.ArrayList.)]
    (doseq [^aqua.recommend.CFRated item (remove-known-anime (.completed user))]
      ; other-score: (- (* (.normalizedRating user item) (- user-rank)))
      (let [score (if (>= (.normalizedRating user item) 0)
                    (- (* (.normalizedRating user item) (- user-rank)))
                    0.5)]
        (.add result (aqua.recommend.ScoredRated. item score))))
    result))

(defn- hash-entry-to-pair [^java.util.Map$Entry entry]
  [(.getKey entry) (.getValue entry)])

(defn recommended-completed [similar-users remove-known-anime]
  (let [scored-anime (apply mutating-merge-with min
                       (for [^aqua.recommend.ScoredUser scored-user similar-users]
                         (score-completed-anime remove-known-anime (.user scored-user) (.score scored-user))))
        ranked-anime (java.util.ArrayList. (.values scored-anime))]
    (.sort ranked-anime aqua.recommend.ScoredRated/SORT_SCORE)
    ranked-anime))

(defn- score-airing-anime [keep-airing-anime
                           ^aqua.recommend.CFUser user
                           user-rank]
  (let [result (java.util.ArrayList.)]
    (doseq [^aqua.recommend.CFRated item (keep-airing-anime (.watching user))]
      (.add result (aqua.recommend.ScoredRated. item user-rank)))
    result))

(defn recommended-airing [similar-users keep-airing-anime]
  (let [scored-anime (apply mutating-merge-with +
                       (for [^aqua.recommend.ScoredUser scored-user similar-users]
                         (score-airing-anime keep-airing-anime (.user scored-user) (.score scored-user))))
        ranked-anime (java.util.ArrayList. (.values scored-anime))]
    (.sort ranked-anime aqua.recommend.ScoredRated/SORT_SCORE)
    ranked-anime))
