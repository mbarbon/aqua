(ns aqua.compare.recall
  (:require aqua.misc
            aqua.compare.misc
            aqua.recommend.cosine
            aqua.recommend.pearson))

; Compare single-item recall for highly-rated items
;
; It picks up to 5 very highly-rated items and 2 less highly rated items
; (all items have a normalized rating of at least 0.6 above average)
; and counts how many of those are returned amongst the first 30 items
; returned by the recommender

; return an [user, set-of-highly-rated-anime] pair
(defn- make-test-entry [user]
  (let [completed-good (filter #(>= (.normalizedRating user %) 0.6) (.completed user))
        total (count completed-good)
        by-rating (sort-by #(.normalizedRating user %) (aqua.compare.misc/stable-shuffle completed-good))
        mid-value (take 2 (drop (/ total 2) by-rating))
        high-value (take 5 (drop (- total 5) by-rating))]
    (if (<= total 10)
      [user #{}]
      [user (set (concat high-value mid-value))])))

(defn make-test-users-list [list-size users]
  (into []
    (map make-test-entry
      (take list-size
        (remove #(< (count (seq (.completed %))) 20)
          users)))))

(defn normalize-test-users [test-users watched-zero dropped-zero]
  (aqua.misc/normalize-all-ratings (map first test-users) watched-zero dropped-zero))

(defn- score-user-anime [recommender test-user test-anime anime-map]
  (let [filtered-user (.removeAnime test-user (.animedbId test-anime))
        known-anime-filter (aqua.misc/make-filter filtered-user anime-map)
        [_ ranked-anime] (recommender filtered-user known-anime-filter)
        test-anime-rank ((fn [index [scored-anime & rest]]
                          (cond
                            (nil? scored-anime) nil
                            (= (.animedbId scored-anime) (.animedbId test-anime)) index
                            (.tags scored-anime) (recur index rest)
                            :else (recur (+ 1 index) rest))) 0 ranked-anime)]
    (if (and test-anime-rank (<= test-anime-rank 30))
      1
      0)))

(defn- score-recommender [recommender test-users anime-map]
  (let [scores (for [[test-user test-anime] test-users]
                 (let [known-anime-tagger (aqua.misc/make-tagger test-user anime-map)
                       user-recommender (fn [filtered-user known-anime-filter]
                                          (known-anime-tagger
                                            (recommender filtered-user known-anime-filter)))
                       score (apply + (map #(score-user-anime user-recommender test-user % anime-map) test-anime))]
                   [score (count test-anime)]))]
    (/ (apply + (map first scores)) (apply + (map second scores)))))

(defn make-score-pearson [users min-common-items]
  (partial score-recommender #(aqua.recommend.pearson/get-recommendations min-common-items %1 users %2)))

(defn make-score-cosine [users]
  (partial score-recommender #(aqua.recommend.cosine/get-recommendations %1 users %2)))
