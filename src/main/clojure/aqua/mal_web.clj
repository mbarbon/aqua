(ns aqua.mal-web
  (:require org.httpkit.client))

(defn- handle-malappinfo-response [callback {:keys [status headers body error]}]
  (try
    (if error
      (callback nil error)
      (callback (aqua.mal.data.Json/readMalAppInfo body) nil))
    (catch Exception e (callback nil e))))

(defn fetch-anime-list [username callback]
  (let [http-options {:timeout 10000
                      :as :stream
                      :query-params {"u" username
                                     "type" "anime"
                                     "status" "all"}}
        http-handler (fn [& rest] (apply handle-malappinfo-response callback rest))]
    (org.httpkit.client/get "http://myanimelist.net/malappinfo.php"
                            http-options
                            http-handler)))
