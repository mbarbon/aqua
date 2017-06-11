(ns aqua.recommend.cosine
  (:require aqua.misc aqua.recommend.collaborative-filter)
  (:import (aqua.recommend Cosine)))

(defn- rank-users [user users]
  (let [user-cosine (Cosine. user)
        scored (java.util.ArrayList.)]
    (doseq [similar users]
      (if (aqua.recommend.collaborative-filter/similar-watched-count user similar)
        (.add scored (aqua.recommend.ScoredUser.
                       similar (.userSimilarity user-cosine similar)))))
    (.sort scored aqua.recommend.ScoredUser/SORT_SCORE)
    scored))

(defn get-recommendations [user users remove-known-anime]
  (let [ranked-users (take 100 (rank-users user users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))

(defn get-all-recommendations [user users remove-known-anime keep-airing-anime tagger]
  (let [[similar-users recommended-completed] (get-recommendations user users remove-known-anime)
        recommended-airing (aqua.recommend.collaborative-filter/recommended-airing similar-users keep-airing-anime)]
    [(tagger recommended-completed) (tagger recommended-airing)]))
