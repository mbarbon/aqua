(ns aqua.recommend.pearson
  (:require aqua.misc aqua.recommend.collaborative-filter)
  (:import (aqua.recommend Pearson)))

(defn- rank-users [min-common-items user users]
  (let [user-pearson (Pearson. user min-common-items)
        scored (java.util.ArrayList.)]
    (doseq [similar users]
      (if (aqua.recommend.collaborative-filter/similar-watched-count user similar)
        (.add scored (aqua.recommend.ScoredUser.
                       similar (.userSimilarity user-pearson similar)))))
    (.sort scored aqua.recommend.ScoredUser/SORT_SCORE)
    scored))

(defn get-raw-anime-recommendations [min-common-items user users remove-known-anime]
  (let [ranked-users (take 100 (rank-users min-common-items user users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))
