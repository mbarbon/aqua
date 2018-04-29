(ns aqua-test.fuzzy-prefix-match-suggest
  (:use clojure.test))

(defn- int-map [m]
  (into {} (for [[k v] m]
             [(int k) (int v)])))

(defn- make-suggest-rank [titles rank]
  (aqua.search.FuzzyPrefixMatchSuggest. (map #(aqua.search.AnimeTitle. %) titles) (int-map rank)))

(defn- make-suggest [titles]
  (make-suggest-rank titles {}))

; (deftest sanity
;   (let [suggester (make-suggest [[1 "Koutetsujou no Kabaneri"] [2 "Cowboy Bebop"] [3 "K-on!"]])
;         suggest (fn [term] (map #(.animedbId %) (.suggest suggester term 5)))]
;     (is (= [3 1] (suggest "k-on")))
;     (is (= [3 1] (suggest "K on")))
;     (is (= [2] (suggest "Cowboy")))
;     (is (= [3 2 1] (suggest "o")))))

(deftest single-word-sanity
  (let [suggester (make-suggest [[1 "Koutetsujou no Kabaneri"]])
        suggest (fn [term] (map #(.animedbId %) (.suggest suggester term 5)))]
    (is (= [1] (suggest "k"))) ; exact
    (is (= [] (suggest "o")))  ; no errors allowed

    (is (= [1] (suggest "ko"))) ; exact
    (is (= [] (suggest "kt")))  ; no errors allowed

    (is (= [1] (suggest "kou"))) ; exact
    (is (= [1] (suggest "kot"))) ; deletion
    (is (= [1] (suggest "kuo"))) ; transposition
    (is (= [1] (suggest "xko"))) ; insertion
    (is (= [1] (suggest "kxo"))) ; insertion
    (is (= [1] (suggest "kxu"))) ; replacement
    (is (= [] (suggest "krx")))  ; max 1 error allowed

    (is (= [1] (suggest "kou"))) ; exact
    (is (= [1] (suggest "kot"))) ; deletion
    (is (= [1] (suggest "kuo"))) ; transposition
    (is (= [1] (suggest "xko"))) ; insertion
    (is (= [1] (suggest "kxo"))) ; insertion
    (is (= [1] (suggest "kxu"))) ; replacement
    (is (= [] (suggest "krx")))  ; max 1 error allowed

    (is (= [1] (suggest "koutet"))) ; exact
    (is (= [1] (suggest "kouetu"))) ; deletions
    (is (= [1] (suggest "kouxtxet"))) ; insertions
    (is (= [1] (suggest "koxtxt"))) ; replacements
    (is (= [1] (suggest "kuotte"))) ; trasnpositions
    (is (= [] (suggest "koxuxtext"))))) ; max 2 errors allowed
