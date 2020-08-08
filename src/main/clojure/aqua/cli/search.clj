(ns aqua.cli.search
  (:require aqua.mal-local
            aqua.recommend.rp-similarity
            aqua.misc))

(defn- readable-size [{:keys [used-memory]}]
  (cond
    (< used-memory 1048576) (format "%.1f KB" (double (/ used-memory 1024)))
    :else                   (format "%.1f MB" (double (/ used-memory 1048576)))))

(defn- prompt [suggest]
  (format "%s (%s) > " (:name suggest) (readable-size suggest)))

(defn- clear-screen []
  (print "\u001b[2J")
  (flush))

(defn- to-home []
  (print "\u001b[0;0H")
  (flush))

(defn- draw-suggestions [console-reader suggest suggestions took]
  (clear-screen)
  (to-home)
  (println)
  (doseq [suggestion suggestions]
    (printf "  %5d   %s\n" (.animedbId suggestion) (.title suggestion)))
  (println)
  (printf "Took %dms" took)
  (to-home)
  (.redrawLine console-reader)
  (.flush console-reader))

(defn- make-suggester [suggester-name]
  (let [runtime (Runtime/getRuntime)
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)
        _ (println "Loading anime titles")
        anime-titles (aqua.mal-local/load-anime-titles data-source (set (.keySet anime)))
        _ (println "Loading anime ranks")
        anime-rank (aqua.mal-local/load-anime-rank data-source)]
    (let [_ (System/gc)
          initial-memory (- (.totalMemory runtime) (.freeMemory runtime))
          _ (println "Creating suggester")
          suggest (case suggester-name
                    "substring" (aqua.search.SubstringMatchSuggest. anime-titles anime-rank)
                    "fuzzy"     (aqua.search.FuzzyPrefixMatchSuggest. anime-titles anime-rank)
                    (throw (Exception. (str "Invlaid suggester name '" suggester-name "'"))))
          _ (System/gc)
          final-memory (- (.totalMemory runtime) (.freeMemory runtime))]
      {:suggest     suggest
       :name        suggester-name
       :used-memory (- final-memory initial-memory)})))

(defn- run-suggester [console-reader suggest]
  (loop [input ""]
    (Thread/sleep 150)
    (let [current-input (-> console-reader
                          (.getCursorBuffer)
                          (.toString)
                          (clojure.string/trim))]
      (if (not= input current-input)
        (let [start-time (System/currentTimeMillis)
              suggestions (if (empty? current-input)
                            []
                            (.suggest (:suggest suggest) current-input 25))
              end-time (System/currentTimeMillis)]
          (draw-suggestions console-reader suggest suggestions (- end-time start-time))))
      (recur current-input))))

(defn- read-forever [console-reader suggest]
  (while true
    (.readLine console-reader (prompt suggest))))

(defn- run-interactive [suggester-name]
  (let [console-reader (jline.console.ConsoleReader.)
        suggest (make-suggester suggester-name)
        thr1 (future (read-forever console-reader suggest))
        thr2 (future (run-suggester console-reader suggest))]
    (while true
      (deref thr1 1000 nil)
      (deref thr2 1000 nil))))

(defn- run-once [suggester-name query]
  (let [suggest (make-suggester suggester-name)
        suggestions (.suggest (:suggest suggest) query 25)]
    (println)
    (doseq [suggestion suggestions]
      (printf "%6d   %s\n" (.animedbId suggestion) (.title suggestion)))))

(defn- do-main
  ([] (do-main "substring"))
  ([suggester-name] (run-interactive suggester-name))
  ([suggester-name & words] (run-once suggester-name (clojure.string/join " " words))))

(defn -main [& args]
  (apply do-main args))
