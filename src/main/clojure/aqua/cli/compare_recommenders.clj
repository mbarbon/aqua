(ns aqua.cli.compare-recommenders
  (:require [aqua.compare.misc :refer [timed-score]]
            aqua.compare.recall-planned
            aqua.compare.recall
            aqua.compare.diversification
            aqua.recommend.rp-similar-anime
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.recommend.user-sample
            aqua.mal-local
            aqua.misc))

(def user-count 15000)

(defn -main []
  (let [directory "maldump"
        compare-count 40
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        sampled-ids (aqua.recommend.user-sample/load-user-sample "maldump/user-sample" user-count)
        cf-parameters-std (aqua.recommend.CFParameters.)
        users (aqua.mal-local/load-cf-users-by-id data-source cf-parameters-std sampled-ids)
        lfd (aqua.recommend.lfd/load-lfd "maldump/lfd-model")
        lfd-users (aqua.recommend.lfd/load-user-lfd "maldump/lfd-user-model" lfd users)
        lfd-items (aqua.recommend.lfd-items/load-lfd-items "maldump/lfd-items-model" lfd lfd)
        rp-model (aqua.recommend.rp-similar-anime/load-rp-similarity "maldump/rp-model-unfiltered")
        test-users-sample (aqua.compare.misc/load-stable-user-sample directory
                                                                     data-source
                                                                     (* 10 compare-count)
                                                                     "test-users.txt")
        anime-map (aqua.mal-local/load-anime data-source)]
    (let [score-pearson (aqua.compare.diversification/make-score-pearson rp-model users 20)
          score-cosine (aqua.compare.diversification/make-score-cosine rp-model users)
          score-lfd (aqua.compare.diversification/make-score-lfd rp-model lfd)
          score-lfd-cf (aqua.compare.diversification/make-score-lfd-cf rp-model lfd-users)
          score-lfd-items (aqua.compare.diversification/make-score-lfd-items rp-model lfd-items)
          score-rp (aqua.compare.diversification/make-score-rp rp-model rp-model)
          test-users (take compare-count test-users-sample)]
      (println (str "\nStart diversification comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
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
          score-lfd (aqua.compare.recall-planned/make-score-lfd lfd)
          score-lfd-cf (aqua.compare.recall-planned/make-score-lfd-cf lfd-users)
          score-lfd-items (aqua.compare.recall-planned/make-score-lfd-items lfd-items)
          score-rp (aqua.compare.recall-planned/make-score-rp rp-model)
          test-users (aqua.compare.recall-planned/make-test-users-list compare-count
                                                                       test-users-sample)]
      (println (str "\nStart planned items recall comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
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
          score-lfd (aqua.compare.recall/make-score-lfd lfd)
          score-lfd-cf (aqua.compare.recall/make-score-lfd-cf lfd-users)
          score-lfd-items (aqua.compare.recall/make-score-lfd-items lfd-items)
          score-rp (aqua.compare.recall/make-score-rp rp-model)
          test-users (aqua.compare.recall/make-test-users-list compare-count
                                                             test-users-sample)]
      (println (str "\nStart single-item recall comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.compare.recall/normalize-test-users test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.compare.recall/normalize-test-users test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))))
