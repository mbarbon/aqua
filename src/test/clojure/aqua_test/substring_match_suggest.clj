(ns aqua-test.substring-match-suggest
  (:use clojure.test))

(defn- int-map [m]
  (into {} (for [[k v] m]
             [(int k) (int v)])))

(defn- make-suggest-rank [titles rank]
  (aqua.search.SubstringMatchSuggest. (map #(aqua.search.AnimeTitle. %) titles) (int-map rank)))

(defn- make-suggest [titles]
  (make-suggest-rank titles {}))

(deftest sanity
  (let [suggester (make-suggest [[1 "Koutetsujou no Kabaneri"] [2 "Cowboy Bebop"] [3 "K-on!"]])
        suggest (fn [term] (com.google.common.primitives.Ints/asList (.suggest suggester term 5)))]
    (is (= [3 1] (suggest "k-on")))
    (is (= [3 1] (suggest "K on")))
    (is (= [2] (suggest "Cowboy")))
    (is (= [3 2 1] (suggest "o")))))

(deftest sanity-rank
  (let [suggester (make-suggest-rank [[1 "K-on!"] [2 "K-on!!"] [3 "K-on! Movie"]] {1 7 2 5 3 1})
        suggest (fn [term] (com.google.common.primitives.Ints/asList (.suggest suggester term 5)))]
    (is (= [3 2 1] (suggest "k-on!"))))

  (let [suggester (make-suggest-rank [[1 "K-on!"] [2 "K-on!!"] [3 "K-on! Movie"]] {1 1 2 2 3 3})
        suggest (fn [term] (com.google.common.primitives.Ints/asList (.suggest suggester term 5)))]
    (is (= [1 2 3] (suggest "k-on!")))))
