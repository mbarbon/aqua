(ns aqua.cli.find-lfd-parameters
  (:require [aqua.compare.misc :refer [timed-score]]
            aqua.compare.diversification
            aqua.compare.recall-planned
            aqua.compare.recall
            aqua.compare.estimated-scores
            aqua.recommend.rp-similarity
            aqua.mal-local
            aqua.paths
            aqua.misc
            aqua.recommend.lfd))

(def user-count 20000)
(def compare-count 40)

(defn- compute-scores [lfd rp-model test-users-sample anime-map]
  (let [score-lfd (aqua.compare.diversification/make-score-lfd rp-model lfd)
        test-users (take compare-count test-users-sample)]
    (timed-score "Diversification" (score-lfd test-users anime-map)))
  (let [score-lfd (aqua.compare.recall-planned/make-score-lfd lfd)
        test-users (aqua.compare.recall-planned/make-test-users-list compare-count
                                                                     test-users-sample)]
    (timed-score "Planned items recall" (score-lfd test-users anime-map)))
  (let [score-lfd (aqua.compare.recall/make-score-lfd lfd)
        test-users (aqua.compare.recall/make-test-users-list compare-count
                                                             test-users-sample)]
    (timed-score "Single item recall" (score-lfd test-users anime-map)))
  (let [score-lfd (aqua.compare.estimated-scores/make-score-lfd lfd)
        test-users (aqua.compare.estimated-scores/make-test-users-list compare-count
                                                                       test-users-sample)]
    (timed-score "Estimate score RMSE" (score-lfd test-users anime-map))))

(defn- run-parameters [users rank lambda iterations rp-model test-users-sample anime-map]
  (println (str "Rank " rank " lambda " lambda " iterations " iterations))
  (let [[lfdr _] (aqua.recommend.lfd/prepare-anime-lfd-decompositor users anime-map rank lambda)]
    (dotimes [i iterations]
      (when (= 0 (mod i 4))
        (println)
        (println "Iteration" i)
        (println "Model RMSE" (.rootMeanSquaredError lfdr))
        (compute-scores (.decomposition lfdr) rp-model test-users-sample anime-map))
      (aqua.recommend.lfd/run-anime-steps lfdr)
      (aqua.recommend.lfd/run-user-steps lfdr))
    (println)
    (println "Iteration" iterations)
    (compute-scores (.decomposition lfdr) rp-model test-users-sample anime-map)
    (println)))

(defn- split-ints [arg]
  (map #(Integer/valueOf %) (clojure.string/split arg #"\s*,\s*")))

(defn- split-doubles [arg]
  (map #(Double/valueOf %) (clojure.string/split arg #"\s*,\s*")))

(defn- has-some-anime [user]
  (>= (count (.completedAndDroppedIds user)) 10))

(defn -main [ranks lambdas iteration-counts]
  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        sampled-ids (aqua.recommend.user-sample/load-user-sample (aqua.paths/anime-user-sample) user-count)
        cf-parameters-std (aqua.misc/make-cf-parameters 0 0)
        anime-map (aqua.mal-local/load-anime data-source)
        users (filter has-some-anime (aqua.mal-local/load-cf-anime-users-by-id data-source anime-map cf-parameters-std sampled-ids))
        test-users-sample (aqua.compare.misc/load-stable-anime-user-sample @aqua.paths/*maldump-directory
                                                                           data-source
                                                                           anime-map
                                                                           (* 10 compare-count)
                                                                           "anime-test-users.txt")
        rp-model (aqua.recommend.rp-similarity/load-rp-similarity (aqua.paths/anime-rp-model-unfiltered))]
    (aqua.misc/normalize-all-ratings users 0.1 -0.1)
    (aqua.misc/normalize-all-ratings test-users-sample 0.1 -0.1)
    (doseq [rank (split-ints ranks)]
      (doseq [lambda (split-doubles lambdas)]
        (doseq [iterations (split-ints iteration-counts)]
          (run-parameters users rank lambda iterations rp-model test-users-sample anime-map))))))
