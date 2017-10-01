(ns aqua.compare.diversification
  (:require aqua.misc
            aqua.recommend.lfd
            aqua.recommend.lfd-cf
            aqua.recommend.cosine
            aqua.recommend.pearson
            clojure.java.io))

; Compare diversification using intra-list similarity
;
; Uses the score from the random projection model as a measure of similarity
; a score of 1 would mean all users scored both anime in the exact same way
;
; The ILS score is averaged over the recommendation list length

(defn- similarity-score [rp-model first-item rest-items]
  (let [similar-list (.similarAnime rp-model (.animedbId first-item))
        similar-map (into {} (for [item similar-list]
                               [(.animedbId item) item]))]
    (apply + (for [item rest-items]
               (if-let [similar (similar-map (.animedbId item))]
                 (- (.score item))
                 0)))))

(defn- compute-diversification [rp-model recommended-anime]
  (let [following-items (rest recommended-anime)]
    (+ (similarity-score rp-model (first recommended-anime) following-items)
       (if (seq following-items)
         (compute-diversification rp-model following-items)
         0))))

(defn- score-recommender [rp-model recommender test-users anime-map]
  (let [scores (for [test-user test-users]
                 (let [known-anime-tagger (aqua.misc/make-tagger test-user anime-map)
                       known-anime-filter (aqua.misc/make-filter test-user anime-map)
                       [_ untagged] (recommender test-user known-anime-filter)
                       recommended-anime (known-anime-tagger untagged)
                       first-anime (take 30 recommended-anime)
                       pairwise-sum (* (compute-diversification rp-model first-anime) 2)]
                   [pairwise-sum (count first-anime)]))]
    (/ (apply + (map first scores)) (apply + (map second scores)))))

(defn make-score-pearson [rp-model users min-common-items]
  (partial score-recommender rp-model #(aqua.recommend.pearson/get-recommendations min-common-items %1 users %2)))

(defn make-score-cosine [rp-model users]
  (partial score-recommender rp-model #(aqua.recommend.cosine/get-recommendations %1 users %2)))

(defn make-score-lfd [rp-model lfd]
  (partial score-recommender rp-model #(aqua.recommend.lfd/get-recommendations %1 lfd %2)))

(defn make-score-lfd-cf [rp-model lfd-users]
  (partial score-recommender rp-model #(aqua.recommend.lfd-cf/get-recommendations %1 lfd-users %2)))
