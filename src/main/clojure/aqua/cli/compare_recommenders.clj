(ns aqua.cli.compare-recommenders
  (:require aqua.mal-local
            aqua.misc
            aqua.recommend.cosine
            aqua.recommend.pearson))

(defn- stable-shuffle [^java.util.Collection items]
  (let [rnd (java.util.Random. 1643789325)
        lst (java.util.ArrayList. items)]
    (java.util.Collections/shuffle lst rnd)
    lst))

(defn- make-test-entry [user]
  (let [completed-good (filter #(>= (.normalizedRating user %) 0.6) (.completed user))
        total (count completed-good)
        by-rating (sort-by #(.normalizedRating user %) (stable-shuffle completed-good))
        mid-value (take 2 (drop (/ total 2) by-rating))
        high-value (take 5 (drop (- total 5) by-rating))]
    (if (<= total 10)
      [user #{}]
      [user (set (concat high-value mid-value))])))

(defn- score-user-anime [recommender test-user test-anime anime]
  (let [filtered-user (.removeAnime test-user (.animedbId test-anime))
        known-anime-filter (aqua.misc/make-filter filtered-user anime)
        [_ ranked-anime] (recommender filtered-user known-anime-filter)
        test-anime-rank ((fn [index [scored-anime & rest]]
                          (cond
                            (nil? scored-anime) nil
                            (= (.animedbId scored-anime) (.animedbId test-anime)) index
                            (.tags scored-anime) (recur index rest)
                            :else (recur (+ 1 index) rest))) 0 ranked-anime)]
    (if (and test-anime-rank (<= test-anime-rank 30))
      1
      0)))

(defn- score-recommender [recommender test-users anime]
  (let [scores (for [[test-user test-anime] test-users]
                 (let [known-anime-tagger (aqua.misc/make-tagger test-user anime)
                       user-recommender (fn [filtered-user known-anime-filter]
                                          (known-anime-tagger
                                            (recommender filtered-user known-anime-filter)))
                       score (apply + (map #(score-user-anime user-recommender test-user % anime) test-anime))]
                   [score (count test-anime)]))]
    (/ (apply + (map first scores)) (apply + (map second scores)))))

(defn load-stable-user-sample [data-source max-count file]
  (if (.exists (clojure.java.io/as-file file))
    (let [user-ids (take max-count
                         (line-seq (clojure.java.io/reader file)))]
      (aqua.mal-local/load-cf-users-by-id data-source
                                          (aqua.recommend.CFParameters.)
                                          user-ids))
    (let [users (aqua.mal-local/load-cf-users data-source
                                              (aqua.recommend.CFParameters.)
                                              max-count)]
      (spit file (clojure.string/join "\n" (map #(.userId %) users)))
      users)))

(defn -main []
  (let [directory "maldump"
        compare-count 25
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        cf-parameters-std (aqua.recommend.CFParameters.)
        users (aqua.mal-local/load-cf-users data-source cf-parameters-std 20000)
        anime (aqua.mal-local/load-anime data-source)
        test-users (into []
                     (map make-test-entry
                       (take compare-count
                         (remove #(< (count (seq (.completed %))) 20)
                           (load-stable-user-sample data-source
                                                    (* 4 compare-count)
                                                    "test-users.txt")))))
        normalize-test-users (fn [test-users watched-zero dropped-zero]
                               (let [cf-parameters (aqua.misc/make-cf-parameters watched-zero dropped-zero)]
                                 (doseq [test-user test-users]
                                   (.processAfterDeserialize (first test-user) cf-parameters))))
        make-score-pearson (fn [min-common-items]
                             (partial score-recommender #(aqua.recommend.pearson/get-recommendations min-common-items %1 users %2)))
        score-pearson (make-score-pearson 20)
        score-cosine (partial score-recommender #(aqua.recommend.cosine/get-recommendations %1 users %2))
        print-score (fn [name score] (printf "%s: %.03f\n" name (float score)) (flush))]
    (let [start-time (System/currentTimeMillis)]
      (println "Start")
      (aqua.misc/normalize-all-ratings users 0 0)
      (normalize-test-users test-users 0 0)
      (print-score "Cosine (default)" (score-cosine test-users anime))
      (print-score "Pearson (default)" (score-pearson test-users anime))
      (let [score-pearson-15 (make-score-pearson 15)
            score-pearson-25 (make-score-pearson 25)]
        (print-score "Pearson (default, min 15)" (score-pearson-15 test-users anime))
        (print-score "Pearson (default, min 25)" (score-pearson-25 test-users anime)))
      (aqua.misc/normalize-all-ratings users 0.5 -1)
      (normalize-test-users test-users 0.5 -1)
      (print-score "Cosine (positive unrated)" (score-cosine test-users anime))
      (print-score "Pearson (positive unrated)" (score-pearson test-users anime))
      (aqua.misc/normalize-all-ratings users 1 -1)
      (normalize-test-users test-users 1 -1)
      (print-score "Cosine (more positive unrated)" (score-cosine test-users anime))
      (print-score "Pearson (more positive unrated)" (score-pearson test-users anime))
      (printf "Took %.02f seconds\n" (float (/ (- (System/currentTimeMillis) start-time) 1000))))))