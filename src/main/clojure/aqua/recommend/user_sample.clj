(ns aqua.recommend.user-sample
  (:require clojure.set
            aqua.misc
            aqua.recommend.model-files
            aqua.mal-local))

; nothing against hentai, just there is not enough data for meaningful
; recommendations
(def ^:private select-non-hentai-anime
  (str "SELECT a.animedb_id AS animedb_id"
       "    FROM anime AS a"
       "      LEFT JOIN anime_genres AS ag"
       "        ON a.animedb_id = ag.animedb_id AND"
       "           genre_id = 12"
       "    WHERE sort_order IS NULL"))

(def ^:private select-active-users
  (str "SELECT uas.user_id, uas.completed as anime_count"
       "    FROM user_anime_stats AS uas"
       "      INNER JOIN users AS u"
       "        ON uas.user_id = u.user_id AND"
       "           u.username <> ''"
       "    WHERE uas.completed > 5 AND"
       "          uas.completed < 500"))

(defn count-to-bucket [c]
  (if (< c 180)
    (int (/ c 15))
    (+ 8 (int (/ c 45)))))

(defn bucket-to-count [b]
  (if (< b 12)
    (* b 15)
    (* 45 (- b 8))))

(defn- non-hentai-anime [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-non-hentai-anime)]
    (->> (resultset-seq rs)
         (map :animedb_id)
         (set))))

(defn- active-users [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-active-users)]
    (let [all-ids (shuffle (map #(vector (:user_id %) (:anime_count %))
                                (resultset-seq rs)))]
      (group-by #(->> % second count-to-bucket) all-ids))))

(defn- make-bitset [^ints items]
  (let [bitset (java.util.BitSet.)]
    (doseq [item items]
      (.set bitset item))
    bitset))

(defn- batched-user-loader [data-source bucketed-users]
  (let [cf-parameters (aqua.misc/make-cf-parameters 0 0)]
    (apply concat
      (for [batch (partition-all 1000 (map first bucketed-users))]
        (remove #(< (% 2) 5) ; work around the (rare) case where anime stats and anime list are completely out of sync
          (for [^aqua.recommend.CFUser cf-user (aqua.mal-local/load-cf-users-by-id data-source cf-parameters batch)]
            (let [ids (.completedAndDroppedIds cf-user)]
              [(.userId cf-user) (make-bitset ids) (count ids)])))))))

(defn- fresh-user-cluster []
  {:user-ids [] :anime-ids (java.util.BitSet.) :anime-count 0})

(defn- pretty-print-user-cluster [{anime-count :anime-count user-ids :user-ids}]
  {:anime-count anime-count
   :user-ids (count user-ids)})

(defn- add-user-to-cluster [insertion-condition
                            ; cluster
                            {user-ids :user-ids
                             ^java.util.BitSet anime-ids :anime-ids
                             anime-count :anime-count}
                            ; user
                            [user-id
                             ^java.util.BitSet completed-and-dropped
                             completed-and-dropped-count]]
  (let [union (doto (java.util.BitSet.)
                (.or anime-ids)
                (.or completed-and-dropped))
        union-cardinality (.cardinality union)
        added (- union-cardinality anime-count)]
    (if (insertion-condition added completed-and-dropped-count)
      {:user-ids (conj user-ids user-id)
       :anime-ids union
       :anime-count union-cardinality})))

(defn- add-user-to-cluster-sequence [is-complete new-in-progress in-progress user]
  (letfn [(insertion-condition [added anime-count]
            (let [cluster-index (count new-in-progress)
                  absolute-threshold (min (* 0.5 anime-count)
                                          (- (+ 20 1) cluster-index))
                  relative-threshold (- 0.33 (* 0.3 (/ cluster-index 20)))]
              (> added (max absolute-threshold
                            (* relative-threshold anime-count)))))]
    (if-not (seq in-progress)
      (if (< (count new-in-progress) 20)
        (if-let [inserted (add-user-to-cluster insertion-condition
                                               (fresh-user-cluster)
                                               user)]
          [(conj new-in-progress inserted) nil]
          (throw (Exception. (str "Can't fill empty cluster at index "
                                   (count new-in-progress) " for user " user))))
        [new-in-progress nil])
      (let [progress-head (first in-progress)
            progress-tail (rest in-progress)]
        (if-let [inserted (add-user-to-cluster insertion-condition
                                               progress-head
                                               user)]
          (cond
            (is-complete inserted)
              [(concat new-in-progress progress-tail) inserted]
            :else
              [(concat new-in-progress [inserted] progress-tail) nil])
          (recur is-complete (conj new-in-progress progress-head) progress-tail user))))))

(defn- cluster-users-helper [; aggregation status
                             {in-progress :in-progress
                              is-complete :is-complete
                              :as state}
                             users]
  (if-not (seq users)
    in-progress
    (let [users-head (first users)
          users-tail (rest users)
          [new-in-progress complete-cluster] (add-user-to-cluster-sequence is-complete [] in-progress users-head)
          new-state (conj state [:in-progress new-in-progress])]
      ; (doseq [ip new-in-progress]
      ;   (print (pretty-print-user-cluster ip)))
      ; (println)
      (if complete-cluster
        (cons complete-cluster (lazy-seq (cluster-users-helper new-state users-tail)))
        (recur new-state users-tail)))))

(defn- cluster-users [bucket anime-ids users]
  (let [bucket-count (bucket-to-count bucket)
        complete-anime (* 0.95 (count anime-ids))
        is-complete (fn [cluster]
                      (> (:anime-count cluster) complete-anime))
        start-state {:in-progress [] :is-complete is-complete :bucket bucket}]
    (cluster-users-helper start-state users)))

(defn- interleave-all [& seqs]
  (let [not-empty (filter seq seqs)]
    (if (seq not-empty)
      (concat (map first not-empty)
              (lazy-seq (apply interleave-all (map rest not-empty))))
      [])))

(defn- cluster-lazy-sequence [data-source all-users anime-ids]
  (let [clusters (for [[bucket bucketed-users] all-users]
                   (let [users (batched-user-loader data-source bucketed-users)
                         clusters (cluster-users bucket anime-ids users)]
                     clusters))]
    (apply interleave-all clusters)))

(defn- consume-clusters [cluster-seq target max-users]
  (if-not (and (seq cluster-seq)
               (< (count target) max-users))
    target
    (do
      (.addAll target (:user-ids (first cluster-seq)))
      (recur (rest cluster-seq) target max-users))))

(defn count-to-bucket-count [n]
  (bucket-to-count (count-to-bucket n)))

(defn recompute-user-sample [sample-count model-path]
  (let [data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        anime-ids (non-hentai-anime data-source)
        all-users (active-users data-source)
        clusters (cluster-lazy-sequence data-source all-users anime-ids)
        users (java.util.ArrayList.)]
    (consume-clusters clusters users sample-count)
    (with-open [out (clojure.java.io/writer model-path)]
      (binding [*out* out]
        (doseq [user-id users]
          (println user-id))))))

(defn load-user-sample [path size]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (doall (take size (for [line (line-seq in)]
                        (Integer/valueOf line))))))

; the only purpose of this function is to avoid doubling memory usage
; while users are reloaded: old users become garbage while new users are loaded
(defn load-filtered-cf-users-into [path data-source cf-parameters cache target anime-map-to-filter-hentai]
  (aqua.mal-local/load-filtered-cf-users-into data-source
                                              (load-user-sample path (count target))
                                              cf-parameters
                                              cache
                                              target
                                              anime-map-to-filter-hentai))

(defn- load-filtered-cf-users-helper [path data-source cf-parameters max-count anime-map-to-filter-hentai]
  (let [cache (java.util.HashMap.)
        target (java.util.ArrayList. (repeat max-count nil))]
    (load-filtered-cf-users-into path data-source cf-parameters cache target anime-map-to-filter-hentai)))

; those produce a lot of garbage, should not be used in web code
(defn load-filtered-cf-users
  ([path data-source cf-parameters max-count]
    (load-filtered-cf-users-helper path data-source cf-parameters max-count nil))
  ([path data-source cf-parameters max-count anime-map-to-filter-hentai]
    (load-filtered-cf-users-helper path data-source cf-parameters max-count anime-map-to-filter-hentai)))

(defn load-filtered-cf-user-ids [data-source cf-parameters user-ids anime-map-to-filter-hentai]
  (let [cache (java.util.HashMap.)
        target (java.util.ArrayList. (repeat (count user-ids) nil))]
    (aqua.mal-local/load-filtered-cf-users-into data-source
                                                user-ids
                                                cf-parameters
                                                cache
                                                target
                                                anime-map-to-filter-hentai)))
