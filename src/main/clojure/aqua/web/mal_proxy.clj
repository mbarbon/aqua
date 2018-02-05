(ns aqua.web.mal-proxy
  (:require aqua.mal-local
            aqua.slowpoke
            aqua.web.render
            [aqua.web.globals :refer [*data-source-ro *data-source-rw *background *anime *anime-list-by-letter]]))

(def ^:private title-letters-numbers "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
(def ^:private title-letters "ABCDEFGHIJKLMNOPQRSTUVWXYZ")

(defn- init-slowpoke []
  (letfn [(schedule [make-function interval]
            (let [start (int (+ 3 (* (Math/random) 5)))]
              (.scheduleWithFixedDelay @*background
                                       (make-function @*data-source-rw
                                                      @*data-source-ro)
                                       start
                                       interval
                                       java.util.concurrent.TimeUnit/SECONDS)))]
    (schedule aqua.slowpoke/make-refresh-anime 300)
    (schedule aqua.slowpoke/make-refresh-manga 300)
    (schedule aqua.slowpoke/make-refresh-users 300)
    (schedule aqua.slowpoke/make-fetch-new-users 30)))

(defn- recompute-anime-list-by-letter []
  (letfn [(is-letter-number [c]
            (not= (.indexOf title-letters-numbers (int c)) -1))
          (to-symbol [c]
            (if-not c
              \0
              (if-not (= (.indexOf title-letters (int c)) -1)
                c
                \0)))
          (first-letter [^aqua.mal.data.Anime anime]
            (->> (.title anime)
                 (clojure.string/upper-case)
                 (filter is-letter-number)
                 (first)
                 (to-symbol)
                 (str)))]
    (reset! *anime-list-by-letter
      (into {} (for [[head-letter anime-list] (group-by #(first-letter %) (remove #(.isHentai %) (vals @*anime)))]
                 [head-letter {:head-letter head-letter
                               :example-anime (take 4 (shuffle anime-list))
                               :anime anime-list}])))))

(defn init [{:keys [slowpoke]}]
  (aqua.mal-local/setup-tables @*data-source-rw)
  (aqua.mal.Http/init)
  (if slowpoke
    (init-slowpoke))
  (.scheduleWithFixedDelay
    @*background
    (aqua.slowpoke/make-process-refresh-queue @*data-source-rw @*data-source-ro)
    1 5 java.util.concurrent.TimeUnit/SECONDS)
  (recompute-anime-list-by-letter))

(defn reload []
  (recompute-anime-list-by-letter))

(def ^:private ct-pb "application/x-protobuf")
(def ^:private ct-json "application/json")
(def ^:private ce-gzip "gzip")
(def ^:private ce-id "identity")

(defn- pb-to-json [stream]
  (let [decompressed (java.util.zip.GZIPInputStream. stream)
        rated-list (aqua.mal.Serialize/readRatedProtobuf decompressed)
        byte-out (java.io.ByteArrayOutputStream.)]
    (aqua.mal.Serialize/writeRatedList byte-out rated-list)
    (java.io.ByteArrayInputStream. (.toByteArray byte-out))))

(defn fetch-user [username-nocase accepts-gzip accepts-protobuf]
  (let [username (aqua.mal-local/case-correct-username @*data-source-ro username-nocase)
        last-user-update (aqua.mal-local/last-user-update @*data-source-ro username)]
    (if (> last-user-update (- (/ (System/currentTimeMillis) 1000) (* 3600 6)))
      (let [[bytes blob-type] (aqua.mal-local/load-user-anime-list @*data-source-ro username)
            stream (java.io.ByteArrayInputStream. bytes)]
        (cond
          (and (= 0 blob-type) accepts-gzip)
            [stream ct-json ce-gzip -1]
          (= 0 blob-type)
            [(java.util.zip.GZIPInputStream. stream) ct-json ce-gzip -1]
          (and accepts-protobuf accepts-gzip)
            [stream ct-pb ce-gzip -1]
          accepts-protobuf
            [(java.util.zip.GZIPInputStream. stream) ct-pb ce-id -1]
          :else
            [(pb-to-json stream) ct-json ce-id -1]))
      [nil nil nil (aqua.slowpoke/enqueue-user-refresh @*data-source-rw username)])))

(defn- render-anime-list [anime-list]
  (for [anime anime-list]
    (aqua.web.render/render-anime anime nil)))

(defn anime-list-detail [head-letter]
  (if-let [item (@*anime-list-by-letter (clojure.string/upper-case head-letter))]
    {:anime (render-anime-list (:anime item))}
    {:anime []}))

(defn anime-list-excerpt []
  {:parts (for [item (sort-by :head-letter (vals @*anime-list-by-letter))]
            {:headLetter (:head-letter item)
             :exampleAnime (render-anime-list (:example-anime item))})})
