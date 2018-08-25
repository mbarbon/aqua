(ns aqua-test.mal-scrape
  (:require aqua.mal-scrape)
  (:use clojure.test))

(defn test-resource [name]
  (let [resource-name (str "/parsing/" name ".gz")
        resource (.getResourceAsStream aqua.mal.Serialize resource-name)]
    (java.util.zip.GZIPInputStream. resource)))

(deftest parse-users
  (let [users (aqua.mal-scrape/parse-users-page (test-resource "users.html"))]
    (is (= 20 (count users)))
    (is (= "FlamyXD" (nth users 0)))
    (is (= "WonderFuture" (nth users 19)))))

(defn- floor-with [v m]
  (- v (mod v m)))

(deftest parse-fuzzy-date
  (let [now (java.time.ZonedDateTime/now (java.time.ZoneId/of "America/Los_Angeles"))
        yesterday (.minusDays now 1)
        seconds-ago-4 (.toEpochSecond (.minusSeconds now 4))
        minutes-ago-1 (floor-with (.toEpochSecond (.minusMinutes now 1)) 60)
        hours-ago-2 (floor-with (.toEpochSecond (.minusHours now 2)) 3600)
        yesterday-3-10 (.toEpochSecond (java.time.OffsetDateTime/parse (format "%04d-%02d-%02dT10:10:00+00:00" (.getYear yesterday) (.getMonthValue yesterday) (.getDayOfMonth yesterday))))
        aug-18 (.toEpochSecond (java.time.OffsetDateTime/parse (format "%04d-08-18T22:10:00+00:00" (.getYear now))))
        jun-14 (.toEpochSecond (java.time.OffsetDateTime/parse "2017-06-14T08:17:00+00:00"))]
    ; time zone is America/Los_Angeles (PST)
    (is (= seconds-ago-4 (aqua.mal-scrape/parse-fuzzy-date "4 seconds ago")))
    (is (= minutes-ago-1 (aqua.mal-scrape/parse-fuzzy-date "1 minute ago")))
    (is (= hours-ago-2 (aqua.mal-scrape/parse-fuzzy-date "2 hours ago")))
    (is (= yesterday-3-10 (aqua.mal-scrape/parse-fuzzy-date "Yesterday, 3:10 AM")))
    (is (= yesterday-3-10 (aqua.mal-scrape/parse-fuzzy-date "Yesterday, 3:10 AM")))
    (is (= aug-18 (aqua.mal-scrape/parse-fuzzy-date "Aug 18, 3:10 PM")))
    (is (= jun-14 (aqua.mal-scrape/parse-fuzzy-date "Jun 14, 2017 1:17 AM")))))

(deftest parse-profile
  (let [profile-data (aqua.mal-scrape/parse-profile-page (test-resource "profile.html"))
        now (java.time.ZonedDateTime/now (java.time.ZoneId/of "America/Los_Angeles"))
        minutes-ago-14 (floor-with (.toEpochSecond (.minusMinutes now 14)) 60)
        mar-14 (.toEpochSecond (java.time.OffsetDateTime/parse (format "%04d-03-14T15:19:00+00:00" (.getYear now))))]
    (is (= minutes-ago-14 (:anime-update profile-data)))
    (is (= mar-14 (:manga-update profile-data)))
    (is (= 560 (:anime-count profile-data)))
    (is (= 34 (:manga-count profile-data)))
    (is (= 5621220 (:user-id profile-data)))
    (is (= "mattia_y" (:username profile-data)))))

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
    (is (= {"Gintama" 1
            "Gin Tama" 2
            "Silver Soul" 2
            "Yorinuki Gintama-san" 2}
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
    (is (= {"Keion" 2
            "K-On!" 1}
           titles))
    (is (= {:score 789, :rank 1224, :popularity 269}
           scores))))

(deftest parse-malappinfo-anime
  (let [data (aqua.mal.Serialize/readMalAppInfo (test-resource "malappinfo.anime.xml"))
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
  (let [data (aqua.mal.Serialize/readMalAppInfo (test-resource "malappinfo.manga.xml"))
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
