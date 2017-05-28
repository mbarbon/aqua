(ns aqua.compare.misc
  (require aqua.mal-local))

(defn stable-shuffle [^java.util.Collection items]
  (let [rnd (java.util.Random. 1643789325)
        lst (java.util.ArrayList. items)]
    (java.util.Collections/shuffle lst rnd)
    lst))

(defn load-stable-user-sample [directory data-source max-count file]
  (if (.exists (clojure.java.io/as-file file))
    (let [user-ids (take max-count
                         (line-seq (clojure.java.io/reader file)))]
      (aqua.mal-local/load-cf-users-by-id data-source
                                          (aqua.recommend.CFParameters.)
                                          user-ids))
    (let [sampled-ids (aqua.mal-local/load-sampled-user-ids directory 20000)
          users (aqua.mal-local/load-cf-users-by-id data-source
                                                    (aqua.recommend.CFParameters.)
                                                    sampled-ids)
          shuffled-users (stable-shuffle users)]
      (spit file (clojure.string/join "\n" (map #(.userId %) shuffled-users)))
      (take max-count shuffled-users))))

(defmacro timed-score [name scorer]
  `(let [start-time# (System/currentTimeMillis)
         score# (float ~scorer)
         end-time# (System/currentTimeMillis)
         took# (- end-time# start-time#)]
     (printf "%s (took %d): %.03f\n" ~name took# score#)
     (flush)))
