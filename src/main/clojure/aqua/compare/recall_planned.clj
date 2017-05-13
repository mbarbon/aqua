(ns aqua.compare.recall-planned
  (:require aqua.misc
            aqua.recommend.cosine
            aqua.recommend.pearson))

; Compare fraction of planned item in the first 40 recommendations
;
; The assumption is that planned items are likely to be good
; recommendations so them being absent is a red flag, but having too
; many means the recommender wont surprise the user

(defn- plan-to-watch-count [user]
  (count (filter #(= (.status %) aqua.recommend.CFRated/PLANTOWATCH)
                 (.animeList user))))

(defn make-test-users-list [list-size users]
  (take list-size
    (remove #(or (< (plan-to-watch-count %) 30)
                 (> (plan-to-watch-count %) 100)) users)))

(defn- count-planned [anime-list]
  (apply + (for [anime anime-list]
             (case (.tags anime)
               :planned 1
               :planned-franchise 1
               0))))

(defn- score-recommender [recommender test-users anime-map]
  (let [scores (for [test-user test-users]
                 (let [known-anime-tagger (aqua.misc/make-tagger test-user anime-map)
                       known-anime-filter (aqua.misc/make-filter test-user anime-map)
                       [_ recommended-anime] (known-anime-tagger
                                               (recommender test-user known-anime-filter))
                       first-anime (take 40 recommended-anime)]
                   [(count-planned first-anime) (count first-anime)]))]
    (/ (apply + (map first scores)) (apply + (map second scores)))))

(defn make-score-pearson [users min-common-items]
  (partial score-recommender #(aqua.recommend.pearson/get-recommendations min-common-items %1 users %2)))

(defn make-score-cosine [users]
  (partial score-recommender #(aqua.recommend.cosine/get-recommendations %1 users %2)))
