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
        suggest (fn [term] (map #(.animedbId %) (.suggest suggester term 5)))]
    (is (= [3 1] (suggest "k-on")))
    (is (= [3 1] (suggest "K on")))
    (is (= [2] (suggest "Cowboy")))
    (is (= [3 2 1] (suggest "o")))))

(deftest sanity-rank
  (let [suggester (make-suggest-rank [[1 "K-on!"] [2 "K-on!!"] [3 "K-on! Movie"]] {1 7 2 5 3 1})
        suggest (fn [term] (map #(.animedbId %) (.suggest suggester term 5)))]
    (is (= [3 2 1] (suggest "k-on!"))))

  (let [suggester (make-suggest-rank [[1 "K-on!"] [2 "K-on!!"] [3 "K-on! Movie"]] {1 1 2 2 3 3})
        suggest (fn [term] (map #(.animedbId %) (.suggest suggester term 5)))]
    (is (= [1 2 3] (suggest "k-on!")))))

(deftest return-matched-title
  (let [suggester (make-suggest [[1 "Gakkougurashi!" 7] [1 "School-Live!" 8]
                                 [2 "Higurashi no Naku Koro ni" 9] [2 "When the Cicadas Cry" 10] [2 "The Moment the Cicadas Cry" 11]])
        suggest (fn [term] (map #(vector (.animedbId %) (.title %)) (.suggest suggester term 5)))]
    (is (= [[2 "Higurashi no Naku Koro ni"] [1 "Gakkougurashi!"]] (suggest "gurashi")))

    (is (= [[2 "The Moment the Cicadas Cry"]] (suggest "cicada")))

    (is (= [[2 "The Moment the Cicadas Cry"]] (suggest "moment cicada")))
    (is (= [[2 "The Moment the Cicadas Cry"]] (suggest "cicada moment")))

    (is (= [[2 "When the Cicadas Cry"]] (suggest "when cicada")))
    (is (= [[2 "When the Cicadas Cry"]] (suggest "cicada when")))

    (is (= [[1 "School-Live!"]] (suggest "school")))))
