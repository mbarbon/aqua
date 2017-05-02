(ns aqua.search.autocomplete
  (:require clojure.string))

(defn- split-words [title]
  (map clojure.string/lower-case
    (clojure.string/split (clojure.string/replace title #"\W+" " ") #"\s+")))

(defn prepare-suggest [anime-title-list anime-rank]
  (let [word-anime (java.util.HashMap.)]
    (doseq [[anime-id title] anime-title-list]
      (doseq [word (split-words title)]
        (if-let [^java.util.Set entries (.get word-anime word)]
          (.add entries anime-id)
          (let [entries (java.util.HashSet.)]
            (.add entries anime-id)
            (.put word-anime word entries)))))
    (aqua.search.Suggest. word-anime anime-rank)))

(defn get-suggestions [suggest term anime-map]
  (let [anime-ids (.suggest suggest (split-words term) 15)]
    (map anime-map anime-ids)))
