(ns aqua.recommend.cosine
  (:require aqua.misc aqua.recommend.collaborative-filter)
  (:import (aqua.recommend Cosine)))

(defn- rank-users [user users]
  (let [user-cosine (Cosine. user)]
    (->> users
      (filter #(aqua.recommend.collaborative-filter/similar-watched-count user %))
      (map #(vector (.userSimilarity user-cosine %) %))
      (sort-by #(% 0)))))

(defn get-recommendations [user users remove-known-anime]
  (let [ranked-users (take 100 (rank-users user users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))
