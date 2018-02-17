(ns aqua.compare.misc
  (require aqua.recommend.user-sample
           aqua.mal-local))

(defn stable-shuffle [^java.util.Collection items]
  (let [rnd (java.util.Random. 1643789325)
        lst (java.util.ArrayList. items)]
    (java.util.Collections/shuffle lst rnd)
    lst))

(defn- load-stable-user-sample-from-db [directory data-source max-count]
  (let [path (str directory "/" "user-sample")
        user-ids (aqua.recommend.user-sample/load-user-sample path (* 2 max-count))]
    (aqua.mal-local/load-test-cf-user-ids data-source user-ids max-count)))

(defn load-stable-user-sample [directory data-source max-count file]
  (if (.exists (clojure.java.io/as-file file))
    (let [user-ids (take max-count
                         (line-seq (clojure.java.io/reader file)))]
      (aqua.mal-local/load-cf-users-by-id data-source
                                          (aqua.recommend.CFParameters.)
                                          user-ids))
    (let [user-ids (load-stable-user-sample-from-db directory
                                                    data-source
                                                    100000)
          unused-user-ids (.subList user-ids 70000 (.size user-ids))
          shuffled-user-ids (stable-shuffle unused-user-ids)]
      (spit file (clojure.string/join "\n" shuffled-user-ids))
      (aqua.mal-local/load-cf-users-by-id data-source
                                          (aqua.recommend.CFParameters.)
                                          (take max-count shuffled-user-ids)))))

(defmacro timed-score [name scorer]
  `(let [start-time# (System/currentTimeMillis)
         score# (float ~scorer)
         end-time# (System/currentTimeMillis)
         took# (- end-time# start-time#)]
     (printf "%s (took %d): %.03f\n" ~name took# score#)
     (flush)))
