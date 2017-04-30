(ns aqua.web.internal
  (:require clojure.java.io
            [aqua.web.globals :refer [*state-directory *users *anime]]))

(defn enabled-and-healthy []
  (if-not [(and @*users @*anime @*state-directory)]
    false
    (not (.exists (clojure.java.io/file @*state-directory "is-disabled")))))
