(ns aqua.cli.compare-recommenders
  (:require [aqua.compare.misc :refer [timed-score]]
            aqua.compare.recall-planned
            aqua.compare.recall
            aqua.compare.diversification
            aqua.mal-local
            aqua.misc))

(defn -main []
  (let [directory "maldump"
        compare-count 40
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        sampled-ids (aqua.mal-local/load-sampled-user-ids directory 20000)
        cf-parameters-std (aqua.recommend.CFParameters.)
        users (aqua.mal-local/load-cf-users-by-id data-source cf-parameters-std sampled-ids)
        test-users-sample (aqua.compare.misc/load-stable-user-sample directory
                                                                     data-source
                                                                     (* 10 compare-count)
                                                                     "test-users.txt")
        anime-map (aqua.mal-local/load-anime data-source)]
    (let [rp-model (aqua.compare.diversification/load-rp-model)
          score-pearson (aqua.compare.diversification/make-score-pearson rp-model users 20)
          score-cosine (aqua.compare.diversification/make-score-cosine rp-model users)
          test-users (take compare-count test-users-sample)]
      (println (str "\nStart diversification comparison ("
                    (count test-users)
                    " users)"))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.misc/normalize-all-ratings test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.misc/normalize-all-ratings test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))

    (let [score-pearson (aqua.compare.recall-planned/make-score-pearson users 20)
          score-cosine (aqua.compare.recall-planned/make-score-cosine users)
          test-users (aqua.compare.recall-planned/make-test-users-list compare-count
                                                                       test-users-sample)]
      (println (str "\nStart planned items recall comparison ("
                    (count test-users)
                    " users)"))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.misc/normalize-all-ratings test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.misc/normalize-all-ratings test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))

    (let [score-pearson (aqua.compare.recall/make-score-pearson users 20)
          score-cosine (aqua.compare.recall/make-score-cosine users)
          test-users (aqua.compare.recall/make-test-users-list compare-count
                                                             test-users-sample)]
      (println (str "\nStart single-item recall comparison ("
                    (count test-users)
                    " users)"))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.compare.recall/normalize-test-users test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.compare.recall/normalize-test-users test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))))
