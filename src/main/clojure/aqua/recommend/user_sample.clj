(ns aqua.recommend.user-sample
  (:require clojure.set
            aqua.misc
            aqua.recommend.model-files
            aqua.mal-local))

(def ^:private select-active-anime-users
  (str "SELECT uas.user_id, uas.completed as item_count"
       "    FROM user_anime_stats AS uas"
       "      INNER JOIN users AS u"
       "        ON uas.user_id = u.user_id AND"
       "           u.username <> ''"
       "    WHERE uas.completed > 5 AND"
       "          uas.completed < 500"))

(def ^:private select-active-manga-users
  (str "SELECT ums.user_id, ums.completed + ums.reading as item_count"
       "    FROM user_manga_stats AS ums"
       "      INNER JOIN users AS u"
       "        ON ums.user_id = u.user_id AND"
       "           u.username <> ''"
       "    WHERE ums.completed + ums.reading > 5 AND"
       "          ums.completed + ums.reading < 500"))

(def max-clusters 25)

(defn bitset-union [^java.util.BitSet a ^java.util.BitSet b]
  (doto (java.util.BitSet.)
    (.or a)
    (.or b)))

(defn count-to-bucket [c]
  (if (< c 180)
    (int (/ c 15))
    (+ 8 (int (/ c 45)))))

(defn bucket-to-count [b]
  (if (< b 12)
    (* b 15)
    (* 45 (- b 8))))

(defn- active-users [kind data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement (if (.isAnime kind)
                                            select-active-anime-users
                                            select-active-manga-users))]
    (let [all-ids (shuffle (map #(vector (:user_id %) (:item_count %))
                                (resultset-seq rs)))]
      (group-by #(->> % second count-to-bucket) all-ids))))

(defn- make-filtered-bitset ^java.util.BitSet [^java.util.Set item-ids ^ints items]
  (let [bitset (java.util.BitSet.)]
    (doseq [item items]
      (if (.contains item-ids item)
        (.set bitset item)))
    bitset))

(defn- items-to-ids [items]
  (into-array Integer/TYPE (map #(.itemId %) items)))

(defn- batched-user-loader [kind data-source item-ids bucketed-users]
  (let [cf-parameters (aqua.misc/make-cf-parameters 0 0)
        load-users (if (.isAnime kind)
                     aqua.mal-local/load-cf-anime-users-by-id
                     aqua.mal-local/load-cf-manga-users-by-id)]
    (apply concat
      (for [batch (partition-all 1000 (map first bucketed-users))]
        ; work around the (rare) case where anime/manga stats and anime/manga list are completely out of sync
        ; or the (slightly more frequent) case where the majority of anime/manga are non-interesting (hentai, specials, ...)
        (remove #(< (% 2) 5)
          (for [^aqua.recommend.CFUser cf-user (load-users data-source nil cf-parameters batch)]
            (let [ids (if (.isAnime kind)
                        (.completedAndDroppedIds cf-user)
                        (items-to-ids (.inProgressAndDropped cf-user)))
                  bitset (make-filtered-bitset item-ids ids)]
              [(.userId cf-user) bitset (.cardinality bitset)])))))))

(defn- fresh-user-cluster []
  {:user-ids [] :item-ids (java.util.BitSet.) :item-count 0 :bucket nil})

(defn- pretty-print-user-cluster [{item-count :item-count user-ids :user-ids bucket :bucket}]
  {:bucket bucket
   :item-count item-count
   :user-ids (count user-ids)})

(defn- add-user-to-cluster [all-ids
                            insertion-condition
                            ; cluster
                            {user-ids :user-ids
                             item-ids :item-ids
                             item-count :item-count
                             bucket :bucket}
                            ; user
                            [user-id
                             completed-and-dropped
                             completed-and-dropped-count]]
  (let [union (bitset-union item-ids completed-and-dropped)
        union-cardinality (.cardinality union)
        added (- union-cardinality item-count)]
    (if (insertion-condition added completed-and-dropped-count)
      {:user-ids (conj user-ids user-id)
       :item-ids union
       :item-count union-cardinality
       :bucket (count-to-bucket completed-and-dropped-count)})))

(defn- add-user-to-cluster-sequence [all-ids is-complete new-in-progress in-progress [_ completed-and-dropped completed-and-dropped-count :as user]]
  (let [updated-ids (bitset-union all-ids completed-and-dropped)
        added-a-new-one (> (.cardinality updated-ids) (.cardinality all-ids))]
    (letfn [(insertion-condition [added item-count]
              (or added-a-new-one
                  (let [cluster-index (count new-in-progress)
                        absolute-threshold (min (* 0.4 item-count)
                                                (- (+ max-clusters 1) cluster-index))
                        relative-threshold (- 0.23 (* 0.2 (/ cluster-index max-clusters)))]
                    ; (println "    items/index" item-count cluster-index "added" added "absolute" absolute-threshold "relative" (* relative-threshold item-count))
                    (> added (max absolute-threshold
                                  (* relative-threshold item-count))))))]
      (if-not (seq in-progress)
        (if (< (count new-in-progress) max-clusters)
          (if-let [inserted (add-user-to-cluster all-ids
                                                insertion-condition
                                                (fresh-user-cluster)
                                                user)]
            [updated-ids (conj new-in-progress inserted) nil]
            (throw (Exception. (str "Can't fill empty cluster at index "
                                    (count new-in-progress) " for user " user))))
          [all-ids new-in-progress nil])
        (let [progress-head (first in-progress)
              progress-tail (rest in-progress)]
          (if-let [inserted (add-user-to-cluster all-ids
                                                insertion-condition
                                                progress-head
                                                user)]
            (cond
              (is-complete inserted)
                [updated-ids (concat new-in-progress progress-tail) inserted]
              :else
                [updated-ids (concat new-in-progress [inserted] progress-tail) nil])
            (recur all-ids is-complete (conj new-in-progress progress-head) progress-tail user)))))))

(defn- cluster-users-helper [; aggregation status
                             {in-progress :in-progress
                              is-complete :is-complete
                              all-ids :all-ids
                              :as state}
                             users]
  (if-not (seq users)
    in-progress
    (let [users-head (first users)
          users-tail (rest users)
          [new-all-ids new-in-progress complete-cluster] (add-user-to-cluster-sequence all-ids is-complete [] in-progress users-head)
          new-state (conj state [:in-progress new-in-progress] [:all-ids new-all-ids])]
      ; (doseq [ip new-in-progress]
      ;   (print (pretty-print-user-cluster ip)))
      ; (println)
      (if complete-cluster
        (cons complete-cluster (lazy-seq (cluster-users-helper new-state users-tail)))
        (recur new-state users-tail)))))

(defn- cluster-users [bucket item-ids users]
  (let [bucket-count (bucket-to-count bucket)
        complete-items (* 0.95 (count item-ids))
        is-complete (fn [cluster]
                      (> (:item-count cluster) complete-items))
        start-state {:in-progress [] :is-complete is-complete :bucket bucket :all-ids (java.util.BitSet.)}]
    (cluster-users-helper start-state users)))

(defn- interleave-all [& seqs]
  (let [not-empty (filter seq seqs)]
    (if (seq not-empty)
      (concat (map first not-empty)
              (lazy-seq (apply interleave-all (map rest not-empty))))
      [])))

(defn- cluster-lazy-sequence [kind data-source all-users item-ids]
  (let [clusters (for [[bucket bucketed-users] all-users]
                   (let [users (batched-user-loader kind data-source item-ids bucketed-users)
                         clusters (cluster-users bucket item-ids users)]
                     clusters))]
    (apply interleave-all clusters)))

(defn- consume-clusters [cluster-seq target max-users]
  (if-not (and (seq cluster-seq)
               (< (count target) max-users))
    target
    (do
      ; (println (pretty-print-user-cluster (first cluster-seq)))
      (.addAll target (:user-ids (first cluster-seq)))
      (recur (rest cluster-seq) target max-users))))

(defn count-to-bucket-count [n]
  (bucket-to-count (count-to-bucket n)))

(defn recompute-user-sample [kind data-source sample-count item-ids model-path]
  (let [all-users (active-users kind data-source)
        clusters (cluster-lazy-sequence kind data-source all-users item-ids)
        users (java.util.ArrayList.)]
    (consume-clusters clusters users sample-count)
    (with-open [out (clojure.java.io/writer model-path)]
      (binding [*out* out]
        (doseq [user-id users]
          (println user-id))))))

(defn load-user-sample [path size]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (doall (take size (for [^String line (line-seq in)]
                        (Integer/valueOf line))))))

; the only purpose of this function is to avoid doubling memory usage
; while users are reloaded: old users become garbage while new users are loaded
(defn load-filtered-cf-users-into [kind path data-source cf-parameters target item-map-to-filter-hentai]
  (if (.isAnime kind)
    (aqua.mal-local/load-filtered-cf-anime-users-into data-source
                                                      (load-user-sample path (count target))
                                                      cf-parameters
                                                      target
                                                      item-map-to-filter-hentai)
    (aqua.mal-local/load-filtered-cf-manga-users-into data-source
                                                      (load-user-sample path (count target))
                                                      cf-parameters
                                                      target
                                                      item-map-to-filter-hentai)))

(defn- load-filtered-cf-users-helper [kind path data-source cf-parameters max-count item-map-to-filter-hentai]
  (let [target (java.util.ArrayList. (repeat max-count nil))]
    (load-filtered-cf-users-into kind path data-source cf-parameters target item-map-to-filter-hentai)))

; those produce a lot of garbage, should not be used in web code
(defn load-filtered-cf-users
  ([kind path data-source cf-parameters max-count]
    (load-filtered-cf-users-helper kind path data-source cf-parameters max-count nil))
  ([kind path data-source cf-parameters max-count item-map-to-filter-hentai]
    (load-filtered-cf-users-helper kind path data-source cf-parameters max-count item-map-to-filter-hentai)))

(defn load-filtered-cf-user-ids [kind data-source cf-parameters user-ids item-map-to-filter-hentai]
  (let [target (java.util.ArrayList. (repeat (count user-ids) nil))]
    (if (.isAnime kind)
      (aqua.mal-local/load-filtered-cf-anime-users-into data-source
                                                        user-ids
                                                        cf-parameters
                                                        target
                                                        item-map-to-filter-hentai))
      (aqua.mal-local/load-filtered-cf-anime-users-into data-source
                                                        user-ids
                                                        cf-parameters
                                                        target
                                                        item-map-to-filter-hentai)))
