(ns aqua.recommend.collaborative-filter
  )

(defn similar-watched-count [^aqua.mal.data.User usera
                             ^aqua.mal.data.User userb]
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

(defn- score-completed-anime [remove-known-anime user user-rank]
  (into {}
    (for [^aqua.mal.data.Rated item (remove-known-anime (.completed user))]
      ; [(.animedbId item) (- (* (.normalizedRating item) (- user-rank)))])))
      [(.animedbId item) (if (>= (.normalizedRating item) 0)
                           (- (.normalizedRating item))
                           0.5)])))

(defn recommended-completed [similar-users remove-known-anime]
  (let [scored-anime (apply merge-with min
                       (for [[rank similar-user] similar-users]
                         (score-completed-anime remove-known-anime similar-user rank)))
        ranked-anime (sort-by #(% 1) (seq scored-anime))]
    ranked-anime))

(defn- score-airing-anime [keep-airing-anime user user-rank]
  (into {}
    (for [^aqua.mal.data.Rated item (keep-airing-anime (.watching user))]
      [(.animedbId item) user-rank])))

(defn recommended-airing [similar-users keep-airing-anime]
  (let [scored-anime (apply merge-with +
                       (for [[rank similar-user] similar-users]
                         (score-airing-anime keep-airing-anime similar-user rank)))
        ranked-anime (sort-by #(% 1) (seq scored-anime))]
    ranked-anime))
