(ns aqua.recommend.incremental-embedding
  (:require aqua.misc
            aqua.mal-local
            aqua.recommend.model-files)
  (:use aqua.db-utils))

(def ^:private cf-parameters (aqua.misc/make-cf-parameters 0 0))
(def ^:private ewma-lambda 0.995)

(def ^:private select-anime-user-sample
  (str "SELECT u.user_id, u.last_anime_change AS last_change"
       "    FROM users AS u"
       "        INNER JOIN user_anime_stats AS uas"
       "            ON u.user_id = uas.user_id"
       "    WHERE uas.completed > 10 AND"
       "          uas.completed < 1000"
       "    ORDER BY last_anime_change DESC"
       "    LIMIT ?"))

(defn- make-parameters [args]
  (let [p (aqua.recommend.ComputeIncrementalEmbedding$Parameters.)]
    (set! (.rank p) (args "rank"))
    (set! (.learningRate p) (args "learningRate"))
    (set! (.frequencySmoothing p) (args "frequencySmoothing"))
    p))

(defn create-incremental-embedding [args]
  (let [params (make-parameters args)
        model (aqua.recommend.ComputeIncrementalEmbedding. params)]
    {:model model
     :item-filter (java.util.HashMap.)
     :model-type aqua.recommend.ModelType/ANIME
     :user-count (atom 0)
     :learning-stats (atom 0)}))

(defn- user-items [incremental-embedding user]
  (into ()
    (if (.isManga (:model-type incremental-embedding))
      (.inProgressAndDropped user)
      (.completedAndDropped user))))

(defn- filter-items [item-filter items]
  (let [filtered-items (doall (filter #(.get item-filter (.itemId %)) items))]
    (doseq [item items]
      (.put item-filter (.itemId item) false))
    (doseq [item-id (take (count items) (shuffle (keys item-filter)))]
      (.put item-filter item-id true))
    filtered-items))

(defn- ewma [v s]
  (+ (* v ewma-lambda) (* s (- 1 ewma-lambda))))

(defn- update-learning-stats [incremental-embedding error]
  (swap! (:learning-stats incremental-embedding) #(ewma % error)))

(defn shuffle-items-by-rating [items]
  (let [comp (aqua.mal.data.RatedBase$StableDescendingRating. (rand-int Integer/MAX_VALUE))]
    (sort comp items)))

(defn- update-histogram [incremental-embedding items]
  (let [model (:model incremental-embedding)]
    (doseq [rated items]
      (.updateItemHistogram model (.itemId rated)))
    (swap! (:user-count incremental-embedding) #(+ 1 %))))

(defn- update-embedding [incremental-embedding items]
  (let [model (:model incremental-embedding)
        filtered-items (filter-items (:item-filter incremental-embedding) items)]
    (doseq [[prev curr next] (partition 3 1 (shuffle-items-by-rating filtered-items))]
      (.beginLearning model (.itemId curr))
      (update-learning-stats incremental-embedding (.addPositiveSample model (.itemId prev)))
      (update-learning-stats incremental-embedding (.addPositiveSample model (.itemId next)))
      (dotimes [_ 2]
        (update-learning-stats incremental-embedding (.addNegativeSample model (.negativeSample model))))
      (.endLearning model))))

(defn learn-embedding-step [incremental-embedding user]
  (let [items (user-items incremental-embedding user)]
    (update-histogram incremental-embedding items)
    (when (zero? (mod @(:user-count incremental-embedding) 10))
      (.updateNegativeSample (:model incremental-embedding)))
    (when (> @(:user-count incremental-embedding) 10)
      (update-embedding incremental-embedding items))
    nil))

(defn- load-user-sample-list [kind data-source]
  (with-query data-source rs select-anime-user-sample [10000]
    (doall
      (for [{:keys [user_id last_change]} (resultset-seq rs)]
        [user_id last_change]))))

(defn load-user-sample [kind data-source item-map]
  {:data-source data-source
    :kind kind
    :item-map item-map
    :users (vec (load-user-sample-list kind data-source))})

(defn load-random-user [{:keys [data-source kind item-map users]}]
  (let [[user-id last-change] (users (rand-int (count users)))
        [user] (aqua.mal-local/load-cf-anime-users-by-id data-source item-map cf-parameters [user-id])]
    user))

(defn create-embedding-items [incremental-embedding item-map item-count]
  (let [state (.saveState (:model incremental-embedding))
        item-indices (aqua.recommend.HPPCUtils/toArray (.itemToIndex state))
        trimmed-matrix (java.util.Arrays/copyOf (.vectors state) (* (alength item-indices) (.rank state)))
        embedding (aqua.recommend.Embedding. (.rank state) item-indices trimmed-matrix)]
  (aqua.recommend.EmbeddingItemItem. (.simpleItemItem embedding item-map item-count))))
