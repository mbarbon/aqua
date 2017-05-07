(ns aqua.recommend.lfd-cf
  (:require aqua.recommend.lfd
            aqua.recommend.collaborative-filter)
  (:import (aqua.recommend LatentFactorDecompositionUsers)))

(defn- rank-users-x [user lfd-users]
  (let [scored (.computeUserUserScores lfd-users user)]
    (.sort scored aqua.recommend.ScoredUser/SORT_SCORE)
    scored))

(defn- rank-users [user ^aqua.recommend.LatentFactorDecompositionUsers lfd-users]
  (let [user-lfd-cf (.prepareUserData lfd-users user)
        scored (java.util.ArrayList.)]
    (dotimes [userIdx (count (.userMap lfd-users))]
      (let [similar (aget (.userMap lfd-users) userIdx)]
        (if (aqua.recommend.collaborative-filter/similar-watched-count user similar)
          (.add scored (aqua.recommend.ScoredUser.
                         similar (.userSimilarity user-lfd-cf userIdx))))))
    (.sort scored aqua.recommend.ScoredUser/SORT_SCORE)
    scored))

(defn get-recommendations [user lfd-users remove-known-anime]
  (let [ranked-users (take 100 (rank-users user lfd-users))
        recommended-complete (aqua.recommend.collaborative-filter/recommended-completed ranked-users remove-known-anime)]
    [ranked-users (take 100 recommended-complete)]))
