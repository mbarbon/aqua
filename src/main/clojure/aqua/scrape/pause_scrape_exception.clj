(ns aqua.scrape.pause-scrape-exception
  (:gen-class :extends java.lang.Exception))

(defmacro pause-scrape [reason]
  `(throw (aqua.scrape.pause_scrape_exception. ~reason)))
