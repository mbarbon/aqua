(ns aqua-test.mal-scrape
  (:require aqua.mal-scrape)
  (:use clojure.test))

(defn test-resource [name]
  (let [resource-name (str "/parsing/" name ".gz")
        resource (.getResourceAsStream aqua.mal.Json resource-name)]
    (java.util.zip.GZIPInputStream. resource)))

(deftest parse-users
  (let [users (aqua.mal-scrape/parse-users-page (test-resource "users.html"))]
    (is (= 20 (count users)))
    (is (= "FlamyXD" (nth users 0)))
    (is (= "WonderFuture" (nth users 19)))))

(deftest parse-anime
  (let [anime-data (aqua.mal-scrape/parse-anime-page (test-resource "gintama.main.html"))
        {:keys [relations genres titles scores]} anime-data]
    (is (= {2951  1
            6945  1
            7472  2
            9969  3
            10643 1
            21899 2
            25313 1
            32122 1}
           relations))
    (is (= [[1 "Action"]
            [24 "Sci-Fi"]
            [4 "Comedy"]
            [13 "Historical"]
            [20 "Parody"]
            [21 "Samurai"]
            [27 "Shounen"]]
           genres))
    (is (= #{"Gintama"
             "Gin Tama"
             "Silver Soul"
             "Yorinuki Gintama-san"}
           titles))
    (is (= {:score 902, :rank 14, :popularity 88}
           scores))))

(deftest parse-manga
  (let [manga-data (aqua.mal-scrape/parse-manga-page (test-resource "k-on.main.html"))
        {:keys [relations genres titles scores]} manga-data]
    (is (= {51855 3
            51857 3}
           relations))
    (is (= [[4 "Comedy"]
            [19 "Music"]
            [23 "School"]
            [36 "Slice of Life"]]
           genres))
    (is (= #{"Keion"
             "K-On!"}
           titles))
    (is (= {:score 789, :rank 1224, :popularity 269}
           scores))))

(deftest parse-malappinfo-anime
  (let [data (aqua.mal.Json/readMalAppInfo (test-resource "malappinfo.anime.xml"))
        user (.user data)
        anime-list (.anime data)
        anime (first anime-list)]
    (is (= 5621220 (.userId user)))
    (is (= "mattia_y" (.username user)))
    (is (= 8 (.watching user)))
    (is (= 230 (.completed user)))
    (is (= 0 (.onhold user)))
    (is (= 25 (.dropped user)))
    (is (= 232 (.plantowatch user)))

    (is (= 495 (count anime-list)))

    (is (= 1 (.animedbId anime)))
    (is (= "Cowboy Bebop" (.title anime)))
    (is (= 1 (.seriesType anime)))
    (is (= 26 (.episodes anime)))
    (is (= 2 (.seriesStatus anime)))
    (is (= "1998-04-03" (.start anime)))
    (is (= "1999-04-24" (.end anime)))
    (is (= "https://myanimelist.cdn-dena.com/images/anime/4/19644.jpg" (.image anime)))
    (is (= 8 (.score anime)))
    (is (= 2 (.userStatus anime)))
    (is (= 1471101172 (.lastUpdated anime)))))

(deftest parse-malappinfo-manga
  (let [data (aqua.mal.Json/readMalAppInfo (test-resource "malappinfo.manga.xml"))
        user (.user data)
        manga-list (.manga data)
        manga (first manga-list)]
    (is (= 5621220 (.userId user)))
    (is (= "mattia_y" (.username user)))
    (is (= 8 (.reading user)))
    (is (= 12 (.completed user)))
    (is (= 0 (.onhold user)))
    (is (= 5 (.dropped user)))
    (is (= 9 (.plantoread user)))

    (is (= 34 (count manga-list)))

    (is (= 3468 (.mangadbId manga)))
    (is (= "Usagi Drop" (.title manga)))
    (is (= 1 (.seriesType manga)))
    (is (= 62 (.chapters manga)))
    (is (= 10 (.volumes manga)))
    (is (= 2 (.seriesStatus manga)))
    (is (= "2005-10-08" (.start manga)))
    (is (= "2011-12-08" (.end manga)))
    (is (= "https://myanimelist.cdn-dena.com/images/manga/1/122689.jpg" (.image manga)))
    (is (= 8 (.score manga)))
    (is (= 2 (.userStatus manga)))
    (is (= 1480719007 (.lastUpdated manga)))))
