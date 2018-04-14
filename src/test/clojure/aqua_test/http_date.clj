(ns aqua-test.http-date
  (:use clojure.test))

(deftest parse-rfc1134
  (is (= 784111777 (aqua.mal.Http/parseDate "Sun, 06 Nov 1994 08:49:37 GMT"))))

(deftest parse-rfc850
  (is (= 784111777 (aqua.mal.Http/parseDate "Sunday, 06-Nov-94 08:49:37 GMT"))))

(deftest parse-asctime
  (is (= 784111777 (aqua.mal.Http/parseDate "Sun Nov  6 08:49:37 1994"))))

(deftest parse-invalid
  (is (= 0 (aqua.mal.Http/parseDate "Bla Nov  6 08:49:37 199"))))