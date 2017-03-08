(ns aqua.recommend.collaborative-filter
  )

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
