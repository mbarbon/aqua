(ns aqua.compare.estimated-scores
  (:require aqua.misc
            aqua.compare.misc
            aqua.recommend.lfd))

; return an [user, set-of-rated-anime] pair
(defn- make-test-entry [user]
  (let [completed-sample (take 8 (aqua.compare.misc/stable-shuffle (seq (.completed user))))]
    [user (into {} (for [rated completed-sample]
                     [(.animedbId rated) (.normalizedRating user rated)]))]))

(defn make-test-users-list [list-size users]
  (into []
    (map make-test-entry
      (take list-size
        (remove #(< (count (seq (.completed %))) 30)
          users)))))

(defn- score-user-anime [recommender test-user test-anime-scores]
  (let [filtered-user (.removeAnime test-user (.keySet test-anime-scores))
        score-map (recommender filtered-user)]
    (for [anime-id (.keySet test-anime-scores)]
      (- (score-map anime-id) (test-anime-scores anime-id)))))

(defn- score-recommender [recommender test-users anime-map]
  (let [user-errors (for [[test-user test-anime-scores] test-users]
                      (score-user-anime recommender test-user test-anime-scores))
        errors (apply concat user-errors)]
    (Math/sqrt (/ (apply + (map #(* % %) errors)) (count errors)))))

(defn make-score-lfd [lfd]
  (partial score-recommender #(aqua.recommend.lfd/anime-score-map %1 lfd)))
