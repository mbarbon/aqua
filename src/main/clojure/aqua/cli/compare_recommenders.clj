(ns aqua.cli.compare-recommenders
  (:require [aqua.compare.misc :refer [timed-score]]
            aqua.compare.recall-planned
            aqua.compare.recall
            aqua.compare.diversification
            aqua.compare.popularity
            aqua.recommend.rp-similar-anime
            aqua.recommend.co-occurrency
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.recommend.user-sample
            aqua.mal-local
            aqua.paths
            aqua.misc))

(def user-count 15000)

(defn -main []
  (let [compare-count 40
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        sampled-ids (aqua.recommend.user-sample/load-user-sample (aqua.paths/anime-user-sample) user-count)
        anime-map (aqua.mal-local/load-anime data-source)
        anime-rank (aqua.mal-local/load-anime-rank data-source)
        cf-parameters-std (aqua.recommend.CFParameters.)
        users (aqua.mal-local/load-cf-users-by-id data-source anime-map cf-parameters-std sampled-ids)
        lfd (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model) anime-map)
        lfd-users (aqua.recommend.lfd/load-user-lfd (aqua.paths/anime-lfd-user-model) lfd users)
        lfd-items (aqua.recommend.lfd-items/load-lfd-items (aqua.paths/anime-lfd-items-model) (aqua.paths/anime-lfd-items-model-airing))
        rp-model (aqua.recommend.rp-similar-anime/load-rp-similarity (aqua.paths/anime-rp-model-unfiltered))
        co-occurrency-model (aqua.recommend.co-occurrency/load-co-occurrency (aqua.paths/anime-co-occurrency-model) (aqua.paths/anime-co-occurrency-model-airing))
        test-users-sample (aqua.compare.misc/load-stable-user-sample @aqua.paths/*maldump-directory
                                                                     data-source
                                                                     anime-map
                                                                     (* 10 compare-count)
                                                                     "anime-test-users.txt")]

    (let [score-pearson (aqua.compare.popularity/make-score-pearson anime-rank users 20)
          score-cosine (aqua.compare.popularity/make-score-cosine anime-rank users)
          score-lfd (aqua.compare.popularity/make-score-lfd anime-rank lfd)
          score-lfd-cf (aqua.compare.popularity/make-score-lfd-cf anime-rank lfd-users)
          score-lfd-items (aqua.compare.popularity/make-score-lfd-items anime-rank lfd-items)
          score-rp (aqua.compare.popularity/make-score-rp anime-rank rp-model)
          score-co-occurrency (aqua.compare.popularity/make-score-co-occurrency anime-rank co-occurrency-model)
          test-users (take compare-count test-users-sample)]
      (println (str "\nStart popularity comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
      (timed-score "Co-occurrency" (score-co-occurrency test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.misc/normalize-all-ratings test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.misc/normalize-all-ratings test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))

    (let [score-pearson (aqua.compare.diversification/make-score-pearson rp-model users 20)
          score-cosine (aqua.compare.diversification/make-score-cosine rp-model users)
          score-lfd (aqua.compare.diversification/make-score-lfd rp-model lfd)
          score-lfd-cf (aqua.compare.diversification/make-score-lfd-cf rp-model lfd-users)
          score-lfd-items (aqua.compare.diversification/make-score-lfd-items rp-model lfd-items)
          score-rp (aqua.compare.diversification/make-score-rp rp-model rp-model)
          score-co-occurrency (aqua.compare.diversification/make-score-co-occurrency rp-model co-occurrency-model)
          test-users (take compare-count test-users-sample)]
      (println (str "\nStart diversification comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
      (timed-score "Co-occurrency" (score-co-occurrency test-users anime-map))
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
          score-co-occurrency (aqua.compare.recall-planned/make-score-co-occurrency co-occurrency-model)
          test-users (aqua.compare.recall-planned/make-test-users-list compare-count
                                                                       test-users-sample)]
      (println (str "\nStart planned items recall comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
      (timed-score "Co-occurrency" (score-co-occurrency test-users anime-map))
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
          score-co-occurrency (aqua.compare.recall/make-score-co-occurrency co-occurrency-model)
          test-users (aqua.compare.recall/make-test-users-list compare-count
                                                             test-users-sample)]
      (println (str "\nStart single-item recall comparison ("
                    (count test-users)
                    " users)"))
      (timed-score "LFD" (score-lfd test-users anime-map))
      (timed-score "LFD CF" (score-lfd-cf test-users anime-map))
      (timed-score "LFD items" (score-lfd-items test-users anime-map))
      (timed-score "RP" (score-rp test-users anime-map))
      (timed-score "Co-occurrency" (score-co-occurrency test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0 0)
      (aqua.compare.recall/normalize-test-users test-users 0 0)
      (timed-score "Cosine (default)" (score-cosine test-users anime-map))
      (timed-score "Pearson (default)" (score-pearson test-users anime-map))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (aqua.compare.recall/normalize-test-users test-users 0.5 -1)
      (timed-score "Cosine (positive unrated)" (score-cosine test-users anime-map))
      (timed-score "Pearson (positive unrated)" (score-pearson test-users anime-map)))))
