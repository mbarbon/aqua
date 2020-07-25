(ns aqua.compare.popularity
  (:require aqua.misc
            aqua.recommend.lfd
            aqua.recommend.lfd-cf
            aqua.recommend.lfd-items
            aqua.recommend.rp-similarity
            aqua.recommend.co-occurrency
            aqua.recommend.cosine
            aqua.recommend.pearson
            clojure.java.io))

; Evaluate novelty using item popularity
;
; Computes average item popularity over the recommendation list; lower values means
; the recommender tends to recommend more popular anime

(defn- sum-popularity [anime-rank recommended-anime]
  (apply +
    (for [recommendation recommended-anime]
      (get anime-rank (.animedbId recommendation) 0))))

(defn- score-recommender [anime-rank recommender test-users anime-map]
  (let [scores (for [test-user test-users]
                 (let [known-anime-tagger (aqua.misc/make-tagger test-user anime-map)
                       known-anime-filter (aqua.misc/make-filter test-user anime-map)
                       [_ untagged] (recommender test-user known-anime-filter)
                       recommended-anime (known-anime-tagger untagged)
                       first-anime (take 40 recommended-anime)]
                   [(sum-popularity anime-rank first-anime) (count first-anime)]))]
    (/ (apply + (map first scores)) (apply + (map second scores)))))

(defn make-score-pearson [anime-rank users min-common-items]
  (partial score-recommender anime-rank #(aqua.recommend.pearson/get-raw-anime-recommendations min-common-items %1 users %2)))

(defn make-score-cosine [anime-rank users]
  (partial score-recommender anime-rank #(aqua.recommend.cosine/get-raw-anime-recommendations %1 users %2)))

(defn make-score-lfd [anime-rank lfd]
  (partial score-recommender anime-rank #(aqua.recommend.lfd/get-raw-anime-recommendations %1 lfd %2)))

(defn make-score-lfd-cf [anime-rank lfd-users]
  (partial score-recommender anime-rank #(aqua.recommend.lfd-cf/get-raw-anime-recommendations %1 lfd-users %2)))

(defn make-score-lfd-items [anime-rank lfd]
  (partial score-recommender anime-rank #(aqua.recommend.lfd-items/get-raw-anime-recommendations %1 lfd %2)))

(defn make-score-rp [anime-rank rp-model]
  (partial score-recommender anime-rank #(aqua.recommend.rp-similarity/get-raw-anime-recommendations %1 rp-model %2)))

(defn make-score-co-occurrency [anime-rank co-occurrency-model]
  (partial score-recommender anime-rank #(aqua.recommend.co-occurrency/get-raw-anime-recommendations %1 co-occurrency-model %2)))
