(ns aqua.recommend.pearson
  (:require aqua.misc aqua.recommend.collaborative-filter)
  (:import (aqua.recommend Pearson)))

(defn- rank-users [min-common-items user users]
  (let [user-pearson (Pearson. user min-common-items)]
    (->> users
      (filter #(aqua.recommend.collaborative-filter/similar-watched-count user %))
      (map #(vector (.userSimilarity user-pearson %) %))
      (sort-by #(% 0)))))

(defn get-recommendations [min-common-items user users remove-known-anime]
  (let [ranked-users (take 100 (rank-users min-common-items user users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))
