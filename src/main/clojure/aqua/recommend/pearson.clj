(ns aqua.recommend.pearson
  (:require aqua.misc aqua.recommend.collaborative-filter)
  (:import (aqua.recommend Pearson)))

(defn- rank-users [min-common-items user users]
  (let [user-pearson (Pearson. user min-common-items)]
    (sort-by #(% 0) (map #(vector (.userSimilarity user-pearson %) %) users))))

(defn get-recommendations [min-common-items user users remove-known-anime]
  (let [ranked-users (take 100 (rank-users min-common-items user users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))
